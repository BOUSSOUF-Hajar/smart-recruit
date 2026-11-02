import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AiApi } from './ai.api';
import { AiTask, percent } from './ai.models';

describe('AiApi.watch', () => {
  const task = (status: AiTask['status']): AiTask => ({
    id: 't-1',
    type: 'MATCHING',
    applicationId: 'a-1',
    status,
    errorCode: status === 'FAILED' ? 'AI_UNAVAILABLE' : null,
    createdAt: '2026-10-22T10:00:00Z',
    finishedAt: null,
  });

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => vi.useRealTimers());

  it("interroge la tâche toutes les 3 s jusqu'à son état final, puis s'arrête", () => {
    const http = TestBed.inject(HttpTestingController);
    const seen: string[] = [];
    let completed = false;
    TestBed.inject(AiApi)
      .watch('t-1')
      .subscribe({ next: (t) => seen.push(t.status), complete: () => (completed = true) });

    vi.advanceTimersByTime(0);
    http.expectOne('/api/v1/ai/tasks/t-1').flush(task('PENDING'));
    vi.advanceTimersByTime(3000);
    http.expectOne('/api/v1/ai/tasks/t-1').flush(task('RUNNING'));
    vi.advanceTimersByTime(3000);
    http.expectOne('/api/v1/ai/tasks/t-1').flush(task('DONE'));
    vi.advanceTimersByTime(9000);

    http.expectNone('/api/v1/ai/tasks/t-1');
    expect(seen).toEqual(['PENDING', 'RUNNING', 'DONE']);
    expect(completed).toBe(true);
  });

  it('affiche les couvertures en pourcentage', () => {
    expect(percent(0.333)).toBe('33 %');
    expect(percent(1)).toBe('100 %');
  });
});
