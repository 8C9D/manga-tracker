import { Component, computed, effect, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Manga, MangaSearchResult, MangaService } from './manga.service';
import { AuthService } from './auth.service';
import { LoginComponent } from './login.component';

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule, LoginComponent],
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
          this.chaptersBehind(b.latestChapter, b.lastReadChapter) -
          this.chaptersBehind(a.latestChapter, a.lastReadChapter)
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
  newTitle = '';
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
        this.newTitle = '';
      }
    });
  }

  loadManga(): void {
    this.mangaService.list().subscribe({
      next: (data) => this.mangaList.set(data),
      error: (err) => {
        if (err?.status !== 401) {
          this.errorMessage.set('Failed to load manga. Is the backend running?');
        }
      }
    });
  }

  logout(): void {
    this.auth.logout();
  }

  addManga(): void {
    const title = this.newTitle.trim();
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
      error: () => {
        this.isSearching.set(false);
        this.errorMessage.set('Search failed.');
      }
    });
  }

  confirmAdd(result: MangaSearchResult): void {
    const title = this.newTitle.trim();
    this.mangaService.add(title, result.mangadexId, result.coverUrl).subscribe({
      next: (manga) => {
        this.mangaList.update(list => [...list, manga]);
        this.searchResults.set([]);
        this.newTitle = '';
        this.errorMessage.set('');
      },
      error: (err) => this.errorMessage.set(
        err.status === 409 ? `"${title}" is already being tracked.` : 'Failed to add manga.'
      )
    });
  }

  addNoSource(): void {
    const title = this.newTitle.trim();
    this.mangaService.add(title, undefined, undefined, true).subscribe({
      next: (manga) => {
        this.mangaList.update(list => [...list, manga]);
        this.showAddAnyway.set(false);
        this.newTitle = '';
        this.errorMessage.set('');
      },
      error: (err) => this.errorMessage.set(
        err.status === 409 ? `"${title}" is already being tracked.` : 'Failed to add manga.'
      )
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
      next: (list) => {
        this.mangaList.set(list);
        this.checkingAll.set(false);
        this.errorMessage.set('');
      },
      error: () => {
        this.errorMessage.set('Check all failed.');
        this.checkingAll.set(false);
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
      error: () => {
        this.errorMessage.set('Check failed.');
        this.checkingId.set(null);
      }
    });
  }

  chaptersBehind(latest: string | null, lastRead: string | null): number {
    if (!latest || !lastRead) return 0;
    const diff = parseFloat(latest) - parseFloat(lastRead);
    return diff > 0 ? Math.floor(diff) : 0;
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

  daysUntil(date: string | null): string {
    if (!date) return '';
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const target = new Date(date + 'T00:00:00');
    const diff = Math.round((target.getTime() - today.getTime()) / 86400000);
    if (diff < 0) return 'overdue';
    if (diff === 0) return 'today';
    if (diff === 1) return 'tomorrow';
    return `in ${diff} days`;
  }

  removeAll(): void {
    if (!confirm('Remove all manga?')) return;
    this.removingAll.set(true);
    this.mangaService.removeAll().subscribe({
      next: () => {
        this.mangaList.set([]);
        this.removingAll.set(false);
      },
      error: () => {
        this.errorMessage.set('Failed to remove all manga.');
        this.removingAll.set(false);
      }
    });
  }

  removeManga(id: number): void {
    this.mangaService.remove(id).subscribe({
      next: () => this.mangaList.update(list => list.filter(m => m.id !== id)),
      error: () => this.errorMessage.set('Failed to remove manga.')
    });
  }
}
