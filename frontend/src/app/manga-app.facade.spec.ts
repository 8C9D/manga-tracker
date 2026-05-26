// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MangaAppFacade } from './manga-app.facade';
import type { Manga, MangaSearchResult } from './manga.service';
import { environment } from '../environments/environment';
import { makeManga } from './testing';

const baseUrl = `${environment.apiUrl}/api/manga`;

describe('MangaAppFacade', () => {
  let facade: MangaAppFacade;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    facade = TestBed.inject(MangaAppFacade);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  // Drive an initial GET /api/manga so individual specs don't re-assert
  // the load flow; mirrors the app.spec helper that existed pre-refactor.
  function loadInitial(list: Manga[] = []): void {
    facade.loadManga();
    const req = http.expectOne(baseUrl);
    expect(req.request.method).toBe('GET');
    req.flush(list);
  }

  describe('loadManga', () => {
    it('GETs /api/manga and populates mangaList', () => {
      const list = [makeManga({ id: 1, title: 'Naruto' })];
      loadInitial(list);
      expect(facade.mangaList()).toEqual(list);
      expect(facade.errorMessage()).toBe('');
    });

    it('surfaces a useful error message when the load fails', () => {
      facade.loadManga();
      const req = http.expectOne(baseUrl);
      req.flush({ message: 'DB down' }, { status: 500, statusText: 'Server Error' });
      expect(facade.errorMessage()).toBe('DB down');
      expect(facade.mangaList()).toEqual([]);
    });
  });

  describe('clearForLogout', () => {
    it('resets mangaList, errorMessage, searchResults, newTitle', () => {
      facade.mangaList.set([makeManga({ id: 1, title: 'X' })]);
      facade.errorMessage.set('boom');
      facade.searchResults.set([{ mangadexId: 'a', title: 'X', coverUrl: null }]);
      facade.newTitle.set('typed');

      facade.clearForLogout();

      expect(facade.mangaList()).toEqual([]);
      expect(facade.errorMessage()).toBe('');
      expect(facade.searchResults()).toEqual([]);
      expect(facade.newTitle()).toBe('');
    });
  });

  describe('sortedMangaList', () => {
    it('next-check: nulls first, then ascending date', () => {
      loadInitial([
        makeManga({ id: 1, title: 'A', nextCheckDate: '2026-05-25' }),
        makeManga({ id: 2, title: 'B', nextCheckDate: null }),
        makeManga({ id: 3, title: 'C', nextCheckDate: '2026-05-22' }),
      ]);
      facade.sortBy.set('next-check');
      expect(facade.sortedMangaList().map((m) => m.id)).toEqual([2, 3, 1]);
    });

    it('title: A–Z by localeCompare', () => {
      loadInitial([
        makeManga({ id: 1, title: 'Bleach' }),
        makeManga({ id: 2, title: 'Akira' }),
        makeManga({ id: 3, title: 'Chainsaw Man' }),
      ]);
      facade.sortBy.set('title');
      expect(facade.sortedMangaList().map((m) => m.title)).toEqual([
        'Akira',
        'Bleach',
        'Chainsaw Man',
      ]);
    });

    it('date-added: newest id first', () => {
      loadInitial([
        makeManga({ id: 1, title: 'Old' }),
        makeManga({ id: 5, title: 'New' }),
        makeManga({ id: 3, title: 'Mid' }),
      ]);
      facade.sortBy.set('date-added');
      expect(facade.sortedMangaList().map((m) => m.id)).toEqual([5, 3, 1]);
    });

    it('chapters-behind: most behind first', () => {
      loadInitial([
        makeManga({ id: 1, latestChapter: '10', lastReadChapter: '10' }),
        makeManga({ id: 2, latestChapter: '100', lastReadChapter: '50' }),
        makeManga({ id: 3, latestChapter: '20', lastReadChapter: '15' }),
      ]);
      facade.sortBy.set('chapters-behind');
      expect(facade.sortedMangaList().map((m) => m.id)).toEqual([2, 3, 1]);
    });

    it('leaves the source mangaList signal untouched (does not mutate in place)', () => {
      const list = [
        makeManga({ id: 1, title: 'Bleach' }),
        makeManga({ id: 2, title: 'Akira' }),
      ];
      loadInitial(list);
      facade.sortBy.set('title');
      void facade.sortedMangaList();
      expect(facade.mangaList().map((m) => m.id)).toEqual([1, 2]);
    });
  });

  describe('search/add orchestration', () => {
    beforeEach(() => loadInitial());

    it('addManga populates searchResults and clears the searching/add-anyway flags', () => {
      facade.newTitle.set('naruto');
      facade.addManga();
      expect(facade.isSearching()).toBe(true);

      const req = http.expectOne((r) => r.method === 'GET' && r.url === `${baseUrl}/search`);
      expect(req.request.params.get('q')).toBe('naruto');
      const results: MangaSearchResult[] = [
        { mangadexId: 'm1', title: 'Naruto', coverUrl: null },
      ];
      req.flush(results);

      expect(facade.searchResults()).toEqual(results);
      expect(facade.isSearching()).toBe(false);
      expect(facade.showAddAnyway()).toBe(false);
    });

    it('empty search results raise the add-anyway path with an explanatory message', () => {
      facade.newTitle.set('nothinghere');
      facade.addManga();
      const req = http.expectOne((r) => r.method === 'GET' && r.url === `${baseUrl}/search`);
      req.flush([]);

      expect(facade.searchResults()).toEqual([]);
      expect(facade.errorMessage()).toContain('nothinghere');
      expect(facade.showAddAnyway()).toBe(true);
      expect(facade.isSearching()).toBe(false);
    });

    it('search error surfaces through errorMessage and clears the searching flag', () => {
      facade.newTitle.set('boom');
      facade.addManga();
      const req = http.expectOne((r) => r.method === 'GET' && r.url === `${baseUrl}/search`);
      req.flush({ message: 'MangaDex unavailable' }, { status: 502, statusText: 'Bad Gateway' });

      expect(facade.errorMessage()).toBe('MangaDex unavailable');
      expect(facade.isSearching()).toBe(false);
    });

    it('addManga is a no-op for whitespace-only input', () => {
      facade.newTitle.set('   ');
      facade.addManga();
      http.expectNone((r) => r.url === `${baseUrl}/search`);
      expect(facade.isSearching()).toBe(false);
    });

    it('confirmAdd POSTs the selected result and appends to mangaList', () => {
      facade.newTitle.set('Naruto');
      const result: MangaSearchResult = {
        mangadexId: 'm1',
        title: 'Naruto',
        coverUrl: 'cover.jpg',
      };
      facade.confirmAdd(result);

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

      expect(facade.mangaList()).toEqual([added]);
      expect(facade.searchResults()).toEqual([]);
      expect(facade.newTitle()).toBe('');
      expect(facade.errorMessage()).toBe('');
    });

    it('addNoSource POSTs with noSource=true and appends to mangaList', () => {
      facade.newTitle.set('My Doujin');
      facade.addNoSource();

      const req = http.expectOne(baseUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toMatchObject({ title: 'My Doujin', noSource: true });

      const added = makeManga({ id: 50, title: 'My Doujin', noSource: true });
      req.flush(added);

      expect(facade.mangaList()).toEqual([added]);
      expect(facade.showAddAnyway()).toBe(false);
      expect(facade.newTitle()).toBe('');
    });

    it('cancelSearch clears searchResults, showAddAnyway, errorMessage', () => {
      facade.searchResults.set([{ mangadexId: 'm1', title: 'X', coverUrl: null }]);
      facade.showAddAnyway.set(true);
      facade.errorMessage.set('foo');

      facade.cancelSearch();

      expect(facade.searchResults()).toEqual([]);
      expect(facade.showAddAnyway()).toBe(false);
      expect(facade.errorMessage()).toBe('');
    });
  });

  describe('per-manga actions', () => {
    const m1 = makeManga({ id: 1, title: 'A', latestChapter: '5', lastReadChapter: '3' });
    const m2 = makeManga({ id: 2, title: 'B', latestChapter: '10', lastReadChapter: '10' });

    beforeEach(() => loadInitial([m1, m2]));

    it('checkNow replaces only the matching manga in the list', () => {
      facade.checkNow(1);
      expect(facade.checkingId()).toBe(1);

      const req = http.expectOne(`${baseUrl}/1/check`);
      expect(req.request.method).toBe('POST');
      const updated = { ...m1, latestChapter: '6' };
      req.flush(updated);

      expect(facade.checkingId()).toBeNull();
      expect(facade.mangaList()).toEqual([updated, m2]);
    });

    it('checkNow on error clears checkingId and surfaces the message', () => {
      facade.checkNow(1);
      const req = http.expectOne(`${baseUrl}/1/check`);
      req.flush({ message: 'MangaDex rate limited' }, { status: 429, statusText: 'Too Many Requests' });

      expect(facade.checkingId()).toBeNull();
      expect(facade.errorMessage()).toContain('MangaDex rate limited');
    });

    it('markRead PATCHes the latest chapter and replaces the item in the list', () => {
      facade.markRead(m1);
      expect(facade.markingReadId()).toBe(1);

      const req = http.expectOne(`${baseUrl}/1/read`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ chapter: '5' });
      const updated = { ...m1, lastReadChapter: '5' };
      req.flush(updated);

      expect(facade.markingReadId()).toBeNull();
      expect(facade.mangaList()).toEqual([updated, m2]);
    });

    it('markRead is a no-op when latestChapter is null', () => {
      const noChapter = makeManga({ id: 99, latestChapter: null });
      facade.markRead(noChapter);
      http.expectNone((r) => r.url.includes('/read'));
      expect(facade.markingReadId()).toBeNull();
    });

    it('removeManga DELETEs and drops the item from mangaList', () => {
      facade.removeManga(1);
      const req = http.expectOne(`${baseUrl}/1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });

      expect(facade.mangaList()).toEqual([m2]);
    });
  });

  describe('removeAll', () => {
    beforeEach(() => loadInitial([makeManga({ id: 1 })]));

    it('returns early with no HTTP call when the user dismisses confirm()', () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
      facade.removeAll();
      expect(confirmSpy).toHaveBeenCalled();
      http.expectNone((r) => r.method === 'DELETE' && r.url === baseUrl);
      expect(facade.removingAll()).toBe(false);
    });

    it('DELETEs the collection and clears mangaList when confirm() is accepted', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      facade.removeAll();
      expect(facade.removingAll()).toBe(true);

      const req = http.expectOne((r) => r.method === 'DELETE' && r.url === baseUrl);
      req.flush(null, { status: 204, statusText: 'No Content' });

      expect(facade.mangaList()).toEqual([]);
      expect(facade.removingAll()).toBe(false);
    });

    it('surfaces backend errors and clears removingAll on failure', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      facade.removeAll();

      const req = http.expectOne((r) => r.method === 'DELETE' && r.url === baseUrl);
      req.flush({ message: 'busy' }, { status: 409, statusText: 'Conflict' });

      expect(facade.removingAll()).toBe(false);
      expect(facade.errorMessage()).toBe('busy');
    });
  });

  describe('checkAll', () => {
    beforeEach(() => loadInitial([makeManga({ id: 1, title: 'X' })]));

    it('POSTs /check-all and triggers a reload that refreshes mangaList on 202', () => {
      facade.checkAll();
      expect(facade.checkingAll()).toBe(true);

      const post = http.expectOne(`${baseUrl}/check-all`);
      expect(post.request.method).toBe('POST');
      post.flush(null, { status: 202, statusText: 'Accepted' });

      const reload = http.expectOne(baseUrl);
      expect(reload.request.method).toBe('GET');
      const updated = [makeManga({ id: 1, title: 'X', latestChapter: '12' })];
      reload.flush(updated);

      expect(facade.checkingAll()).toBe(false);
      expect(facade.mangaList()).toEqual(updated);
      expect(facade.errorMessage()).toBe('');
    });

    it('surfaces 409 conflict errors through errorMessage', () => {
      facade.checkAll();
      const req = http.expectOne(`${baseUrl}/check-all`);
      req.flush({ message: 'already running' }, { status: 409, statusText: 'Conflict' });

      expect(facade.checkingAll()).toBe(false);
      expect(facade.errorMessage()).toBe('already running');
    });

    it('surfaces 429 with Retry-After through the helper', () => {
      facade.checkAll();
      const req = http.expectOne(`${baseUrl}/check-all`);
      req.flush(
        { message: 'Slow down' },
        {
          status: 429,
          statusText: 'Too Many Requests',
          headers: { 'Retry-After': '60' },
        },
      );

      expect(facade.checkingAll()).toBe(false);
      expect(facade.errorMessage()).toBe('Slow down Try again in about a minute.');
    });
  });
});
