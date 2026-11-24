import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription, switchMap, timer } from 'rxjs';
import { AppNavComponent } from '../../components/app-nav/app-nav.component';
import { DualAxisChartComponent } from '../../components/dual-axis-chart/dual-axis-chart.component';
import { EmailListComponent } from '../../components/email-list/email-list.component';
import { BUILD_VERSION } from '../../build-version';
import {
  ApplicationLearning,
  Category,
  Digest,
  DualAxisSection,
  EmailSummary,
  SummarizedSection,
  SystemStatus,
  TriageProgress,
  withoutFixtureDigest,
  withoutFixtures
} from '../../models/email.model';
import { ApiService, describeHttpError } from '../../services/api.service';

// Browser-local YYYY-MM-DDTHH:mm, the format <input type="datetime-local"> uses.
function localDateTime(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-digest',
  standalone: true,
  imports: [EmailListComponent, AppNavComponent, DualAxisChartComponent, RouterLink],
  templateUrl: './digest.component.html'
})
export class DigestComponent implements OnInit, OnDestroy {
  @ViewChild('statusDialog') statusDialog?: ElementRef<HTMLDialogElement>;

  readonly frontendVersion = BUILD_VERSION;
  digest: Digest | null = null;
  status: SystemStatus | null = null;
  learning: ApplicationLearning | null = null;
  error: string | null = null;
  running = false;
  includeRead = false;
  // Default window is the last 24 hours; the picker shows the start of that window.
  since = localDateTime(new Date(Date.now() - 24 * 60 * 60 * 1000));
  job: TriageProgress | null = null;
  extra: Partial<Record<Category, EmailSummary[]>> = {};

  private progressPoll: Subscription | null = null;

  constructor(private readonly api: ApiService) {}

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    this.stopProgressPoll();
  }

  reload(): void {
    this.extra = {};
    this.api.digest(this.includeRead, this.sinceIso()).subscribe({
      next: (digest) => {
        this.digest = withoutFixtureDigest(digest);
      },
      error: (err: unknown) => {
        this.error = describeHttpError(err, 'Failed to load digest');
      }
    });
    this.api.applicationLearning().subscribe({
      next: (learning) => {
        this.learning = learning;
      },
      error: (err: unknown) => {
        this.error = describeHttpError(err, 'Failed to load application learning');
      }
    });
    this.api.status().subscribe({
      next: (status) => {
        this.status = status;
      },
      error: (err: unknown) => {
        this.error = describeHttpError(err, 'Failed to load status');
      }
    });
  }

  setIncludeRead(includeRead: boolean): void {
    this.includeRead = includeRead;
    this.reload();
  }

  setSince(since: string): void {
    if (!since) {
      return;
    }
    this.since = since;
    this.reload();
  }

  private sinceIso(): string {
    return new Date(this.since).toISOString();
  }

  runTriage(): void {
    this.running = true;
    this.error = null;
    this.api.runTriage().subscribe({
      next: (progress) => {
        this.job = progress;
        this.startProgressPoll();
      },
      error: (err: unknown) => {
        this.running = false;
        this.error = describeHttpError(err, 'Triage job failed');
      }
    });
  }

  progressLine(): string {
    if (!this.job) {
      return '';
    }
    const j = this.job;
    const classified = j.classifiedByRule + j.classifiedByLlm + j.fallback;
    const phaseLabel = this.phaseLabel(j.phase);
    const parts = [phaseLabel];
    if (j.phase === 'DOWNLOADING' || j.ingested + j.skipped + j.ingestFailed > 0) {
      parts.push(`downloaded ${j.ingested}, skipped ${j.skipped}` + (j.ingestFailed ? `, failed ${j.ingestFailed}` : ''));
    }
    if (j.phase === 'CLASSIFYING' || j.phase === 'RANKING' || j.phase === 'JOBS' || j.phase === 'DONE' || j.phase === 'FAILED' || classified > 0) {
      const ofTotal = j.toClassify > 0 ? ` / ${j.toClassify}` : '';
      parts.push(`classified ${classified}${ofTotal} (rules ${j.classifiedByRule}, LLM ${j.classifiedByLlm}, fallback ${j.fallback})`);
    }
    return parts.join(' · ');
  }

  private phaseLabel(phase: string): string {
    switch (phase) {
      case 'IDLE':
        return 'Idle';
      case 'STARTING_LLM':
        return 'Starting LLM…';
      case 'DOWNLOADING':
        return 'Downloading mail…';
      case 'CLASSIFYING':
        return 'Classifying…';
      case 'RANKING':
        return 'Ranking…';
      case 'JOBS':
        return 'Picking job candidates…';
      case 'DONE':
        return 'Done';
      case 'FAILED':
        return 'Failed';
      default:
        return phase;
    }
  }

  private startProgressPoll(): void {
    this.stopProgressPoll();
    this.progressPoll = timer(0, 500)
      .pipe(switchMap(() => this.api.triageProgress()))
      .subscribe({
        next: (progress) => {
          this.job = progress;
          if (!progress.running) {
            this.running = false;
            this.stopProgressPoll();
            this.reload();
          }
        },
        error: (err: unknown) => {
          this.running = false;
          this.stopProgressPoll();
          this.error = describeHttpError(err, 'Failed to read triage progress');
        }
      });
  }

  private stopProgressPoll(): void {
    this.progressPoll?.unsubscribe();
    this.progressPoll = null;
  }

  showMore(category: Category, already: number): void {
    this.api.categoryPage(category, already, 5, this.includeRead, this.sinceIso()).subscribe({
      next: (page) => {
        const current = this.extra[category] ?? [];
        this.extra = { ...this.extra, [category]: [...current, ...withoutFixtures(page.items)] };
      }
    });
  }

  extras(category: Category): EmailSummary[] {
    return this.extra[category] ?? [];
  }

  onRated(section: 'actionNeeded' | 'events' | 'jobOffers', next: DualAxisSection): void {
    if (!this.digest) {
      return;
    }
    this.digest = { ...this.digest, [section]: next };
  }

  hasDual(section: DualAxisSection): boolean {
    return section.byUrgency.length > 0 || section.byImportance.length > 0 || (section.allEmails?.length ?? 0) > 0;
  }

  hasSummarized(section: SummarizedSection, category: Category): boolean {
    return section.top.length + this.extras(category).length > 0 || section.total > 0;
  }

  openStatusInfo(): void {
    this.statusDialog?.nativeElement.showModal();
  }

  closeStatusInfo(): void {
    this.statusDialog?.nativeElement.close();
  }

  onStatusDialogClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closeStatusInfo();
    }
  }

  mailProblem(): string {
    if (!this.status) {
      return '';
    }
    if (this.status.mailSource === 'imap' && !this.status.imapCredentialsPresent) {
      return `Mail source is IMAP (${this.status.imapHost}) but username/password are missing. Copy config/secrets.example.yml to config/secrets.yml.`;
    }
    if (this.status.mailSource !== 'imap') {
      return 'Mail source is local files (testdata), not your inbox. Set app.mail.source to imap in config/secrets.yml.';
    }
    return '';
  }

  mailInfo(): string {
    if (!this.status) {
      return '';
    }
    if (this.status.mailSource === 'imap') {
      return `Mail is read from IMAP ${this.status.imapHost} (INBOX only; archived mail is skipped). Triage downloads and rates unread and read mail; the digest hides read unless Include read is on. Nothing downloads while this page is open.`;
    }
    return 'Mail source is local files (testdata), not your inbox.';
  }

  llmProblem(): string {
    if (!this.status) {
      return '';
    }
    if (!this.status.llmReachable) {
      return `LLM at ${this.status.llmBaseUrl} is not reachable${this.status.llmError ? ': ' + this.status.llmError : ''}. Run triage will try to start LM Studio if the GPU is free.`;
    }
    return '';
  }

  llmInfo(): string {
    if (!this.status) {
      return '';
    }
    if (!this.status.llmReachable) {
      return `LLM at ${this.status.llmBaseUrl} is not reachable. Run triage will try to start LM Studio if the GPU is free.`;
    }
    if (this.status.llmModels.length === 0) {
      return `LLM server is up at ${this.status.llmBaseUrl} but no chat model is in VRAM. Run triage will load Gemma if the GPU is free, then unload it afterward.`;
    }
    const model = this.status.llmModel ?? this.status.llmModels.join(', ');
    return `LLM: ${model} at ${this.status.llmBaseUrl}`;
  }
}
