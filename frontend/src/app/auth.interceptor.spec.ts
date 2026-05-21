// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

const url = 'http://example.test/api/some';
const TOKEN_KEY = 'auth.basic';
const STORED_TOKEN = 'Basic ' + btoa('user:pw');

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    // AuthService is injected per test (after seeding sessionStorage) so the
    // singleton picks up the right initial token on first construction.
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  describe('outgoing Authorization header', () => {
    it('attaches the stored token when the request has none', () => {
      sessionStorage.setItem(TOKEN_KEY, STORED_TOKEN);
      const auth = TestBed.inject(AuthService);
      expect(auth.token()).toBe(STORED_TOKEN);

      http.get(url).subscribe();
      const req = httpMock.expectOne(url);
      expect(req.request.headers.get('Authorization')).toBe(STORED_TOKEN);
      req.flush({});
    });

    it('does not overwrite an Authorization header that the caller already set', () => {
      sessionStorage.setItem(TOKEN_KEY, STORED_TOKEN);
      TestBed.inject(AuthService);
      const oneOff = 'Basic ' + btoa('candidate:guess');

      http.get(url, { headers: { Authorization: oneOff } }).subscribe();
      const req = httpMock.expectOne(url);
      expect(req.request.headers.get('Authorization')).toBe(oneOff);
      req.flush({});
    });

    it('does not attach an Authorization header when no token is stored', () => {
      const auth = TestBed.inject(AuthService);
      expect(auth.token()).toBeNull();

      http.get(url).subscribe();
      const req = httpMock.expectOne(url);
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });
  });

  describe('401 handling', () => {
    it('logs out with "Please log in again." when 401 fires while authenticated', () => {
      sessionStorage.setItem(TOKEN_KEY, STORED_TOKEN);
      const auth = TestBed.inject(AuthService);
      expect(auth.isAuthenticated()).toBe(true);

      let received: HttpErrorResponse | undefined;
      http.get(url).subscribe({
        next: () => {},
        error: (err: HttpErrorResponse) => (received = err),
      });

      httpMock
        .expectOne(url)
        .flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

      expect(auth.token()).toBeNull();
      expect(auth.isAuthenticated()).toBe(false);
      expect(auth.message()).toBe('Please log in again.');
      expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
      expect(received).toBeInstanceOf(HttpErrorResponse);
      expect(received?.status).toBe(401);
    });

    it('does not log out or set the session-expired message on 401 when unauthenticated', () => {
      const auth = TestBed.inject(AuthService);
      expect(auth.isAuthenticated()).toBe(false);

      let received: HttpErrorResponse | undefined;
      http.get(url).subscribe({
        next: () => {},
        error: (err: HttpErrorResponse) => (received = err),
      });

      httpMock
        .expectOne(url)
        .flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

      // The session-expired text is reserved for "was-logged-in → got booted".
      // LoginComponent owns the "invalid credentials" message for its own 401s.
      expect(auth.message()).toBeNull();
      expect(received?.status).toBe(401);
    });
  });

  describe('non-401 errors', () => {
    it.each([400, 409, 429, 500])(
      'propagates a %s error without logging out and leaves the stored token intact',
      (status) => {
        sessionStorage.setItem(TOKEN_KEY, STORED_TOKEN);
        const auth = TestBed.inject(AuthService);

        let received: HttpErrorResponse | undefined;
        http.get(url).subscribe({
          next: () => {},
          error: (err: HttpErrorResponse) => (received = err),
        });

        httpMock
          .expectOne(url)
          .flush({ message: 'boom' }, { status, statusText: 'Err' });

        expect(received?.status).toBe(status);
        expect(auth.token()).toBe(STORED_TOKEN);
        expect(auth.isAuthenticated()).toBe(true);
        expect(auth.message()).toBeNull();
      },
    );
  });
});
