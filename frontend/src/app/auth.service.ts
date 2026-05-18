import { Injectable, computed, signal } from '@angular/core';

const TOKEN_KEY = 'auth.basic';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private tokenSignal = signal<string | null>(sessionStorage.getItem(TOKEN_KEY));
  private messageSignal = signal<string | null>(null);

  readonly token = this.tokenSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenSignal() !== null);
  readonly message = this.messageSignal.asReadonly();

  setCredentials(username: string, password: string): void {
    const token = 'Basic ' + btoa(`${username}:${password}`);
    sessionStorage.setItem(TOKEN_KEY, token);
    this.tokenSignal.set(token);
    this.messageSignal.set(null);
  }

  logout(reason?: string): void {
    sessionStorage.removeItem(TOKEN_KEY);
    this.tokenSignal.set(null);
    if (reason) this.messageSignal.set(reason);
  }

  setMessage(message: string | null): void {
    this.messageSignal.set(message);
  }
}
