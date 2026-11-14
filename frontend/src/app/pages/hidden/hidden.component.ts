import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { AppNavComponent } from '../../components/app-nav/app-nav.component';
import { EmailListComponent } from '../../components/email-list/email-list.component';
import { EmailSummary, withoutFixtures } from '../../models/email.model';
import { ApiService } from '../../services/api.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-hidden',
  standalone: true,
  imports: [EmailListComponent, AppNavComponent],
  templateUrl: './hidden.component.html'
})
export class HiddenComponent implements OnInit {
  emails: EmailSummary[] = [];
  error: string | null = null;

  constructor(private readonly api: ApiService) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api.hidden().subscribe({
      next: (emails) => {
        this.emails = withoutFixtures(emails);
      },
      error: () => {
        this.error = 'Could not load hidden mail';
      }
    });
  }
}
