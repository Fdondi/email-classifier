import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CATEGORIES, Category, EmailSummary, categoryLabel } from '../../models/email.model';
import { ApiService, describeHttpError } from '../../services/api.service';

@Component({
  selector: 'app-email-list',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './email-list.component.html'
})
export class EmailListComponent {
  @Input({ required: true }) emails: EmailSummary[] = [];
  @Input() emptyText = 'None';
  @Output() categoryChanged = new EventEmitter<void>();
  @Output() failed = new EventEmitter<string>();

  readonly categories = CATEGORIES;
  busyId: number | null = null;

  constructor(private readonly api: ApiService) {}

  label(category: EmailSummary['category']): string {
    return categoryLabel(category);
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
}
