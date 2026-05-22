// @vitest-environment jsdom
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { AuthService } from './auth.service';
import { MangaAppFacade } from './manga-app.facade';

// App now delegates manga state and actions to MangaAppFacade. This spec only
// covers the thin pieces that still live on App: the auth-driven effect and
// the logout passthrough. Orchestration coverage moved to
// manga-app.facade.spec.ts; template/wiring coverage lives in app-template.spec.ts.

const TOKEN_KEY = 'auth.basic';

describe('App', () => {
  let fixture: ComponentFixture<App>;
  let facade: MangaAppFacade;
  let auth: AuthService;
  let loadSpy: ReturnType<typeof vi.spyOn>;
  let clearSpy: ReturnType<typeof vi.spyOn>;

  function createApp(): void {
    fixture = TestBed.createComponent(App);
    facade = TestBed.inject(MangaAppFacade);
    auth = TestBed.inject(AuthService);
    // Stub the facade calls so the effect's behavior is observable without
    // pulling HTTP plumbing in — the real load/clear are covered in the
    // facade spec.
    loadSpy = vi.spyOn(facade, 'loadManga').mockImplementation(() => {});
    clearSpy = vi.spyOn(facade, 'clearForLogout').mockImplementation(() => {});
  }

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  describe('auth-driven effect', () => {
    it('calls facade.loadManga() once when constructed with an authenticated session', () => {
      sessionStorage.setItem(TOKEN_KEY, 'Basic ' + btoa('user:pw'));
      createApp();
      fixture.detectChanges();

      expect(loadSpy).toHaveBeenCalledTimes(1);
      expect(clearSpy).not.toHaveBeenCalled();
    });

    it('calls facade.clearForLogout() when constructed without a session', () => {
      createApp();
      fixture.detectChanges();

      expect(clearSpy).toHaveBeenCalledTimes(1);
      expect(loadSpy).not.toHaveBeenCalled();
    });

    it('calls facade.clearForLogout() when auth flips from true to false', () => {
      sessionStorage.setItem(TOKEN_KEY, 'Basic ' + btoa('user:pw'));
      createApp();
      fixture.detectChanges();
      expect(loadSpy).toHaveBeenCalledTimes(1);

      auth.logout();
      fixture.detectChanges();

      expect(clearSpy).toHaveBeenCalledTimes(1);
    });
  });

  describe('logout()', () => {
    it('delegates to AuthService.logout()', () => {
      sessionStorage.setItem(TOKEN_KEY, 'Basic ' + btoa('user:pw'));
      createApp();
      fixture.detectChanges();
      const authLogout = vi.spyOn(auth, 'logout');

      fixture.componentInstance.logout();

      expect(authLogout).toHaveBeenCalledTimes(1);
    });
  });
});
