// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LoginComponent } from './login.component';
import { AuthService } from './auth.service';
import { environment } from '../environments/environment';

const mangaUrl = `${environment.apiUrl}/api/manga`;
const TOKEN_KEY = 'auth.basic';

function setup(username: string, password: string) {
  const component = TestBed.createComponent(LoginComponent).componentInstance;
  component.username = username;
  component.password = password;
  return {
    component,
    auth: TestBed.inject(AuthService),
    http: TestBed.inject(HttpTestingController),
  };
}

describe('LoginComponent', () => {
  beforeEach(() => {
    // Fresh storage per test so the AuthService singleton in TestBed sees a
    // clean token signal on construction.
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    sessionStorage.clear();
  });

  describe('input validation', () => {
    it('does not send a request when username is empty', () => {
      const { component, http } = setup('', 'pw');
      component.submit();
      http.expectNone(mangaUrl);
      expect(component.submitting()).toBe(false);
    });

    it('does not send a request when password is empty', () => {
      const { component, http } = setup('user', '');
      component.submit();
      http.expectNone(mangaUrl);
      expect(component.submitting()).toBe(false);
    });
  });

  describe('credential verification request', () => {
    it('GETs /api/manga with a one-off Basic header from the submitted credentials', () => {
      const { component, http } = setup('alice', 'secret');
      component.submit();

      const req = http.expectOne(mangaUrl);
      expect(req.request.method).toBe('GET');
      expect(req.request.headers.get('Authorization')).toBe('Basic ' + btoa('alice:secret'));
      req.flush([]);
    });

    it('uses the submitted credentials even when a stale AuthService token is already in sessionStorage', () => {
      // Seed before component creation so AuthService picks it up on construction.
      sessionStorage.setItem(TOKEN_KEY, 'Basic ' + btoa('stale:old'));
      const { component, http } = setup('alice', 'secret');

      component.submit();
      const req = http.expectOne(mangaUrl);
      expect(req.request.headers.get('Authorization')).toBe('Basic ' + btoa('alice:secret'));
      req.flush([]);
    });

    it('flips submitting to true and clears any stale auth message before the request resolves', () => {
      const { component, auth, http } = setup('alice', 'secret');
      auth.setMessage('stale');

      component.submit();
      expect(component.submitting()).toBe(true);
      expect(auth.message()).toBeNull();

      http.expectOne(mangaUrl).flush([]);
    });
  });

  describe('successful login (200)', () => {
    it('stores the submitted credentials in AuthService and sessionStorage, clears submitting', () => {
      const { component, auth, http } = setup('alice', 'secret');
      component.submit();
      http.expectOne(mangaUrl).flush([]);

      const expectedToken = 'Basic ' + btoa('alice:secret');
      expect(auth.token()).toBe(expectedToken);
      expect(auth.isAuthenticated()).toBe(true);
      expect(sessionStorage.getItem(TOKEN_KEY)).toBe(expectedToken);
      expect(component.submitting()).toBe(false);
      expect(auth.message()).toBeNull();
    });
  });

  describe('failed login', () => {
    it('on 401: sets "Invalid username or password.", clears submitting, stores nothing', () => {
      const { component, auth, http } = setup('alice', 'wrong');
      component.submit();
      http.expectOne(mangaUrl).flush(
        { message: 'Unauthorized' },
        { status: 401, statusText: 'Unauthorized' },
      );

      expect(auth.message()).toBe('Invalid username or password.');
      expect(auth.token()).toBeNull();
      expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
      expect(component.submitting()).toBe(false);
    });

    it('on status 0: surfaces the network-unavailable message, stores nothing', () => {
      const { component, auth, http } = setup('alice', 'secret');
      component.submit();
      http.expectOne(mangaUrl).flush(null, { status: 0, statusText: 'Unknown Error' });

      expect(auth.message()).toBe('Cannot reach the server. Is the backend running?');
      expect(auth.token()).toBeNull();
      expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
      expect(component.submitting()).toBe(false);
    });

    it('on other HTTP errors: surfaces "Login failed (status N).", stores nothing', () => {
      const { component, auth, http } = setup('alice', 'secret');
      component.submit();
      http.expectOne(mangaUrl).flush(
        { message: 'boom' },
        { status: 500, statusText: 'Server Error' },
      );

      expect(auth.message()).toBe('Login failed (status 500).');
      expect(auth.token()).toBeNull();
      expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
      expect(component.submitting()).toBe(false);
    });
  });
});
