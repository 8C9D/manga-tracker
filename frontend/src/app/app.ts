import { Component, computed, effect, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Manga, MangaSearchResult, MangaService } from './manga.service';
import { AuthService } from './auth.service';
import { LoginComponent } from './login.component';
import { MangaListComponent } from './manga-list.component';
import { MangaSearchAddComponent } from './manga-search-add.component';
import { chaptersBehind } from './manga-utils';
import { describeHttpError } from './http-error';

@Component({
  selector: 'app-root',
  imports: [CommonModule, LoginComponent, MangaListComponent, MangaSearchAddComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  mangaList = signal<Manga[]>([]);
  sortBy = signal<string>('next-check');
  sortedMangaList = computed(() => {
    const list = [...this.mangaList()];
    switch (this.sortBy()) {
      case 'title':
        return list.sort((a, b) => a.title.localeCompare(b.title));
      case 'date-added':
        return list.sort((a, b) => b.id - a.id);
      case 'chapters-behind':
        return list.sort((a, b) =>
          chaptersBehind(b.latestChapter, b.lastReadChapter) -
          chaptersBehind(a.latestChapter, a.lastReadChapter)
        );
      default: // next-check
        return list.sort((a, b) => {
          if (!a.nextCheckDate && !b.nextCheckDate) return 0;
          if (!a.nextCheckDate) return -1;
          if (!b.nextCheckDate) return 1;
          return a.nextCheckDate < b.nextCheckDate ? -1 : a.nextCheckDate > b.nextCheckDate ? 1 : 0;
        });
    }
  });
  newTitle = signal('');
  errorMessage = signal('');
  checkingId = signal<number | null>(null);
  checkingAll = signal(false);
  markingReadId = signal<number | null>(null);
  removingAll = signal(false);
  searchResults = signal<MangaSearchResult[]>([]);
  isSearching = signal(false);
  showAddAnyway = signal(false);

  constructor(private mangaService: MangaService, public auth: AuthService) {
    effect(() => {
      if (this.auth.isAuthenticated()) {
        this.loadManga();
      } else {
        this.mangaList.set([]);
        this.errorMessage.set('');
        this.searchResults.set([]);
        this.newTitle.set('');
      }
    });
  }

  loadManga(): void {
    this.mangaService.list().subscribe({
      next: (data) => this.mangaList.set(data),
      error: (err) => this.showError(err, 'Failed to load manga.')
    });
  }

  logout(): void {
    this.auth.logout();
  }

  addManga(): void {
    const title = this.newTitle().trim();
    if (!title) return;
    this.isSearching.set(true);
    this.errorMessage.set('');
    this.searchResults.set([]);
    this.mangaService.search(title).subscribe({
      next: (results) => {
        this.isSearching.set(false);
        if (results.length === 0) {
          this.errorMessage.set(`No results found for "${title}" on MangaDex.`);
          this.showAddAnyway.set(true);
        } else {
          this.searchResults.set(results);
          this.showAddAnyway.set(false);
        }
      },
      error: (err) => {
        this.isSearching.set(false);
        this.showError(err, 'Search failed.');
      }
    });
  }

  confirmAdd(result: MangaSearchResult): void {
    const title = this.newTitle().trim();
    this.mangaService.add(title, result.mangadexId, result.coverUrl).subscribe({
      next: (manga) => {
        this.mangaList.update(list => [...list, manga]);
        this.searchResults.set([]);
        this.newTitle.set('');
        this.errorMessage.set('');
      },
      error: (err) => this.showError(err, 'Failed to add manga.')
    });
  }

  addNoSource(): void {
    const title = this.newTitle().trim();
    this.mangaService.add(title, undefined, undefined, true).subscribe({
      next: (manga) => {
        this.mangaList.update(list => [...list, manga]);
        this.showAddAnyway.set(false);
        this.newTitle.set('');
        this.errorMessage.set('');
      },
      error: (err) => this.showError(err, 'Failed to add manga.')
    });
  }

  cancelSearch(): void {
    this.searchResults.set([]);
    this.showAddAnyway.set(false);
    this.errorMessage.set('');
  }

  checkAll(): void {
    this.checkingAll.set(true);
    this.mangaService.checkAll().subscribe({
      next: () => {
        this.checkingAll.set(false);
        this.errorMessage.set('');
        this.loadManga();
      },
      error: (err) => {
        this.checkingAll.set(false);
        this.showError(err, 'Check all failed.');
      }
    });
  }

  checkNow(id: number): void {
    this.checkingId.set(id);
    this.mangaService.check(id).subscribe({
      next: (updated) => {
        this.mangaList.update(list => list.map(m => m.id === id ? updated : m));
        this.checkingId.set(null);
        this.errorMessage.set('');
      },
      error: (err) => {
        this.checkingId.set(null);
        this.showError(err, 'Check failed.');
      }
    });
  }

  markRead(manga: Manga): void {
    if (!manga.latestChapter) return;
    this.markingReadId.set(manga.id);
    this.mangaService.markRead(manga.id, manga.latestChapter).subscribe({
      next: (updated) => {
        this.mangaList.update(list => list.map(m => m.id === manga.id ? updated : m));
        this.markingReadId.set(null);
      },
      error: () => this.markingReadId.set(null)
    });
  }

  removeAll(): void {
    if (!confirm('Remove all manga?')) return;
    this.removingAll.set(true);
    this.mangaService.removeAll().subscribe({
      next: () => {
        this.mangaList.set([]);
        this.removingAll.set(false);
      },
      error: (err) => {
        this.removingAll.set(false);
        this.showError(err, 'Failed to remove all manga.');
      }
    });
  }

  removeManga(id: number): void {
    this.mangaService.remove(id).subscribe({
      next: () => this.mangaList.update(list => list.filter(m => m.id !== id)),
      error: (err) => this.showError(err, 'Failed to remove manga.')
    });
  }

  private showError(err: unknown, fallback: string): void {
    // null = 401, already surfaced via auth.interceptor → AuthService.message.
    const msg = describeHttpError(err, fallback);
    if (msg !== null) this.errorMessage.set(msg);
  }
}
