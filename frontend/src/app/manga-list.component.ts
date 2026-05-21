import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Manga } from './manga.service';
import { chaptersBehind, daysUntil } from './manga-utils';

@Component({
  selector: 'app-manga-list',
  imports: [CommonModule],
  templateUrl: './manga-list.component.html',
  styleUrl: './manga-list.component.css',
})
export class MangaListComponent {
  mangaList = input.required<Manga[]>();
  sortBy = input.required<string>();
  checkingId = input<number | null>(null);
  markingReadId = input<number | null>(null);
  checkingAll = input(false);
  removingAll = input(false);

  sortByChange = output<string>();
  checkAll = output<void>();
  removeAll = output<void>();
  checkNow = output<number>();
  markRead = output<Manga>();
  remove = output<number>();

  protected readonly chaptersBehind = chaptersBehind;
  protected readonly daysUntil = daysUntil;
}
