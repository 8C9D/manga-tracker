import type { Manga } from './manga.service';

export function makeManga(overrides: Partial<Manga> = {}): Manga {
  return {
    id: 1,
    title: 'Untitled',
    coverUrl: null,
    latestChapter: null,
    lastReadChapter: null,
    nextCheckDate: null,
    noSource: false,
    ...overrides,
  };
}
