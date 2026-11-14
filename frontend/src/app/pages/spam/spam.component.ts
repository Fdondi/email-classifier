import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { AppNavComponent } from '../../components/app-nav/app-nav.component';
import { EmailListComponent } from '../../components/email-list/email-list.component';
import { EmailSummary, withoutFixtures } from '../../models/email.model';
import { ApiService } from '../../services/api.service';

@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-spam',
  standalone: true,
  imports: [EmailListComponent, AppNavComponent],
  templateUrl: './spam.component.html'
})
export class SpamComponent implements OnInit {
  emails: EmailSummary[] = [];
  error: string | null = null;

  constructor(private readonly api: ApiService) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api.spam().subscribe({
      next: (emails) => {
        this.emails = withoutFixtures(emails);
      }
    });
  }
}
