import { Component, input, model, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MangaSearchResult } from './manga.service';

@Component({
  selector: 'app-manga-search-add',
  imports: [CommonModule, FormsModule],
  templateUrl: './manga-search-add.component.html',
  styleUrl: './manga-search-add.component.css',
})
export class MangaSearchAddComponent {
  newTitle = model.required<string>();
  isSearching = input(false);
  searchResults = input.required<MangaSearchResult[]>();
  showAddAnyway = input(false);

  search = output<void>();
  confirmAdd = output<MangaSearchResult>();
  addNoSource = output<void>();
  cancel = output<void>();
}
