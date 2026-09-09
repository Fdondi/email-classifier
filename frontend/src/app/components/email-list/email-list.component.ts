import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, EventEmitter, HostListener, Input, OnChanges, Output } from '@angular/core';
import { Router } from '@angular/router';
import {
  CATEGORIES,
  Category,
  EmailSummary,
  categoryLabel,
  isExpiredDueDate,
  partitionExpiredEmails
} from '../../models/email.model';
import { ApiService, describeHttpError } from '../../services/api.service';
import { importanceScore, urgencyScore } from '../dual-axis-chart/dual-axis-chart.layout';
import { EmailReorderAxis, insertScore, moveItem, spliceGroup } from './email-list.reorder';

type ActionMenuKind = 'category' | 'deadline';

const EXPIRED_SECTION_OPEN_KEY = 'email-tool.expired-section-open';

function loadExpiredSectionOpen(): boolean {
  try {
    return localStorage.getItem(EXPIRED_SECTION_OPEN_KEY) !== '0';
  } catch {
    return true;
  }
}

let expiredSectionOpen = loadExpiredSectionOpen();

@Component({
  selector: 'app-email-list',
  standalone: true,
  imports: [DatePipe, NgTemplateOutlet],
  templateUrl: './email-list.component.html'
})
export class EmailListComponent implements OnChanges {
  @Input({ required: true }) emails: EmailSummary[] = [];
  @Input() emptyText = 'None';
  /** When true, show Unhide instead of Hide (Hidden page). */
  @Input() recoveryMode = false;
  @Input() reorderAxis: EmailReorderAxis = 'list';
  @Output() categoryChanged = new EventEmitter<void>();
  @Output() visibilityChanged = new EventEmitter<void>();
  @Output() failed = new EventEmitter<string>();

  readonly categories = CATEGORIES;
  busyId: number | null = null;
  items: EmailSummary[] = [];
  menu: { id: number; kind: ActionMenuKind } | null = null;
  draggingId: number | null = null;
  dropTargetId: number | null = null;

  private itemKey = '';

  constructor(
    private readonly api: ApiService,
    private readonly router: Router
  ) {}

  ngOnChanges(): void {
    const key = this.emails.map((email) => email.id).join(',');
    if (key !== this.itemKey) {
      this.itemKey = key;
      this.items = [...this.emails];
      return;
    }
    this.items = this.items.map((item) => this.emails.find((email) => email.id === item.id) ?? item);
  }

  label(category: EmailSummary['category']): string {
    return categoryLabel(category);
  }

  groups(): { collapsible: boolean; emails: EmailSummary[] }[] {
    const { expired, current } = partitionExpiredEmails(this.items);
    const groups: { collapsible: boolean; emails: EmailSummary[] }[] = [];
    if (expired.length) {
      groups.push({ collapsible: true, emails: expired });
    }
    if (current.length) {
      groups.push({ collapsible: false, emails: current });
    }
    return groups;
  }

  expiredOpen(): boolean {
    return expiredSectionOpen;
  }

  toggleExpired(): void {
    expiredSectionOpen = !expiredSectionOpen;
    try {
      localStorage.setItem(EXPIRED_SECTION_OPEN_KEY, expiredSectionOpen ? '1' : '0');
    } catch {
      /* private mode / quota — preference is session-only */
    }
  }

  expired(email: EmailSummary): boolean {
    return isExpiredDueDate(email.dueDate);
  }

  menuOpen(email: EmailSummary, kind: ActionMenuKind): boolean {
    return this.menu?.id === email.id && this.menu.kind === kind;
  }

  toggleMenu(email: EmailSummary, kind: ActionMenuKind, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.menu = this.menuOpen(email, kind) ? null : { id: email.id, kind };
  }

  @HostListener('document:click')
  closeMenu(): void {
    this.menu = null;
  }

  @HostListener('document:keydown.escape')
  closeMenuOnEscape(): void {
    this.menu = null;
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

  pickCategory(email: EmailSummary, next: Category, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    if (next === email.category) {
      this.menu = null;
      return;
    }
    this.busyId = email.id;
    this.api.correctCategory(email.id, next).subscribe({
      next: () => {
        this.busyId = null;
        this.menu = null;
        this.categoryChanged.emit();
      },
      error: (err: unknown) => {
        this.busyId = null;
        this.failed.emit(describeHttpError(err, 'Could not save correction'));
      }
    });
  }

  saveDueDate(email: EmailSummary, event: Event): void {
    const input = event.target as HTMLInputElement;
    this.saveDueDateValue(email, input.value ? input.value : null);
  }

  saveDueDateValue(email: EmailSummary, dueDate: string | null, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    if (dueDate === email.dueDate) {
      this.menu = null;
      return;
    }
    this.busyId = email.id;
    this.api.correctDueDate(email.id, dueDate).subscribe({
      next: () => {
        this.busyId = null;
        this.menu = null;
        this.categoryChanged.emit();
      },
      error: (err: unknown) => {
        this.busyId = null;
        this.failed.emit(describeHttpError(err, 'Could not save deadline'));
      }
    });
  }

  toggleActionNeeded(email: EmailSummary, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.busyId = email.id;
    this.menu = null;
    this.api.correctActionNeeded(email.id, !email.actionNeeded).subscribe({
      next: () => {
        this.busyId = null;
        this.categoryChanged.emit();
      },
      error: (err: unknown) => {
        this.busyId = null;
        this.failed.emit(describeHttpError(err, 'Could not save action needed'));
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

  onDragStart(event: DragEvent, email: EmailSummary): void {
    event.stopPropagation();
    this.draggingId = email.id;
    this.menu = null;
    event.dataTransfer?.setData('text/plain', String(email.id));
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
    }
  }

  onDragOver(event: DragEvent, email: EmailSummary): void {
    if (this.draggingId == null || this.draggingId === email.id) {
      return;
    }
    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
    }
    this.dropTargetId = email.id;
  }

  onDrop(event: DragEvent, target: EmailSummary, group: EmailSummary[]): void {
    event.preventDefault();
    event.stopPropagation();
    const fromId = this.draggingId;
    this.clearDrag();
    if (fromId == null || fromId === target.id) {
      return;
    }
    const from = group.findIndex((email) => email.id === fromId);
    const to = group.findIndex((email) => email.id === target.id);
    if (from < 0 || to < 0) {
      return;
    }
    const previous = this.items;
    const nextGroup = moveItem(group, from, to);
    this.items = spliceGroup(this.items, group, nextGroup);
    const moved = nextGroup.find((email) => email.id === fromId);
    if (moved) {
      this.persistReorder(nextGroup, moved, previous);
    }
  }

  onDragEnd(): void {
    this.clearDrag();
  }

  private clearDrag(): void {
    this.draggingId = null;
    this.dropTargetId = null;
  }

  private persistReorder(group: EmailSummary[], moved: EmailSummary, previous: EmailSummary[]): void {
    const idx = group.findIndex((email) => email.id === moved.id);
    const before = idx > 0 ? group[idx - 1] : undefined;
    const after = idx >= 0 && idx < group.length - 1 ? group[idx + 1] : undefined;
    switch (this.reorderAxis) {
      case 'urgency': {
        const urgency = insertScore(
          before ? urgencyScore(before) : undefined,
          after ? urgencyScore(after) : undefined
        );
        const importance = importanceScore(moved);
        this.saveRating(moved, urgency, importance, urgencyScore(moved), importance, previous);
        return;
      }
      case 'importance': {
        const importance = insertScore(
          before ? importanceScore(before) : undefined,
          after ? importanceScore(after) : undefined
        );
        const urgency = urgencyScore(moved);
        this.saveRating(moved, urgency, importance, urgency, importanceScore(moved), previous);
        return;
      }
      case 'list': {
        const ids = this.groups().flatMap((item) => item.emails.map((email) => email.id));
        this.busyId = moved.id;
        this.api.reorderEmails(ids).subscribe({
          next: () => {
            this.busyId = null;
            this.categoryChanged.emit();
          },
          error: (err: unknown) => {
            this.busyId = null;
            this.items = previous;
            this.failed.emit(describeHttpError(err, 'Could not save order'));
          }
        });
        return;
      }
      default: {
        const exhaustive: never = this.reorderAxis;
        return exhaustive;
      }
    }
  }

  private saveRating(
    email: EmailSummary,
    urgency: number,
    importance: number,
    predictedUrgency: number,
    predictedImportance: number,
    previous: EmailSummary[]
  ): void {
    this.busyId = email.id;
    this.api
      .rateEmail(email.id, { urgency, importance, predictedUrgency, predictedImportance })
      .subscribe({
        next: () => {
          this.busyId = null;
          this.categoryChanged.emit();
        },
        error: (err: unknown) => {
          this.busyId = null;
          this.items = previous;
          this.failed.emit(describeHttpError(err, 'Could not save order'));
        }
      });
  }
}
