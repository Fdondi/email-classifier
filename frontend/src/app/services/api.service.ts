import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Category,
  CategoryPage,
  Digest,
  EmailDetail,
  EmailSummary,
  SystemStatus,
  TriageProgress,
  TriageRun
} from '../models/email.model';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private readonly http: HttpClient) {}

  digest(includeRead = false): Observable<Digest> {
    return this.http.get<Digest>('/api/digest', {
      params: { includeRead: String(includeRead) }
    });
  }

  status(): Observable<SystemStatus> {
    return this.http.get<SystemStatus>('/api/status');
  }

  categoryPage(category: Category, offset: number, limit = 5, includeRead = false): Observable<CategoryPage> {
    return this.http.get<CategoryPage>(`/api/digest/category/${category}`, {
      params: { offset, limit, includeRead: String(includeRead) }
    });
  }

  spam(includeRead = false): Observable<EmailSummary[]> {
    return this.http.get<EmailSummary[]>('/api/digest/spam', {
      params: { includeRead: String(includeRead) }
    });
  }

  email(id: number): Observable<EmailDetail> {
    return this.http.get<EmailDetail>(`/api/emails/${id}`);
  }

  loadImages(id: number): Observable<{ loaded: number }> {
    return this.http.post<{ loaded: number }>(`/api/emails/${id}/load-images`, {});
  }

  correctCategory(id: number, category: Category): Observable<EmailDetail> {
    return this.http.post<EmailDetail>(`/api/emails/${id}/corrections`, { category });
  }

  correctActionNeeded(id: number, actionNeeded: boolean): Observable<EmailDetail> {
    return this.http.post<EmailDetail>(`/api/emails/${id}/corrections`, { actionNeeded });
  }

  rateEmail(
    id: number,
    body: {
      urgency: number;
      importance: number;
      predictedUrgency: number;
      predictedImportance: number;
    }
  ): Observable<EmailDetail> {
    return this.http.post<EmailDetail>(`/api/emails/${id}/rating`, body);
  }

  correctDueDate(id: number, dueDate: string | null): Observable<EmailDetail> {
    return this.http.post<EmailDetail>(`/api/emails/${id}/due-date`, { dueDate });
  }

  reorderEmails(ids: number[]): Observable<void> {
    return this.http.post<void>('/api/digest/reorder', { ids });
  }

  hideEmail(id: number): Observable<{ hidden: boolean }> {
    return this.http.post<{ hidden: boolean }>(`/api/emails/${id}/hide`, {});
  }

  unhideEmail(id: number): Observable<{ hidden: boolean }> {
    return this.http.post<{ hidden: boolean }>(`/api/emails/${id}/unhide`, {});
  }

  hidden(offset = 0, limit = 50): Observable<EmailSummary[]> {
    return this.http.get<EmailSummary[]>('/api/digest/hidden', {
      params: { offset, limit }
    });
  }

  runTriage(): Observable<TriageProgress> {
    return this.http.post<TriageProgress>('/api/jobs/triage', {});
  }

  triageProgress(): Observable<TriageProgress> {
    return this.http.get<TriageProgress>('/api/jobs/triage');
  }

  runs(): Observable<TriageRun[]> {
    return this.http.get<TriageRun[]>('/api/jobs/runs');
  }
}

export function describeHttpError(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error;
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    if (body && typeof body === 'object') {
      const message = (body as { message?: unknown }).message;
      if (typeof message === 'string' && message.trim()) {
        return message;
      }
    }
    return `HTTP ${err.status} ${err.statusText}`;
  }
  if (err instanceof Error) {
    return err.message;
  }
  return fallback;
}
