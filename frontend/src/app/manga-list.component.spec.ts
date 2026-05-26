// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { MangaListComponent } from './manga-list.component';
import type { Manga } from './manga.service';
import { makeManga } from './testing';

interface RenderInputs {
  mangaList?: Manga[];
  sortBy?: string;
  checkingId?: number | null;
  markingReadId?: number | null;
  checkingAll?: boolean;
  removingAll?: boolean;
}

function render(inputs: RenderInputs = {}) {
  const fixture = TestBed.createComponent(MangaListComponent);
  fixture.componentRef.setInput('mangaList', inputs.mangaList ?? []);
  fixture.componentRef.setInput('sortBy', inputs.sortBy ?? 'next-check');
  fixture.componentRef.setInput('checkingId', inputs.checkingId ?? null);
  fixture.componentRef.setInput('markingReadId', inputs.markingReadId ?? null);
  fixture.componentRef.setInput('checkingAll', inputs.checkingAll ?? false);
  fixture.componentRef.setInput('removingAll', inputs.removingAll ?? false);
  fixture.detectChanges();
  return fixture;
}

describe('MangaListComponent', () => {
  // Pin the clock so "Due ..." text assertions are stable; matches manga-utils.spec.
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-05-20T12:00:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('empty state', () => {
    it('renders the empty-state message and no list/header when mangaList is empty', () => {
      const fixture = render({ mangaList: [] });
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('No manga tracked yet');
      expect(el.querySelector('.manga-list')).toBeNull();
      expect(el.querySelector('.list-header')).toBeNull();
    });
  });

  describe('non-empty list', () => {
    it('renders one list item per manga with title and latest chapter', () => {
      const fixture = render({
        mangaList: [
          makeManga({ id: 1, title: 'Naruto', latestChapter: '700', lastReadChapter: '700' }),
          makeManga({ id: 2, title: 'Bleach', latestChapter: '686', lastReadChapter: '686' }),
        ],
      });
      const items = fixture.nativeElement.querySelectorAll('.manga-list li') as NodeListOf<HTMLElement>;
      expect(items.length).toBe(2);

      const titles = Array.from(items).map((li) => li.querySelector('.manga-title')?.textContent?.trim());
      expect(titles).toEqual(['Naruto', 'Bleach']);

      expect(items[0].textContent).toContain('Ch. 700');
      expect(items[1].textContent).toContain('Ch. 686');
    });

    it('renders the next-check display when nextCheckDate is set', () => {
      const fixture = render({
        mangaList: [
          makeManga({
            title: 'Soon',
            latestChapter: '10',
            lastReadChapter: '10',
            nextCheckDate: '2026-05-22',
          }),
        ],
      });
      expect(fixture.nativeElement.textContent).toContain('Due in 2 days');
    });

    it('renders "Check pending" when nextCheckDate is null but the source exists', () => {
      const fixture = render({
        mangaList: [makeManga({ title: 'Fresh', latestChapter: '1', lastReadChapter: '1' })],
      });
      expect(fixture.nativeElement.textContent).toContain('Check pending');
    });

    it('renders "No chapter yet" when latestChapter is null and the source exists', () => {
      const fixture = render({
        mangaList: [makeManga({ title: 'Pending', latestChapter: null })],
      });
      expect(fixture.nativeElement.textContent).toContain('No chapter yet');
    });

    it('renders "Not on MangaDex" and hides the check button for noSource manga', () => {
      const fixture = render({
        mangaList: [makeManga({ title: 'Doujin', noSource: true })],
      });
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('Not on MangaDex');
      expect(el.querySelector('.check-btn')).toBeNull();
    });
  });

  describe('derived display text', () => {
    // manga-utils.spec covers chaptersBehind/daysUntil edge cases; here we just
    // verify the wiring renders the helper output, not every input variation.
    it('renders chapters-behind text when latest > lastRead', () => {
      const fixture = render({
        mangaList: [makeManga({ latestChapter: '100', lastReadChapter: '95' })],
      });
      const behind = fixture.nativeElement.querySelector('.behind') as HTMLElement | null;
      expect(behind?.textContent?.trim()).toBe('5 ch. behind');
    });

    it('omits chapters-behind text when caught up', () => {
      const fixture = render({
        mangaList: [makeManga({ latestChapter: '100', lastReadChapter: '100' })],
      });
      expect(fixture.nativeElement.querySelector('.behind')).toBeNull();
    });

    it('renders the days-until display for a future nextCheckDate', () => {
      const fixture = render({
        mangaList: [
          makeManga({
            latestChapter: '10',
            lastReadChapter: '10',
            nextCheckDate: '2026-05-21',
          }),
        ],
      });
      expect(fixture.nativeElement.textContent).toContain('Due tomorrow');
    });
  });

  describe('output events', () => {
    it('emits checkNow with the manga id when the per-item check button is clicked', () => {
      const fixture = render({
        mangaList: [makeManga({ id: 42, latestChapter: '5', lastReadChapter: '4' })],
      });
      let emitted: number | undefined;
      fixture.componentInstance.checkNow.subscribe((id) => (emitted = id));

      (fixture.nativeElement.querySelector('.check-btn') as HTMLButtonElement).click();
      expect(emitted).toBe(42);
    });

    it('emits markRead with the manga object when the mark-read button is clicked', () => {
      const manga = makeManga({ id: 7, latestChapter: '10', lastReadChapter: '8' });
      const fixture = render({ mangaList: [manga] });
      let emitted: Manga | undefined;
      fixture.componentInstance.markRead.subscribe((m) => (emitted = m));

      (fixture.nativeElement.querySelector('.mark-read-btn') as HTMLButtonElement).click();
      expect(emitted).toBe(manga);
    });

    it('emits remove with the manga id when the remove button is clicked', () => {
      const fixture = render({
        mangaList: [makeManga({ id: 9 })],
      });
      let emitted: number | undefined;
      fixture.componentInstance.remove.subscribe((id) => (emitted = id));

      (fixture.nativeElement.querySelector('.remove-btn') as HTMLButtonElement).click();
      expect(emitted).toBe(9);
    });

    it('emits checkAll when the header check-all button is clicked', () => {
      const fixture = render({ mangaList: [makeManga({ id: 1 })] });
      let called = 0;
      fixture.componentInstance.checkAll.subscribe(() => called++);

      (fixture.nativeElement.querySelector('.check-all-btn') as HTMLButtonElement).click();
      expect(called).toBe(1);
    });

    it('emits removeAll when the header remove-all button is clicked', () => {
      const fixture = render({ mangaList: [makeManga({ id: 1 })] });
      let called = 0;
      fixture.componentInstance.removeAll.subscribe(() => called++);

      (fixture.nativeElement.querySelector('.remove-all-btn') as HTMLButtonElement).click();
      expect(called).toBe(1);
    });

    it('emits sortByChange with the selected value when the sort dropdown changes', () => {
      const fixture = render({ mangaList: [makeManga({ id: 1 })] });
      let emitted: string | undefined;
      fixture.componentInstance.sortByChange.subscribe((v) => (emitted = v));

      const select = fixture.nativeElement.querySelector('.sort-select') as HTMLSelectElement;
      select.value = 'title';
      select.dispatchEvent(new Event('change'));
      expect(emitted).toBe('title');
    });
  });

  describe('action state', () => {
    it('disables the check button and shows the loading label for the matching checkingId', () => {
      const fixture = render({
        mangaList: [makeManga({ id: 1, latestChapter: '5', lastReadChapter: '4' })],
        checkingId: 1,
      });
      const btn = fixture.nativeElement.querySelector('.check-btn') as HTMLButtonElement;
      expect(btn.disabled).toBe(true);
      expect(btn.textContent?.trim()).toBe('...');
    });

    it('disables the mark-read button and shows the loading label for the matching markingReadId', () => {
      const fixture = render({
        mangaList: [makeManga({ id: 2, latestChapter: '5', lastReadChapter: '3' })],
        markingReadId: 2,
      });
      const btn = fixture.nativeElement.querySelector('.mark-read-btn') as HTMLButtonElement;
      expect(btn.disabled).toBe(true);
      expect(btn.textContent?.trim()).toBe('...');
    });

    it('disables and re-labels the header buttons when checkingAll/removingAll are true', () => {
      const fixture = render({
        mangaList: [makeManga({ id: 1 })],
        checkingAll: true,
        removingAll: true,
      });
      const checkAll = fixture.nativeElement.querySelector('.check-all-btn') as HTMLButtonElement;
      const removeAll = fixture.nativeElement.querySelector('.remove-all-btn') as HTMLButtonElement;
      expect(checkAll.disabled).toBe(true);
      expect(checkAll.textContent?.trim()).toBe('Checking...');
      expect(removeAll.disabled).toBe(true);
      expect(removeAll.textContent?.trim()).toBe('Removing...');
    });
  });
});
