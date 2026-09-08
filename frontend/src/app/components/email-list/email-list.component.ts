import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Router } from '@angular/router';
import { CATEGORIES, Category, EmailSummary, categoryLabel } from '../../models/email.model';
import { ApiService, describeHttpError } from '../../services/api.service';

@Component({
  selector: 'app-email-list',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './email-list.component.html'
})
export class EmailListComponent {
  @Input({ required: true }) emails: EmailSummary[] = [];
  @Input() emptyText = 'None';
  /** When true, show Unhide instead of Hide (Hidden page). */
  @Input() recoveryMode = false;
  @Output() categoryChanged = new EventEmitter<void>();
  @Output() visibilityChanged = new EventEmitter<void>();
  @Output() failed = new EventEmitter<string>();

  readonly categories = CATEGORIES;
  busyId: number | null = null;

  constructor(
    private readonly api: ApiService,
    private readonly router: Router
  ) {}

  label(category: EmailSummary['category']): string {
    return categoryLabel(category);
  }

  /** Open Gmail when we have a deep link; otherwise stay in-app. */
  openRow(email: EmailSummary, event: MouseEvent): void {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
      return;
    }
    if (email.gmailUrl) {
      event.preventDefault();
      window.open(email.gmailUrl, '_blank', 'noopener,noreferrer');
      return;
    }
    event.preventDefault();
    void this.router.navigate(['/email', email.id]);
  }

  reassign(email: EmailSummary, event: Event): void {
    const select = event.target as HTMLSelectElement;
    const next = select.value as Category;
    if (!next || next === email.category) {
      return;
    }
    this.busyId = email.id;
    this.api.correctCategory(email.id, next).subscribe({
      next: () => {
        this.busyId = null;
        this.categoryChanged.emit();
      },
      error: (err: unknown) => {
        this.busyId = null;
        select.value = email.category ?? '';
        this.failed.emit(describeHttpError(err, 'Could not save correction'));
      }
    });
  }

  hide(email: EmailSummary, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.busyId = email.id;
    this.api.hideEmail(email.id).subscribe({
      next: () => {
        this.busyId = null;
        this.visibilityChanged.emit();
      },
      error: (err: unknown) => {
        this.busyId = null;
        this.failed.emit(describeHttpError(err, 'Could not hide email'));
      }
    });
  }

  unhide(email: EmailSummary, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.busyId = email.id;
    this.api.unhideEmail(email.id).subscribe({
      next: () => {
        this.busyId = null;
        this.visibilityChanged.emit();
      },
      error: (err: unknown) => {
        this.busyId = null;
        this.failed.emit(describeHttpError(err, 'Could not unhide email'));
      }
    });
  }
}
