// @vitest-environment jsdom
import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { MangaSearchAddComponent } from './manga-search-add.component';
import type { MangaSearchResult } from './manga.service';

interface RenderInputs {
  newTitle?: string;
  isSearching?: boolean;
  searchResults?: MangaSearchResult[];
  showAddAnyway?: boolean;
}

function render(inputs: RenderInputs = {}): ComponentFixture<MangaSearchAddComponent> {
  const fixture = TestBed.createComponent(MangaSearchAddComponent);
  fixture.componentRef.setInput('newTitle', inputs.newTitle ?? '');
  fixture.componentRef.setInput('isSearching', inputs.isSearching ?? false);
  fixture.componentRef.setInput('searchResults', inputs.searchResults ?? []);
  fixture.componentRef.setInput('showAddAnyway', inputs.showAddAnyway ?? false);
  fixture.detectChanges();
  return fixture;
}

describe('MangaSearchAddComponent', () => {
  describe('basic rendering', () => {
    it('renders the search input and a Search submit button by default', () => {
      const fixture = render();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('input[name="title"]')).not.toBeNull();
      const button = el.querySelector('button[type="submit"]') as HTMLButtonElement;
      expect(button).not.toBeNull();
      expect(button.disabled).toBe(false);
      expect(button.textContent?.trim()).toBe('Search');
    });

    it('does not render the search results panel when searchResults is empty', () => {
      const fixture = render({ searchResults: [] });
      expect(fixture.nativeElement.querySelector('.search-results')).toBeNull();
    });

    it('does not render the add-without-MangaDex affordance unless showAddAnyway is true', () => {
      const fixture = render({ showAddAnyway: false });
      expect(fixture.nativeElement.querySelector('.add-anyway')).toBeNull();
      expect(fixture.nativeElement.querySelector('.add-anyway-btn')).toBeNull();
    });
  });

  describe('newTitle model', () => {
    // NgModel inside a <form> registers with NgForm via a resolved promise,
    // so the initial writeValue() to the input element only lands after the
    // microtask queue flushes — hence the await fixture.whenStable() calls.
    it('reflects the parent-provided newTitle in the input value', async () => {
      const fixture = render({ newTitle: 'Naruto' });
      await fixture.whenStable();
      const input = fixture.nativeElement.querySelector('input[name="title"]') as HTMLInputElement;
      expect(input.value).toBe('Naruto');
    });

    it('updates the model signal when the user types in the input', async () => {
      const fixture = render({ newTitle: '' });
      await fixture.whenStable();
      const input = fixture.nativeElement.querySelector('input[name="title"]') as HTMLInputElement;
      input.value = 'Bleach';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(fixture.componentInstance.newTitle()).toBe('Bleach');
    });
  });

  describe('search submit', () => {
    it('emits search once when the form is submitted', () => {
      const fixture = render({ newTitle: 'Naruto' });
      let called = 0;
      fixture.componentInstance.search.subscribe(() => called++);

      const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
      form.dispatchEvent(new Event('submit', { cancelable: true }));
      expect(called).toBe(1);
    });

    it('disables the submit button and shows "Searching..." while isSearching is true', () => {
      const fixture = render({ isSearching: true });
      const button = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
      expect(button.disabled).toBe(true);
      expect(button.textContent?.trim()).toBe('Searching...');
    });
  });

  describe('search results', () => {
    const results: MangaSearchResult[] = [
      { mangadexId: 'mid-1', title: 'Naruto', coverUrl: 'cover1.jpg' },
      { mangadexId: 'mid-2', title: 'Bleach', coverUrl: null },
    ];

    it('renders one .search-result-item per result with the title', () => {
      const fixture = render({ searchResults: results });
      const items = fixture.nativeElement.querySelectorAll('.search-result-item') as NodeListOf<HTMLElement>;
      expect(items.length).toBe(2);

      const titles = Array.from(items).map(
        (li) => li.querySelector('.search-result-title')?.textContent?.trim(),
      );
      expect(titles).toEqual(['Naruto', 'Bleach']);
    });

    it('renders the cover <img> only when the result has a coverUrl', () => {
      const fixture = render({ searchResults: results });
      const items = fixture.nativeElement.querySelectorAll('.search-result-item') as NodeListOf<HTMLElement>;
      expect(items[0].querySelector('img.search-result-cover')).not.toBeNull();
      expect(items[1].querySelector('img.search-result-cover')).toBeNull();
    });

    it('emits confirmAdd with the clicked result', () => {
      const fixture = render({ searchResults: results });
      let emitted: MangaSearchResult | undefined;
      fixture.componentInstance.confirmAdd.subscribe((r) => (emitted = r));

      const addBtns = fixture.nativeElement.querySelectorAll('.add-result-btn') as NodeListOf<HTMLButtonElement>;
      addBtns[1].click();
      expect(emitted).toBe(results[1]);
    });

    it('emits cancel when the cancel-search button is clicked', () => {
      const fixture = render({ searchResults: results });
      let called = 0;
      fixture.componentInstance.cancel.subscribe(() => called++);

      (fixture.nativeElement.querySelector('.cancel-search-btn') as HTMLButtonElement).click();
      expect(called).toBe(1);
    });
  });

  describe('add without MangaDex', () => {
    it('renders the .add-anyway-btn when showAddAnyway is true', () => {
      const fixture = render({ showAddAnyway: true });
      expect(fixture.nativeElement.querySelector('.add-anyway-btn')).not.toBeNull();
    });

    it('emits addNoSource when the .add-anyway-btn is clicked', () => {
      const fixture = render({ showAddAnyway: true });
      let called = 0;
      fixture.componentInstance.addNoSource.subscribe(() => called++);

      (fixture.nativeElement.querySelector('.add-anyway-btn') as HTMLButtonElement).click();
      expect(called).toBe(1);
    });
  });
});
