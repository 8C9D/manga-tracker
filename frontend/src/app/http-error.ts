import { HttpErrorResponse } from '@angular/common/http';

/**
 * Turn an HTTP error into a user-facing message, preferring the backend's
 * GlobalExceptionHandler envelope ({ message, fieldErrors? }) and falling
 * back gracefully for network failures or unknown shapes.
 *
 * Returns null for 401 so callers don't overwrite the message that
 * auth.interceptor already wired through AuthService (e.g. "Please log in
 * again."). All other statuses produce a string.
 */
export function describeHttpError(err: unknown, fallback: string): string | null {
  if (!(err instanceof HttpErrorResponse)) {
    return fallback;
  }
  if (err.status === 401) {
    return null;
  }
  if (err.status === 0) {
    return 'Cannot reach the server. Is the backend running?';
  }

  const body = err.error;
  const isObject = body !== null && typeof body === 'object' && !Array.isArray(body);

  const message = isObject ? cleanString((body as Record<string, unknown>)['message']) : null;
  const fieldErrorParts = isObject
    ? formatFieldErrors((body as Record<string, unknown>)['fieldErrors'])
    : [];

  const retryAfter = err.status === 429 ? readRetryAfter(err) : null;

  if (message && fieldErrorParts.length > 0) {
    return `${message} (${fieldErrorParts.join('; ')})`;
  }
  if (fieldErrorParts.length > 0) {
    return fieldErrorParts.join('; ');
  }
  if (message && retryAfter) {
    return `${message} Try again in ${retryAfter}.`;
  }
  if (message) {
    return message;
  }
  if (retryAfter) {
    return `${fallback} Try again in ${retryAfter}.`;
  }
  return fallback;
}

function cleanString(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function formatFieldErrors(value: unknown): string[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return [];
  const parts: string[] = [];
  for (const [name, raw] of Object.entries(value as Record<string, unknown>)) {
    const text = cleanString(raw);
    if (text) parts.push(`${name}: ${text}`);
  }
  return parts;
}

function readRetryAfter(err: HttpErrorResponse): string | null {
  // Header is only readable cross-origin if the backend lists it in
  // Access-Control-Expose-Headers; we degrade silently when it isn't.
  const header = err.headers?.get('Retry-After');
  if (!header || !/^\d+$/.test(header)) return null;
  return humanizeSeconds(parseInt(header, 10));
}

function humanizeSeconds(n: number): string {
  if (n <= 1) return '1 second';
  if (n < 60) return `${n} seconds`;
  const minutes = Math.round(n / 60);
  if (minutes === 1) return 'about a minute';
  return `about ${minutes} minutes`;
}
