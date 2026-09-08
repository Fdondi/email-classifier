import { Component, Input, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { BUILD_VERSION } from '../../build-version';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './app-nav.component.html'
})
export class AppNavComponent implements OnInit {
  @Input() spamCount: number | null = null;
  @Input() hiddenCount: number | null = null;

  readonly frontendVersion = BUILD_VERSION;
  backendVersion: string | null = null;

  constructor(private readonly api: ApiService) {}

  ngOnInit(): void {
    this.api.status().subscribe({
      next: (status) => {
        this.backendVersion = status.buildVersion;
      },
      error: () => {
        this.backendVersion = null;
      }
    });
  }
}
