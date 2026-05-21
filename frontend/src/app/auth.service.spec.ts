// @vitest-environment jsdom
import { AuthService } from './auth.service';

const TOKEN_KEY = 'auth.basic';

describe('AuthService', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('starts unauthenticated when nothing is in sessionStorage', () => {
    const auth = new AuthService();
    expect(auth.token()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.message()).toBeNull();
  });

  it('rehydrates the token from sessionStorage on construction', () => {
    sessionStorage.setItem(TOKEN_KEY, 'Basic existing');
    const auth = new AuthService();
    expect(auth.token()).toBe('Basic existing');
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('stores a Basic-prefixed base64 token under the expected key', () => {
    const auth = new AuthService();
    auth.setCredentials('dev', 'local-dev-only');
    const expected = 'Basic ' + btoa('dev:local-dev-only');
    expect(auth.token()).toBe(expected);
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe(expected);
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('clears any stale message when credentials are set', () => {
    const auth = new AuthService();
    auth.setMessage('Please log in again.');
    auth.setCredentials('dev', 'pw');
    expect(auth.message()).toBeNull();
  });

  it('logout removes the token from sessionStorage and the signal', () => {
    sessionStorage.setItem(TOKEN_KEY, 'Basic x');
    const auth = new AuthService();
    auth.logout();
    expect(auth.token()).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('logout with a reason surfaces it via the message signal', () => {
    const auth = new AuthService();
    auth.logout('Please log in again.');
    expect(auth.message()).toBe('Please log in again.');
  });

  it('logout without a reason leaves the existing message alone', () => {
    const auth = new AuthService();
    auth.setMessage('still here');
    auth.logout();
    expect(auth.message()).toBe('still here');
  });

  it('setMessage can clear the message with null', () => {
    const auth = new AuthService();
    auth.setMessage('boom');
    auth.setMessage(null);
    expect(auth.message()).toBeNull();
  });
});
