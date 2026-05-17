import { Component, OnInit, signal } from '@angular/core';
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
  newTitle = '';
  errorMessage = signal('');
  checkingId = signal<number | null>(null);

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

  removeManga(id: number): void {
    this.mangaService.remove(id).subscribe({
      next: () => this.mangaList.update(list => list.filter(m => m.id !== id)),
      error: () => this.errorMessage.set('Failed to remove manga.')
    });
  }
}
