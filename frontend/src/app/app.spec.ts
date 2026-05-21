// @vitest-environment jsdom
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { AuthService } from './auth.service';
import type { Manga, MangaSearchResult } from './manga.service';
import { environment } from '../environments/environment';

const baseUrl = `${environment.apiUrl}/api/manga`;
const TOKEN_KEY = 'auth.basic';

function makeManga(overrides: Partial<Manga> = {}): Manga {
  return {
    id: 1,
    title: 'Untitled',
    coverUrl: null,
    latestChapter: null,
    lastReadChapter: null,
    nextCheckDate: null,
    noSource: false,
    ...overrides,
  };
}

describe('App (authenticated)', () => {
  let fixture: ComponentFixture<App>;
  let app: App;
  let http: HttpTestingController;

  beforeEach(() => {
    // Pre-seed an auth token so the constructor effect goes through the
    // "authenticated" branch and fires loadManga() once we detectChanges().
    sessionStorage.clear();
    sessionStorage.setItem(TOKEN_KEY, 'Basic ' + btoa('user:pw'));

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    fixture = TestBed.createComponent(App);
    app = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  // Resolve the GET fired by the constructor effect so individual specs can
  // focus on their own request/response without re-asserting initial load.
  function flushInitialLoad(list: Manga[] = []): void {
    const req = http.expectOne(baseUrl);
    expect(req.request.method).toBe('GET');
    req.flush(list);
    fixture.detectChanges();
  }

  describe('initial load', () => {
    it('GETs /api/manga and populates mangaList on auth', () => {
      const list = [makeManga({ id: 1, title: 'Naruto' })];
      flushInitialLoad(list);
      expect(app.mangaList()).toEqual(list);
      expect(app.errorMessage()).toBe('');
    });

    it('surfaces a useful error message when the initial load fails', () => {
      const req = http.expectOne(baseUrl);
      req.flush({ message: 'DB down' }, { status: 500, statusText: 'Server Error' });
      expect(app.errorMessage()).toBe('DB down');
      expect(app.mangaList()).toEqual([]);
    });
  });

  describe('logout transitions', () => {
    it('clears mangaList, errorMessage, searchResults, newTitle when auth flips to false', () => {
      flushInitialLoad([makeManga({ id: 1, title: 'X' })]);
      app.errorMessage.set('boom');
      app.searchResults.set([{ mangadexId: 'a', title: 'X', coverUrl: null }]);
      app.newTitle.set('typed');

      TestBed.inject(AuthService).logout();
      fixture.detectChanges();

      expect(app.mangaList()).toEqual([]);
      expect(app.errorMessage()).toBe('');
      expect(app.searchResults()).toEqual([]);
      expect(app.newTitle()).toBe('');
    });
  });

  describe('sortedMangaList', () => {
    it('next-check: nulls first, then ascending date', () => {
      flushInitialLoad([
        makeManga({ id: 1, title: 'A', nextCheckDate: '2026-05-25' }),
        makeManga({ id: 2, title: 'B', nextCheckDate: null }),
        makeManga({ id: 3, title: 'C', nextCheckDate: '2026-05-22' }),
      ]);
      app.sortBy.set('next-check');
      expect(app.sortedMangaList().map((m) => m.id)).toEqual([2, 3, 1]);
    });

    it('title: A–Z by localeCompare', () => {
      flushInitialLoad([
        makeManga({ id: 1, title: 'Bleach' }),
        makeManga({ id: 2, title: 'Akira' }),
        makeManga({ id: 3, title: 'Chainsaw Man' }),
      ]);
      app.sortBy.set('title');
      expect(app.sortedMangaList().map((m) => m.title)).toEqual([
        'Akira',
        'Bleach',
        'Chainsaw Man',
      ]);
    });

    it('date-added: newest id first', () => {
      flushInitialLoad([
        makeManga({ id: 1, title: 'Old' }),
        makeManga({ id: 5, title: 'New' }),
        makeManga({ id: 3, title: 'Mid' }),
      ]);
      app.sortBy.set('date-added');
      expect(app.sortedMangaList().map((m) => m.id)).toEqual([5, 3, 1]);
    });

    it('chapters-behind: most behind first', () => {
      flushInitialLoad([
        makeManga({ id: 1, latestChapter: '10', lastReadChapter: '10' }), // 0 behind
        makeManga({ id: 2, latestChapter: '100', lastReadChapter: '50' }), // 50 behind
        makeManga({ id: 3, latestChapter: '20', lastReadChapter: '15' }), // 5 behind
      ]);
      app.sortBy.set('chapters-behind');
      expect(app.sortedMangaList().map((m) => m.id)).toEqual([2, 3, 1]);
    });

    it('leaves the source mangaList signal untouched (does not mutate in place)', () => {
      const list = [
        makeManga({ id: 1, title: 'Bleach' }),
        makeManga({ id: 2, title: 'Akira' }),
      ];
      flushInitialLoad(list);
      app.sortBy.set('title');
      void app.sortedMangaList();
      expect(app.mangaList().map((m) => m.id)).toEqual([1, 2]);
    });
  });

  describe('search/add orchestration', () => {
    beforeEach(() => flushInitialLoad());

    it('search populates searchResults and clears the searching/add-anyway flags', () => {
      app.newTitle.set('naruto');
      app.addManga();
      expect(app.isSearching()).toBe(true);

      const req = http.expectOne((r) => r.method === 'GET' && r.url === `${baseUrl}/search`);
      expect(req.request.params.get('q')).toBe('naruto');
      const results: MangaSearchResult[] = [
        { mangadexId: 'm1', title: 'Naruto', coverUrl: null },
      ];
      req.flush(results);

      expect(app.searchResults()).toEqual(results);
      expect(app.isSearching()).toBe(false);
      expect(app.showAddAnyway()).toBe(false);
    });

    it('empty search results raise the add-anyway path with an explanatory message', () => {
      app.newTitle.set('nothinghere');
      app.addManga();
      const req = http.expectOne((r) => r.method === 'GET' && r.url === `${baseUrl}/search`);
      req.flush([]);

      expect(app.searchResults()).toEqual([]);
      expect(app.errorMessage()).toContain('nothinghere');
      expect(app.showAddAnyway()).toBe(true);
      expect(app.isSearching()).toBe(false);
    });

    it('search error surfaces through errorMessage and clears the searching flag', () => {
      app.newTitle.set('boom');
      app.addManga();
      const req = http.expectOne((r) => r.method === 'GET' && r.url === `${baseUrl}/search`);
      req.flush({ message: 'MangaDex unavailable' }, { status: 502, statusText: 'Bad Gateway' });

      expect(app.errorMessage()).toBe('MangaDex unavailable');
      expect(app.isSearching()).toBe(false);
    });

    it('addManga is a no-op for whitespace-only input', () => {
      app.newTitle.set('   ');
      app.addManga();
      http.expectNone((r) => r.url === `${baseUrl}/search`);
      expect(app.isSearching()).toBe(false);
    });

    it('confirmAdd POSTs the selected result and appends to mangaList', () => {
      app.newTitle.set('Naruto');
      const result: MangaSearchResult = {
        mangadexId: 'm1',
        title: 'Naruto',
        coverUrl: 'cover.jpg',
      };
      app.confirmAdd(result);

      const req = http.expectOne(baseUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        title: 'Naruto',
        mangadexId: 'm1',
        coverUrl: 'cover.jpg',
        noSource: false,
      });

      const added = makeManga({ id: 99, title: 'Naruto', coverUrl: 'cover.jpg' });
      req.flush(added);

      expect(app.mangaList()).toEqual([added]);
      expect(app.searchResults()).toEqual([]);
      expect(app.newTitle()).toBe('');
      expect(app.errorMessage()).toBe('');
    });

    it('addNoSource POSTs with noSource=true and appends to mangaList', () => {
      app.newTitle.set('My Doujin');
      app.addNoSource();

      const req = http.expectOne(baseUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toMatchObject({ title: 'My Doujin', noSource: true });

      const added = makeManga({ id: 50, title: 'My Doujin', noSource: true });
      req.flush(added);

      expect(app.mangaList()).toEqual([added]);
      expect(app.showAddAnyway()).toBe(false);
      expect(app.newTitle()).toBe('');
    });

    it('cancelSearch clears searchResults, showAddAnyway, errorMessage', () => {
      app.searchResults.set([{ mangadexId: 'm1', title: 'X', coverUrl: null }]);
      app.showAddAnyway.set(true);
      app.errorMessage.set('foo');

      app.cancelSearch();

      expect(app.searchResults()).toEqual([]);
      expect(app.showAddAnyway()).toBe(false);
      expect(app.errorMessage()).toBe('');
    });
  });

  describe('per-manga actions', () => {
    const m1 = makeManga({ id: 1, title: 'A', latestChapter: '5', lastReadChapter: '3' });
    const m2 = makeManga({ id: 2, title: 'B', latestChapter: '10', lastReadChapter: '10' });

    beforeEach(() => flushInitialLoad([m1, m2]));

    it('checkNow replaces only the matching manga in the list', () => {
      app.checkNow(1);
      expect(app.checkingId()).toBe(1);

      const req = http.expectOne(`${baseUrl}/1/check`);
      expect(req.request.method).toBe('POST');
      const updated = { ...m1, latestChapter: '6' };
      req.flush(updated);

      expect(app.checkingId()).toBeNull();
      expect(app.mangaList()).toEqual([updated, m2]);
    });

    it('checkNow on error clears checkingId and surfaces the message', () => {
      app.checkNow(1);
      const req = http.expectOne(`${baseUrl}/1/check`);
      req.flush({ message: 'MangaDex rate limited' }, { status: 429, statusText: 'Too Many Requests' });

      expect(app.checkingId()).toBeNull();
      expect(app.errorMessage()).toContain('MangaDex rate limited');
    });

    it('markRead PATCHes the latest chapter and replaces the item in the list', () => {
      app.markRead(m1);
      expect(app.markingReadId()).toBe(1);

      const req = http.expectOne(`${baseUrl}/1/read`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ chapter: '5' });
      const updated = { ...m1, lastReadChapter: '5' };
      req.flush(updated);

      expect(app.markingReadId()).toBeNull();
      expect(app.mangaList()).toEqual([updated, m2]);
    });

    it('markRead is a no-op when latestChapter is null', () => {
      const noChapter = makeManga({ id: 99, latestChapter: null });
      app.markRead(noChapter);
      http.expectNone((r) => r.url.includes('/read'));
      expect(app.markingReadId()).toBeNull();
    });

    it('removeManga DELETEs and drops the item from mangaList', () => {
      app.removeManga(1);
      const req = http.expectOne(`${baseUrl}/1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });

      expect(app.mangaList()).toEqual([m2]);
    });
  });

  describe('removeAll', () => {
    beforeEach(() => flushInitialLoad([makeManga({ id: 1 })]));

    it('returns early with no HTTP call when the user dismisses confirm()', () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
      app.removeAll();
      expect(confirmSpy).toHaveBeenCalled();
      http.expectNone((r) => r.method === 'DELETE' && r.url === baseUrl);
      expect(app.removingAll()).toBe(false);
    });

    it('DELETEs the collection and clears mangaList when confirm() is accepted', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      app.removeAll();
      expect(app.removingAll()).toBe(true);

      const req = http.expectOne((r) => r.method === 'DELETE' && r.url === baseUrl);
      req.flush(null, { status: 204, statusText: 'No Content' });

      expect(app.mangaList()).toEqual([]);
      expect(app.removingAll()).toBe(false);
    });

    it('surfaces backend errors and clears removingAll on failure', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      app.removeAll();

      const req = http.expectOne((r) => r.method === 'DELETE' && r.url === baseUrl);
      req.flush({ message: 'busy' }, { status: 409, statusText: 'Conflict' });

      expect(app.removingAll()).toBe(false);
      expect(app.errorMessage()).toBe('busy');
    });
  });

  describe('checkAll', () => {
    beforeEach(() => flushInitialLoad([makeManga({ id: 1, title: 'X' })]));

    it('POSTs /check-all and triggers a reload that refreshes mangaList on 202', () => {
      app.checkAll();
      expect(app.checkingAll()).toBe(true);

      const post = http.expectOne(`${baseUrl}/check-all`);
      expect(post.request.method).toBe('POST');
      post.flush(null, { status: 202, statusText: 'Accepted' });

      const reload = http.expectOne(baseUrl);
      expect(reload.request.method).toBe('GET');
      const updated = [makeManga({ id: 1, title: 'X', latestChapter: '12' })];
      reload.flush(updated);

      expect(app.checkingAll()).toBe(false);
      expect(app.mangaList()).toEqual(updated);
      expect(app.errorMessage()).toBe('');
    });

    it('surfaces 409 conflict errors through errorMessage', () => {
      app.checkAll();
      const req = http.expectOne(`${baseUrl}/check-all`);
      req.flush({ message: 'already running' }, { status: 409, statusText: 'Conflict' });

      expect(app.checkingAll()).toBe(false);
      expect(app.errorMessage()).toBe('already running');
    });

    it('surfaces 429 with Retry-After through the helper', () => {
      app.checkAll();
      const req = http.expectOne(`${baseUrl}/check-all`);
      req.flush(
        { message: 'Slow down' },
        {
          status: 429,
          statusText: 'Too Many Requests',
          headers: { 'Retry-After': '60' },
        },
      );

      expect(app.checkingAll()).toBe(false);
      expect(app.errorMessage()).toBe('Slow down Try again in about a minute.');
    });
  });
});
