import { Routes } from '@angular/router';
import { DigestComponent } from './pages/digest/digest.component';
import { EmailDetailComponent } from './pages/email-detail/email-detail.component';
import { HiddenComponent } from './pages/hidden/hidden.component';
import { RunsComponent } from './pages/runs/runs.component';
import { SpamComponent } from './pages/spam/spam.component';

export const routes: Routes = [
  { path: '', component: DigestComponent },
  { path: 'email/:id', component: EmailDetailComponent },
  { path: 'runs', component: RunsComponent },
  { path: 'spam', component: SpamComponent },
  { path: 'hidden', component: HiddenComponent }
];
