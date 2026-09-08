import { DatePipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CATEGORIES, Category, EmailDetail, categoryLabel } from '../../models/email.model';
import { ApiService, describeHttpError } from '../../services/api.service';

@Component({
  selector: 'app-email-detail',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './email-detail.component.html'
})
export class EmailDetailComponent implements OnInit {
  email: EmailDetail | null = null;
  error: string | null = null;
  bodyUrl: SafeResourceUrl | null = null;
  loadingImages = false;
  savingCategory = false;
  readonly categories = CATEGORIES;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly api: ApiService,
    private readonly sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (Number.isNaN(id)) {
      this.error = 'Invalid email id';
      return;
    }
    this.api.email(id).subscribe({
      next: (email) => {
        this.email = email;
        this.setBodyUrl(email.id, false);
      },
      error: () => {
        this.error = 'Email not found';
      }
    });
  }

  categoryName(category: Category): string {
    return categoryLabel(category);
  }

  /** Gmail web search by RFC822 Message-ID (works without Gmail API / thread ids). */
  gmailUrl(messageId: string | null | undefined): string | null {
    if (!messageId) {
      return null;
    }
    const id = messageId.replace(/^<|>$/g, '').trim();
    if (!id) {
      return null;
    }
    return `https://mail.google.com/mail/u/0/#search/rfc822msgid:${encodeURIComponent(id)}`;
  }

  reassign(event: Event): void {
    if (!this.email) {
      return;
    }
    const select = event.target as HTMLSelectElement;
    const next = select.value as Category;
    if (!next || next === this.email.category) {
      return;
    }
    this.savingCategory = true;
    this.api.correctCategory(this.email.id, next).subscribe({
      next: (email) => {
        this.savingCategory = false;
        this.email = { ...email, imagesLoaded: this.email?.imagesLoaded ?? email.imagesLoaded };
      },
      error: (err: unknown) => {
        this.savingCategory = false;
        select.value = this.email?.category ?? '';
        this.error = describeHttpError(err, 'Could not save correction');
      }
    });
  }

  loadImages(): void {
    if (!this.email) {
      return;
    }
    this.loadingImages = true;
    this.api.loadImages(this.email.id).subscribe({
      next: () => {
        this.loadingImages = false;
        this.email = { ...this.email!, imagesLoaded: true };
        this.setBodyUrl(this.email.id, true);
      },
      error: () => {
        this.loadingImages = false;
        this.error = 'Could not load remote images';
      }
    });
  }

  private setBodyUrl(id: number, loaded: boolean): void {
    const suffix = loaded ? '?loaded=1' : '';
    this.bodyUrl = this.sanitizer.bypassSecurityTrustResourceUrl(`/api/emails/${id}/body${suffix}`);
  }
}
