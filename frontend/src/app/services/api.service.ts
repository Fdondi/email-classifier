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
  TriageJobResult,
  TriageRun
} from '../models/email.model';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private readonly http: HttpClient) {}

  digest(): Observable<Digest> {
    return this.http.get<Digest>('/api/digest');
  }

  status(): Observable<SystemStatus> {
    return this.http.get<SystemStatus>('/api/status');
  }

  categoryPage(category: Category, offset: number, limit = 5): Observable<CategoryPage> {
    return this.http.get<CategoryPage>(`/api/digest/category/${category}`, {
      params: { offset, limit }
    });
  }

  spam(): Observable<EmailSummary[]> {
    return this.http.get<EmailSummary[]>('/api/digest/spam');
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

  runTriage(): Observable<TriageJobResult> {
    return this.http.post<TriageJobResult>('/api/jobs/triage', {});
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
