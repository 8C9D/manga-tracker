// @vitest-environment jsdom
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { App } from './app';
import { LoginComponent } from './login.component';
import { MangaSearchAddComponent } from './manga-search-add.component';
import { MangaListComponent } from './manga-list.component';
import { MangaAppFacade } from './manga-app.facade';
import type { MangaSearchResult } from './manga.service';
import { environment } from '../environments/environment';

// Template/wiring slice for app.html. Stateful orchestration is covered in
// manga-app.facade.spec.ts; the auth-effect wiring is covered in app.spec.ts.
// Here we only assert which children render in each auth branch, that the
// error banner appears, and that child @Output bindings reach the facade.

const baseUrl = `${environment.apiUrl}/api/manga`;
const TOKEN_KEY = 'auth.basic';

describe('App template wiring', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    sessionStorage.clear();
  });

  function renderAuthenticated(): {
    fixture: ComponentFixture<App>;
    facade: MangaAppFacade;
  } {
    // Seed a token so the App effect takes the authenticated branch and
    // triggers facade.loadManga() — which we immediately flush to keep the
    // template render tests independent of the load response.
    sessionStorage.setItem(TOKEN_KEY, 'Basic ' + btoa('user:pw'));
    const fixture = TestBed.createComponent(App);
    const facade = TestBed.inject(MangaAppFacade);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne(baseUrl).flush([]);
    fixture.detectChanges();
    return { fixture, facade };
  }

  describe('unauthenticated shell', () => {
    it('renders <app-login> and none of the authenticated children', () => {
      const fixture = TestBed.createComponent(App);
      fixture.detectChanges();

      expect(fixture.debugElement.query(By.directive(LoginComponent))).not.toBeNull();
      expect(fixture.debugElement.query(By.directive(MangaSearchAddComponent))).toBeNull();
      expect(fixture.debugElement.query(By.directive(MangaListComponent))).toBeNull();
      expect(fixture.nativeElement.querySelector('.container')).toBeNull();
    });
  });

  describe('authenticated shell', () => {
    it('renders the .container with both children and hides <app-login>', () => {
      const { fixture } = renderAuthenticated();

      expect(fixture.nativeElement.querySelector('.container')).not.toBeNull();
      expect(fixture.debugElement.query(By.directive(MangaSearchAddComponent))).not.toBeNull();
      expect(fixture.debugElement.query(By.directive(MangaListComponent))).not.toBeNull();
      expect(fixture.debugElement.query(By.directive(LoginComponent))).toBeNull();
    });
  });

  describe('error banner', () => {
    it('renders the .error paragraph with the message when errorMessage is set', () => {
      const { fixture, facade } = renderAuthenticated();
      facade.errorMessage.set('Something broke');
      fixture.detectChanges();

      const banner = fixture.nativeElement.querySelector('.error') as HTMLElement | null;
      expect(banner).not.toBeNull();
      expect(banner?.textContent?.trim()).toBe('Something broke');
    });

    it('omits the .error paragraph when errorMessage is empty', () => {
      const { fixture, facade } = renderAuthenticated();
      facade.errorMessage.set('');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.error')).toBeNull();
    });
  });

  describe('child output wiring', () => {
    // Each test fires the @Output directly on the child component instance,
    // bypassing the child's own DOM (covered in its own spec) and proving the
    // template's (event)="..." binding lands on the facade method.
    it('wires MangaSearchAddComponent.search to facade.addManga()', () => {
      const { fixture, facade } = renderAuthenticated();
      const spy = vi.spyOn(facade, 'addManga').mockImplementation(() => {});
      const child = fixture.debugElement.query(By.directive(MangaSearchAddComponent))
        .componentInstance as MangaSearchAddComponent;

      child.search.emit();
      expect(spy).toHaveBeenCalledTimes(1);
    });

    it('wires MangaSearchAddComponent.confirmAdd to facade.confirmAdd($event)', () => {
      const { fixture, facade } = renderAuthenticated();
      const spy = vi.spyOn(facade, 'confirmAdd').mockImplementation(() => {});
      const child = fixture.debugElement.query(By.directive(MangaSearchAddComponent))
        .componentInstance as MangaSearchAddComponent;
      const result: MangaSearchResult = { mangadexId: 'mid-1', title: 'Naruto', coverUrl: null };

      child.confirmAdd.emit(result);
      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith(result);
    });

    it('wires MangaListComponent.checkNow to facade.checkNow($event)', () => {
      const { fixture, facade } = renderAuthenticated();
      const spy = vi.spyOn(facade, 'checkNow').mockImplementation(() => {});
      const child = fixture.debugElement.query(By.directive(MangaListComponent))
        .componentInstance as MangaListComponent;

      child.checkNow.emit(42);
      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy).toHaveBeenCalledWith(42);
    });

    it('wires MangaListComponent.sortByChange to facade.sortBy.set($event)', () => {
      const { fixture, facade } = renderAuthenticated();
      expect(facade.sortBy()).toBe('next-check');
      const child = fixture.debugElement.query(By.directive(MangaListComponent))
        .componentInstance as MangaListComponent;

      child.sortByChange.emit('title');
      expect(facade.sortBy()).toBe('title');
    });
  });
});
