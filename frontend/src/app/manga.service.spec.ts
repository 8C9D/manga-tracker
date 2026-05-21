// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { MangaService, type Manga, type MangaSearchResult } from './manga.service';
import { environment } from '../environments/environment';

const baseUrl = `${environment.apiUrl}/api/manga`;

const sampleManga: Manga = {
  id: 1,
  title: 'Naruto',
  coverUrl: null,
  latestChapter: '700',
  lastReadChapter: '699',
  nextCheckDate: null,
  noSource: false,
};

describe('MangaService', () => {
  let service: MangaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MangaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  describe('list()', () => {
    it('GETs /api/manga and yields the response body', () => {
      let received: Manga[] | undefined;
      service.list().subscribe((data) => (received = data));

      const req = http.expectOne(baseUrl);
      expect(req.request.method).toBe('GET');
      expect(req.request.body).toBeNull();
      req.flush([sampleManga]);

      expect(received).toEqual([sampleManga]);
    });
  });

  describe('search(q)', () => {
    it('GETs /api/manga/search with q as a query parameter', () => {
      let received: MangaSearchResult[] | undefined;
      service.search('naruto').subscribe((d) => (received = d));

      const req = http.expectOne(
        (r) => r.method === 'GET' && r.url === `${baseUrl}/search`,
      );
      expect(req.request.params.get('q')).toBe('naruto');
      const body: MangaSearchResult[] = [
        { mangadexId: 'abc', title: 'Naruto', coverUrl: null },
      ];
      req.flush(body);

      expect(received).toEqual(body);
    });

    it('URL-encodes special characters in q via HttpParams', () => {
      service.search('one piece & more').subscribe();
      const req = http.expectOne(
        (r) => r.method === 'GET' && r.url === `${baseUrl}/search`,
      );
      expect(req.request.params.get('q')).toBe('one piece & more');
      // urlWithParams is what actually hits the wire — verify encoding happens there.
      expect(req.request.urlWithParams).toContain('q=one%20piece%20%26%20more');
      req.flush([]);
    });
  });

  describe('add()', () => {
    it('POSTs to /api/manga with all fields when fully specified', () => {
      service.add('My Title', 'mid-1', 'https://cdn/c.jpg', true).subscribe();

      const req = http.expectOne(baseUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        title: 'My Title',
        mangadexId: 'mid-1',
        coverUrl: 'https://cdn/c.jpg',
        noSource: true,
      });
      req.flush(sampleManga);
    });

    it('defaults noSource to false when omitted', () => {
      service.add('Bare title').subscribe();

      const req = http.expectOne(baseUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body.title).toBe('Bare title');
      expect(req.request.body.noSource).toBe(false);
      req.flush(sampleManga);
    });

    it('passes a null coverUrl through to the request body', () => {
      service.add('No cover', 'mid-2', null, false).subscribe();

      const req = http.expectOne(baseUrl);
      expect(req.request.body.coverUrl).toBeNull();
      req.flush(sampleManga);
    });
  });

  describe('check(id)', () => {
    it('POSTs to /api/manga/{id}/check with a null body', () => {
      service.check(42).subscribe();

      const req = http.expectOne(`${baseUrl}/42/check`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toBeNull();
      req.flush(sampleManga);
    });
  });

  describe('checkAll()', () => {
    it('POSTs to /api/manga/check-all with a null body and emits void', () => {
      let emitted = false;
      let value: void | undefined;
      service.checkAll().subscribe((v) => {
        emitted = true;
        value = v;
      });

      const req = http.expectOne(`${baseUrl}/check-all`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toBeNull();
      req.flush(null, { status: 202, statusText: 'Accepted' });

      expect(emitted).toBe(true);
      expect(value).toBeUndefined();
    });
  });

  describe('markRead(id, chapter)', () => {
    it('PATCHes /api/manga/{id}/read with { chapter }', () => {
      service.markRead(5, '37').subscribe();

      const req = http.expectOne(`${baseUrl}/5/read`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ chapter: '37' });
      req.flush(sampleManga);
    });
  });

  describe('remove(id)', () => {
    it('DELETEs /api/manga/{id} and emits void on 204', () => {
      let emitted = false;
      service.remove(7).subscribe(() => (emitted = true));

      const req = http.expectOne(`${baseUrl}/7`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });

      expect(emitted).toBe(true);
    });
  });

  describe('removeAll()', () => {
    it('DELETEs /api/manga (collection root) and emits void on 204', () => {
      let emitted = false;
      service.removeAll().subscribe(() => (emitted = true));

      const req = http.expectOne(baseUrl);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });

      expect(emitted).toBe(true);
    });
  });
});
