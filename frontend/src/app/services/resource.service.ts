import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StackitResource, BillingSummary, ResourceSearchResult } from '../models/resource.model';

@Injectable({
  providedIn: 'root',
})
export class ResourceService {
  private readonly http = inject(HttpClient);

  getResources(query?: string): Observable<ResourceSearchResult> {
    let params = new HttpParams();
    if (query && query.trim().length > 0) {
      params = params.set('q', query.trim());
    }
    return this.http.get<ResourceSearchResult>('/resources', { params });
  }

  getBillingSummary(): Observable<BillingSummary[]> {
    return this.http.get<BillingSummary[]>('/resources/billing-summary');
  }
}
