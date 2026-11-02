import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { Observable, of } from 'rxjs';
import { AiApi } from './ai.api';
import { AiTask, MatchResult } from './ai.models';
import { AiPanel } from './ai-panel';

describe('AiPanel', () => {
  const watch = vi.fn<(id: string) => Observable<AiTask>>();
  const match = vi.fn<(id: string) => Observable<AiTask>>();
  const announce = vi.fn(() => Promise.resolve());

  const result: MatchResult = {
    score: 81,
    label: 'STRONG',
    breakdown: {
      requiredCoverage: 0.9,
      preferredCoverage: 0.333,
      experienceFit: 1,
      formulaVersion: 'v1',
    },
    matchedSkills: [
      {
        skillId: 's-1',
        name: 'REST API',
        source: 'RESUME',
        level: null,
        evidence: "Conception d'API REST",
        required: true,
        belowRequiredLevel: false,
      },
    ],
    missingSkills: [
      { skillId: 's-2', name: 'Kafka', required: false },
      { skillId: 's-3', name: 'Go', required: true },
    ],
    strengths: ['Bonne couverture'],
    weaknesses: [],
    summary: 'Synthèse',
    warnings: [],
    disclaimer: "Indicateur d'aide à la décision. Il ne remplace pas l'évaluation du recruteur.",
    model: { name: 'qwen', promptVersion: 'match-explain-v1' },
    createdAt: '2026-10-22T10:00:00Z',
  };

  beforeEach(() => {
    watch.mockReset();
    match.mockReset();
    announce.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AiApi, useValue: { watch, match, analyse: match } },
        { provide: LiveAnnouncer, useValue: { announce } },
      ],
    });
  });

  async function render(enabled: boolean, matching: MatchResult | null) {
    const fixture = TestBed.createComponent(AiPanel);
    fixture.componentRef.setInput('applicationId', 'a-1');
    fixture.detectChanges();
    TestBed.tick();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/ai/status').flush({ enabled, model: enabled ? 'qwen' : null });
    http
      .match('/api/v1/applications/a-1/ai/resume-analysis')
      .forEach((r) => r.flush(null, { status: 404, statusText: 'Not Found' }));
    http
      .match('/api/v1/applications/a-1/ai/matching')
      .forEach((r) =>
        matching ? r.flush(matching) : r.flush(null, { status: 404, statusText: 'Not Found' }),
      );
    await fixture.whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement, http };
  }

  it("masque les boutons quand l'IA est désactivée", async () => {
    const { element } = await render(false, null);

    expect(element.querySelector('button')).toBeNull();
    expect(element.textContent).toContain('désactivée');
  });

  it('montre le score, sa décomposition, les manques séparés et la mention « aide à la décision »', async () => {
    const { element } = await render(true, result);

    expect(element.textContent).toContain('81 / 100 — Forte adéquation');
    expect(element.textContent).toContain('90 %');
    expect(element.textContent).toContain('33 %');
    expect(element.textContent).toContain("« Conception d'API REST »");
    expect(element.textContent).toContain('aide à la décision');
    const lists = [...element.querySelectorAll('h3')].map((h) => h.textContent?.trim());
    expect(lists).toContain('Compétences requises manquantes');
    expect(lists).toContain('Compétences souhaitées manquantes');
  });

  it("annonce l'échec avec un message explicite", async () => {
    match.mockReturnValue(of({ id: 't-1', status: 'PENDING' } as AiTask));
    watch.mockReturnValue(
      of({ id: 't-1', status: 'FAILED', errorCode: 'FILE_UNREADABLE' } as AiTask),
    );
    const { fixture, element } = await render(true, null);

    [...element.querySelectorAll('button')]
      .find((b) => b.textContent?.includes('adéquation'))!
      .click();
    await fixture.whenStable();

    expect(element.querySelector('[role=alert]')?.textContent).toContain(
      'CV non analysable automatiquement',
    );
    expect(announce).toHaveBeenCalledWith(
      expect.stringContaining('CV non analysable'),
      'assertive',
    );
  });
});
