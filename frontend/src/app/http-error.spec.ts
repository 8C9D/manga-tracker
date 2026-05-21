import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { describeHttpError } from './http-error';

const fallback = 'Something went wrong.';

function errorWith(opts: { status: number; error?: unknown; headers?: HttpHeaders }): HttpErrorResponse {
  return new HttpErrorResponse({
    status: opts.status,
    error: opts.error,
    headers: opts.headers,
  });
}

describe('describeHttpError', () => {
  it('returns the backend message when the body has one', () => {
    const err = errorWith({ status: 400, error: { message: 'Title is required' } });
    expect(describeHttpError(err, fallback)).toBe('Title is required');
  });

  it('joins fieldErrors with semicolons when no top-level message', () => {
    const err = errorWith({
      status: 400,
      error: { fieldErrors: { title: 'must not be blank', q: 'too short' } },
    });
    expect(describeHttpError(err, fallback)).toBe('title: must not be blank; q: too short');
  });

  it('appends fieldErrors in parens when a top-level message exists', () => {
    const err = errorWith({
      status: 400,
      error: { message: 'Validation failed', fieldErrors: { title: 'must not be blank' } },
    });
    expect(describeHttpError(err, fallback)).toBe('Validation failed (title: must not be blank)');
  });

  it('skips non-string fieldErrors values without breaking the rest', () => {
    const err = errorWith({
      status: 400,
      error: { fieldErrors: { title: 'must not be blank', count: 42, missing: '' } },
    });
    expect(describeHttpError(err, fallback)).toBe('title: must not be blank');
  });

  it('humanizes Retry-After seconds and appends to backend message on 429', () => {
    const err = errorWith({
      status: 429,
      error: { message: 'Slow down' },
      headers: new HttpHeaders({ 'Retry-After': '60' }),
    });
    expect(describeHttpError(err, fallback)).toBe('Slow down Try again in about a minute.');
  });

  it('humanizes sub-minute Retry-After as "N seconds"', () => {
    const err = errorWith({
      status: 429,
      error: null,
      headers: new HttpHeaders({ 'Retry-After': '30' }),
    });
    expect(describeHttpError(err, fallback)).toBe(`${fallback} Try again in 30 seconds.`);
  });

  it('humanizes Retry-After = 1 as singular "1 second"', () => {
    const err = errorWith({
      status: 429,
      error: null,
      headers: new HttpHeaders({ 'Retry-After': '1' }),
    });
    expect(describeHttpError(err, fallback)).toBe(`${fallback} Try again in 1 second.`);
  });

  it('humanizes multi-minute Retry-After as "about N minutes"', () => {
    const err = errorWith({
      status: 429,
      error: null,
      headers: new HttpHeaders({ 'Retry-After': '180' }),
    });
    expect(describeHttpError(err, fallback)).toBe(`${fallback} Try again in about 3 minutes.`);
  });

  it('falls back gracefully on 429 when Retry-After is hidden (e.g. not CORS-exposed)', () => {
    const err = errorWith({ status: 429, error: null });
    expect(describeHttpError(err, fallback)).toBe(fallback);
  });

  it('ignores non-numeric Retry-After values like HTTP-date', () => {
    const err = errorWith({
      status: 429,
      error: null,
      headers: new HttpHeaders({ 'Retry-After': 'Wed, 21 Oct 2026 07:28:00 GMT' }),
    });
    expect(describeHttpError(err, fallback)).toBe(fallback);
  });

  it('returns null for 401 so auth.interceptor owns the message', () => {
    const err = errorWith({ status: 401, error: { message: 'Unauthorized' } });
    expect(describeHttpError(err, fallback)).toBeNull();
  });

  it('returns the network message when status is 0', () => {
    const err = errorWith({ status: 0, error: null });
    expect(describeHttpError(err, fallback)).toBe('Cannot reach the server. Is the backend running?');
  });

  it('falls back when the body is a plain string', () => {
    const err = errorWith({ status: 500, error: 'Internal Server Error' });
    expect(describeHttpError(err, fallback)).toBe(fallback);
  });

  it('falls back when the body is null', () => {
    const err = errorWith({ status: 500, error: null });
    expect(describeHttpError(err, fallback)).toBe(fallback);
  });

  it('falls back when the body is an array', () => {
    const err = errorWith({ status: 500, error: ['nope'] });
    expect(describeHttpError(err, fallback)).toBe(fallback);
  });

  it('falls back when message is present but blank', () => {
    const err = errorWith({ status: 500, error: { message: '   ' } });
    expect(describeHttpError(err, fallback)).toBe(fallback);
  });

  it('falls back when the input is not an HttpErrorResponse at all', () => {
    expect(describeHttpError(new Error('boom'), fallback)).toBe(fallback);
    expect(describeHttpError('boom', fallback)).toBe(fallback);
    expect(describeHttpError(undefined, fallback)).toBe(fallback);
  });
});
