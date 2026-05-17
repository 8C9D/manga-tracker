import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Manga, MangaService } from './manga.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  mangaList = signal<Manga[]>([]);
  sortedMangaList = computed(() =>
    [...this.mangaList()].sort((a, b) => {
      if (!a.nextCheckDate && !b.nextCheckDate) return 0;
      if (!a.nextCheckDate) return -1;
      if (!b.nextCheckDate) return 1;
      return a.nextCheckDate < b.nextCheckDate ? -1 : a.nextCheckDate > b.nextCheckDate ? 1 : 0;
    })
  );
  newTitle = '';
  errorMessage = signal('');
  checkingId = signal<number | null>(null);
  markingReadId = signal<number | null>(null);

  constructor(private mangaService: MangaService) {}

  ngOnInit(): void {
    this.loadManga();
  }

  loadManga(): void {
    this.mangaService.list().subscribe({
      next: (data) => this.mangaList.set(data),
      error: () => this.errorMessage.set('Failed to load manga. Is the backend running?')
    });
  }

  addManga(): void {
    const title = this.newTitle.trim();
    if (!title) return;
    this.mangaService.add(title).subscribe({
      next: (manga) => {
        this.mangaList.update(list => [...list, manga]);
        this.newTitle = '';
        this.errorMessage.set('');
      },
      error: (err) => this.errorMessage.set(
        err.status === 409 ? `"${title}" is already being tracked.` : 'Failed to add manga.'
      )
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

  removeManga(id: number): void {
    this.mangaService.remove(id).subscribe({
      next: () => this.mangaList.update(list => list.filter(m => m.id !== id)),
      error: () => this.errorMessage.set('Failed to remove manga.')
    });
  }
}
