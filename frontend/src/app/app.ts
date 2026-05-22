import { Component, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from './auth.service';
import { LoginComponent } from './login.component';
import { MangaListComponent } from './manga-list.component';
import { MangaSearchAddComponent } from './manga-search-add.component';
import { MangaAppFacade } from './manga-app.facade';

@Component({
  selector: 'app-root',
  imports: [CommonModule, LoginComponent, MangaListComponent, MangaSearchAddComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  constructor(public store: MangaAppFacade, public auth: AuthService) {
    effect(() => {
      if (this.auth.isAuthenticated()) {
        this.store.loadManga();
      } else {
        this.store.clearForLogout();
      }
    });
  }

  logout(): void {
    this.auth.logout();
  }
}
