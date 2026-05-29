import { chaptersBehind, daysUntil } from './manga-utils';

describe('chaptersBehind', () => {
  it('returns 0 when latest is null', () => {
    expect(chaptersBehind(null, '10')).toBe(0);
  });

  it('returns 0 when lastRead is null', () => {
    expect(chaptersBehind('10', null)).toBe(0);
  });

  it('returns 0 when both are null', () => {
    expect(chaptersBehind(null, null)).toBe(0);
  });

  it('returns 0 when caught up exactly', () => {
    expect(chaptersBehind('100', '100')).toBe(0);
  });

  it('returns 0 when lastRead is ahead of latest', () => {
    expect(chaptersBehind('100', '101')).toBe(0);
  });

  it('returns the integer count when behind by whole chapters', () => {
    expect(chaptersBehind('100', '95')).toBe(5);
    expect(chaptersBehind('700', '699')).toBe(1);
  });

  it('floors fractional behind counts', () => {
    expect(chaptersBehind('100', '95.5')).toBe(4);
    expect(chaptersBehind('100.5', '95')).toBe(5);
  });

  it('handles fractional chapters on both sides', () => {
    expect(chaptersBehind('12.5', '10.25')).toBe(2);
  });

  // MangaDex chapter identifiers can be non-numeric (e.g. "Oneshot"). parseFloat
  // yields NaN for those, and the NaN > 0 comparison is false, so the badge
  // degrades to 0 ("caught up") rather than rendering NaN.
  it('returns 0 when latest is non-numeric', () => {
    expect(chaptersBehind('Oneshot', '10')).toBe(0);
  });

  it('returns 0 when lastRead is non-numeric', () => {
    expect(chaptersBehind('100', 'Oneshot')).toBe(0);
  });

  it('returns 0 when both are non-numeric', () => {
    expect(chaptersBehind('foo', 'bar')).toBe(0);
  });
});

describe('daysUntil', () => {
  // Fake the clock to make day-diff assertions deterministic; pick mid-day to dodge
  // DST/midnight rounding edge cases.
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-05-20T12:00:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns empty string for null', () => {
    expect(daysUntil(null)).toBe('');
  });

  it('returns "today" when the target date is today', () => {
    expect(daysUntil('2026-05-20')).toBe('today');
  });

  it('returns "tomorrow" when the target date is one day out', () => {
    expect(daysUntil('2026-05-21')).toBe('tomorrow');
  });

  it('returns "overdue" for any past date', () => {
    expect(daysUntil('2026-05-19')).toBe('overdue');
    expect(daysUntil('2026-01-01')).toBe('overdue');
  });

  it('returns "in N days" for future dates beyond tomorrow', () => {
    expect(daysUntil('2026-05-25')).toBe('in 5 days');
    expect(daysUntil('2026-06-03')).toBe('in 14 days');
  });
});
