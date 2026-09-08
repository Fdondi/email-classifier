import { Component, Input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './app-nav.component.html'
})
export class AppNavComponent {
  @Input() spamCount: number | null = null;
}
