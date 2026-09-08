import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { AppNavComponent } from '../../components/app-nav/app-nav.component';
import { EmailListComponent } from '../../components/email-list/email-list.component';
import {
  Category,
  Digest,
  DualAxisSection,
  EmailSummary,
  SummarizedSection,
  SystemStatus,
  TriageJobResult,
  withoutFixtureDigest,
  withoutFixtures
} from '../../models/email.model';
import { ApiService, describeHttpError } from '../../services/api.service';

@Component({
  selector: 'app-digest',
  standalone: true,
  imports: [EmailListComponent, AppNavComponent],
  templateUrl: './digest.component.html'
})
export class DigestComponent implements OnInit {
  @ViewChild('statusDialog') statusDialog?: ElementRef<HTMLDialogElement>;

  digest: Digest | null = null;
  status: SystemStatus | null = null;
  error: string | null = null;
  running = false;
  job: TriageJobResult | null = null;
  extra: Partial<Record<Category, EmailSummary[]>> = {};

  constructor(private readonly api: ApiService) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.extra = {};
    this.api.digest().subscribe({
      next: (digest) => {
        this.digest = withoutFixtureDigest(digest);
      },
      error: (err: unknown) => {
        this.error = describeHttpError(err, 'Failed to load digest');
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

  runTriage(): void {
    this.running = true;
    this.api.runTriage().subscribe({
      next: (job) => {
        this.job = job;
        this.running = false;
        this.reload();
      },
      error: (err: unknown) => {
        this.running = false;
        this.error = describeHttpError(err, 'Triage job failed');
      }
    });
  }

  showMore(category: Category, already: number): void {
    this.api.categoryPage(category, already, 5).subscribe({
      next: (page) => {
        const current = this.extra[category] ?? [];
        this.extra = { ...this.extra, [category]: [...current, ...withoutFixtures(page.items)] };
      }
    });
  }

  extras(category: Category): EmailSummary[] {
    return this.extra[category] ?? [];
  }

  hasDual(section: DualAxisSection): boolean {
    return section.byUrgency.length > 0 || section.byImportance.length > 0;
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
      return `Mail is read from IMAP ${this.status.imapHost} when you run triage or the nightly job. Nothing is downloading while this page is open.`;
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
