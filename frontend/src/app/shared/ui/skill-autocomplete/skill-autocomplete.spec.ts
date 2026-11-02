import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { SkillAutocomplete, SEARCH_DEBOUNCE_MS } from './skill-autocomplete';
import { SKILL_SEARCH, SkillOption, normalizeSkillName } from './skill-search';

describe('SkillAutocomplete', () => {
  const search = vi.fn((query: string) =>
    of<SkillOption[]>(query.startsWith('spr') ? [{ id: '1', name: 'Spring Boot' }] : []),
  );
  const create = vi.fn((name: string) => of<SkillOption>({ id: 'new', name }));

  beforeEach(() => {
    vi.useFakeTimers();
    search.mockClear();
    TestBed.configureTestingModule({
      providers: [{ provide: SKILL_SEARCH, useValue: { search, create } }],
    });
  });

  afterEach(() => vi.useRealTimers());

  function render() {
    const fixture = TestBed.createComponent(SkillAutocomplete);
    fixture.detectChanges();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    const type = (value: string) => {
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    return { fixture, type };
  }

  it('attend 300 ms après la dernière frappe et 2 caractères avant de chercher', () => {
    const { type } = render();

    type('s');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS);
    expect(search).not.toHaveBeenCalled();

    type('sp');
    type('spr');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS - 1);
    expect(search).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(search).toHaveBeenCalledTimes(1);
    expect(search).toHaveBeenCalledWith('spr');
  });

  it('propose de créer une compétence absente, mais pas un doublon d’une proposition', () => {
    const { fixture, type } = render();
    const component = fixture.componentInstance;

    type('Spring  boot');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS);
    // Le faux service ne trouve rien pour « Spring  boot » (majuscule) : la création est proposée
    expect(component['canCreate']()).toBe(true);

    type('spring boot');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS);
    expect(component['options']()).toEqual([{ id: '1', name: 'Spring Boot' }]);
    expect(component['canCreate']()).toBe(false);
  });

  it('normalise les noms comme le serveur', () => {
    expect(normalizeSkillName('  Spring   BOOT ')).toBe('spring boot');
  });
});
