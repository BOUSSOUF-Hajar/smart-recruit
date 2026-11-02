import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Observable } from 'rxjs';
import { isApiError } from '../../../core/errors/api-error';
import { NotificationService } from '../../../core/errors/notification.service';
import { SKILL_LEVEL_LABELS, SkillLevel } from '../../../shared/models/job';
import { AiApi } from './ai.api';
import {
  AI_ERROR_MESSAGES,
  AiStatus,
  AiTask,
  MATCH_LABELS,
  MatchResult,
  ResumeAnalysis,
  isActive,
  percent,
} from './ai.models';

/**
 * Onglet « Assistance IA » du dossier (US-41, US-42). L'IA extrait et explique, le backend calcule
 * le score, le recruteur décide : la mention « aide à la décision » accompagne chaque résultat.
 * Chargé à la demande (`@defer` dans le dossier) ; masqué si l'IA est désactivée (D7.3).
 */
@Component({
  selector: 'app-ai-panel',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ai-panel.html',
  styleUrl: './ai-panel.scss',
})
export class AiPanel {
  private readonly api = inject(AiApi);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly applicationId = input.required<string>();

  protected readonly status = httpResource<AiStatus>(() => '/api/v1/ai/status');
  protected readonly enabled = computed(
    () => this.status.hasValue() && this.status.value().enabled,
  );

  /** 404 = jamais calculé : ce n'est pas une erreur, l'écran propose de lancer le calcul. */
  protected readonly analysis = httpResource<ResumeAnalysis>(
    () => `/api/v1/applications/${this.applicationId()}/ai/resume-analysis`,
  );
  protected readonly match = httpResource<MatchResult>(
    () => `/api/v1/applications/${this.applicationId()}/ai/matching`,
  );
  protected readonly analysisData = computed(() =>
    this.analysis.hasValue() ? this.analysis.value() : null,
  );
  protected readonly matchData = computed(() =>
    this.match.hasValue() ? this.match.value() : null,
  );

  /** Tâche en cours, suivie toutes les 3 s. */
  protected readonly running = signal<AiTask | null>(null);
  protected readonly failure = signal<string | null>(null);

  protected readonly requiredMissing = computed(
    () => this.matchData()?.missingSkills.filter((s) => s.required) ?? [],
  );
  protected readonly preferredMissing = computed(
    () => this.matchData()?.missingSkills.filter((s) => !s.required) ?? [],
  );

  protected label(match: MatchResult): string {
    return MATCH_LABELS[match.label];
  }

  protected percent(value: number): string {
    return percent(value);
  }

  protected level(level: SkillLevel | null): string {
    return level ? SKILL_LEVEL_LABELS[level] : '';
  }

  protected analyse(): void {
    this.start(this.api.analyse(this.applicationId()));
  }

  protected computeMatch(): void {
    this.start(this.api.match(this.applicationId()));
  }

  private start(request: Observable<AiTask>): void {
    this.failure.set(null);
    request.subscribe({
      next: (task) => this.follow(task),
      error: (error: unknown) => {
        if (isApiError(error)) {
          this.notifications.error(error.detail ?? error.title);
        }
      },
    });
  }

  private follow(task: AiTask): void {
    this.running.set(task);
    void this.announcer.announce($localize`Analyse en cours.`, 'polite');
    this.api
      .watch(task.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (current) => {
          if (isActive(current)) {
            this.running.set(current);
            return;
          }
          this.running.set(null);
          if (current.status === 'DONE') {
            this.analysis.reload();
            this.match.reload();
            void this.announcer.announce(
              $localize`Résultat de l'assistance IA disponible.`,
              'polite',
            );
          } else {
            const message = current.errorCode
              ? AI_ERROR_MESSAGES[current.errorCode]
              : AI_ERROR_MESSAGES.AI_UNAVAILABLE;
            this.failure.set(message);
            void this.announcer.announce(message, 'assertive');
          }
        },
        error: () => this.running.set(null),
      });
  }
}
