import { TestBed } from '@angular/core/testing';
import { CompletenessCard } from './completeness-card';
import { formatSize } from '../data-access/profile-labels';

describe('CompletenessCard', () => {
  it('affiche le pourcentage et mène à l’onglet du critère manquant', () => {
    const fixture = TestBed.createComponent(CompletenessCard);
    fixture.componentRef.setInput('completeness', { percent: 85, missing: ['resume'] });
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const tabs: number[] = [];
    fixture.componentInstance.goTo.subscribe((tab) => tabs.push(tab));

    expect(element.textContent).toContain('85 %');
    const button = element.querySelector<HTMLButtonElement>('ul button');
    expect(button?.textContent).toContain('Un CV');
    button?.click();

    expect(tabs).toEqual([4]);
  });

  it('félicite un profil complet', () => {
    const fixture = TestBed.createComponent(CompletenessCard);
    fixture.componentRef.setInput('completeness', { percent: 100, missing: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Votre profil est complet.');
  });
});

describe('formatSize', () => {
  it('affiche des tailles lisibles en français', () => {
    expect(formatSize(182340)).toBe('178 Ko');
    expect(formatSize(1468006)).toBe('1,4 Mo');
  });
});
