import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { HttpErrorResponse } from '@angular/common/http';
import { ResourceService } from './services/resource.service';
import { StackitResource, BillingSummary, AggregationItem } from './models/resource.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatToolbarModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatTabsModule
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private readonly resourceService = inject(ResourceService);

  // All resources loaded from the backend (capped to maximum 100)
  readonly resources = signal<StackitResource[]>([]);

  // Total matching resource count returned by the backend across the full query
  readonly totalCount = signal<number>(0);

  // Aggregated billing summary loaded from the backend
  readonly billingSummary = signal<BillingSummary[]>([]);

  // Error message to display in the header banner when an error occurs
  readonly errorMessage = signal<string | null>(null);
  private lastErrorSource: 'resources' | 'billing' | null = null;

  // Selected tab index
  readonly selectedIndex = signal<number>(0);

  // Search string entered by the user
  readonly searchString = signal<string>('');

  // Active search query (submitted on "Search" button click or press enter)
  readonly activeSearchQuery = signal<string>('');

  // Filtered resources list based on the active search query
  readonly filteredResources = computed(() => this.resources());

  // Exact type aggregations calculated by the backend across the full query dataset
  readonly typeAggregations = signal<AggregationItem[]>([]);

  // Exact region aggregations calculated by the backend across the full query dataset
  readonly regionAggregations = signal<AggregationItem[]>([]);

  // Exact status/state aggregations calculated by the backend across the full query dataset
  readonly statusAggregations = signal<AggregationItem[]>([]);

  ngOnInit(): void {
    this.loadResources();
    this.loadBillingSummary();
  }

  loadResources(query?: string): void {
    this.resourceService.getResources(query).subscribe({
      next: (data) => {
        this.resources.set(data?.resources || []);
        this.totalCount.set(data?.totalCount ?? (data?.resources?.length || 0));
        this.typeAggregations.set(data?.typeAggregations || data?.aggregations || []);
        this.regionAggregations.set(data?.regionAggregations || []);
        this.statusAggregations.set(data?.statusAggregations || []);
        if (this.lastErrorSource === 'resources') {
          this.errorMessage.set(null);
          this.lastErrorSource = null;
        }
      },
      error: (err) => {
        console.error('Failed to load resources', err);
        this.lastErrorSource = 'resources';
        this.errorMessage.set(this.extractErrorMessage(err));
      }
    });
  }

  loadBillingSummary(): void {
    this.resourceService.getBillingSummary().subscribe({
      next: (data) => {
        const sorted = (data || []).slice().sort((a, b) => {
          const aIsOrg = a.type?.toLowerCase() === 'organization';
          const bIsOrg = b.type?.toLowerCase() === 'organization';
          if (aIsOrg && !bIsOrg) return -1;
          if (!aIsOrg && bIsOrg) return 1;
          const aAmt = a.amount ?? 0;
          const bAmt = b.amount ?? 0;
          if (bAmt !== aAmt) {
            return bAmt - aAmt; // descending
          }
          return (a.name || '').localeCompare(b.name || '');
        });
        this.billingSummary.set(sorted);
        if (this.lastErrorSource === 'billing') {
          this.errorMessage.set(null);
          this.lastErrorSource = null;
        }
      },
      error: (err) => {
        console.error('Failed to load billing summary', err);
        this.lastErrorSource = 'billing';
        this.errorMessage.set(this.extractErrorMessage(err));
      }
    });
  }

  clearError(): void {
    this.errorMessage.set(null);
    this.lastErrorSource = null;
  }

  extractErrorMessage(err: unknown): string {
    if (!err) {
      return 'An unexpected error occurred.';
    }

    if (err instanceof HttpErrorResponse) {
      if (err.status === 0) {
        return 'Backend service is not reachable. Please verify the backend is running.';
      }
      if (err.status === 504 || err.status === 502) {
        return 'Backend service is not reachable (gateway error). Please verify the backend is running.';
      }
      if (err.error) {
        if (typeof err.error === 'string' && err.error.trim().length > 0) {
          return err.error;
        }
        if (typeof err.error === 'object') {
          if (err.error.error && typeof err.error.error === 'string') {
            return err.error.error;
          }
          if (err.error.message && typeof err.error.message === 'string') {
            return err.error.message;
          }
          if (err.error.detail && typeof err.error.detail === 'string') {
            return err.error.detail;
          }
        }
      }
      if (err.status && err.statusText) {
        return `Backend error (${err.status}): ${err.statusText}`;
      }
      if (err.status) {
        return `Backend error (${err.status})`;
      }
      if (err.message) {
        return err.message;
      }
    }

    const genericErr = err as { status?: number; statusText?: string; error?: any; message?: string };
    if (genericErr.status === 0) {
      return 'Backend service is not reachable. Please verify the backend is running.';
    }
    if (genericErr.status === 504 || genericErr.status === 502) {
      return 'Backend service is not reachable (gateway error). Please verify the backend is running.';
    }
    if (genericErr.error) {
      if (typeof genericErr.error === 'string' && genericErr.error.trim().length > 0) {
        return genericErr.error;
      }
      if (typeof genericErr.error === 'object') {
        if (genericErr.error.error && typeof genericErr.error.error === 'string') {
          return genericErr.error.error;
        }
        if (genericErr.error.message && typeof genericErr.error.message === 'string') {
          return genericErr.error.message;
        }
        if (genericErr.error.detail && typeof genericErr.error.detail === 'string') {
          return genericErr.error.detail;
        }
      }
    }
    if (genericErr.status && genericErr.statusText) {
      return `Backend error (${genericErr.status}): ${genericErr.statusText}`;
    }
    if (genericErr.status) {
      return `Backend error (${genericErr.status})`;
    }
    if (genericErr.message && typeof genericErr.message === 'string') {
      return genericErr.message;
    }

    return 'An unexpected error occurred while communicating with the backend.';
  }

  onSearch(): void {
    const query = this.searchString().trim();
    this.activeSearchQuery.set(query);
    this.loadResources(query);
  }

  filterTokenFlow(): void {
    if (this.searchString() === 'Token Flow' || this.searchString() === 'Token Flow (Deprecated)') {
      this.searchString.set('');
    } else {
      this.searchString.set('Token Flow');
    }
    this.onSearch();
  }

  filterKeyFlow(): void {
    if (this.searchString() === 'Key Flow') {
      this.searchString.set('');
    } else {
      this.searchString.set('Key Flow');
    }
    this.onSearch();
  }

  isTokenFlowDeprecated(res: StackitResource): boolean {
    if (!res || !res.data) return false;
    const deprecated = res.data['deprecated'];
    const authScheme = res.data['authScheme'];
    const authFlow = res.data['authFlow'];
    return deprecated === true ||
      (typeof authScheme === 'string' && authScheme.includes('Deprecated')) ||
      (typeof authFlow === 'string' && authFlow.includes('Deprecated'));
  }

  onSearchStringChange(value: string): void {
    this.searchString.set(value);
    if (!value || value.trim() === '') {
      if (this.activeSearchQuery()) {
        this.activeSearchQuery.set('');
        this.loadResources();
      }
    }
  }

  formatTypeLabel(type: string): string {
    if (!type) return 'Unknown';
    switch (type.toLowerCase()) {
      case 'compute':
      case 'vm':
        return 'VMs';
      case 'storage':
        return 'Buckets';
      case 'vmdisks':
      case 'disk':
      case 'volume':
        return 'VM Disks';
      case 'network-vpc':
        return 'VPCs';
      case 'network':
        return 'Load Balancers';
      case 'billing':
      case 'billing-org':
        return 'Invoices';
      case 'iam':
        return 'IAM Policies';
      default:
        return type.charAt(0).toUpperCase() + type.slice(1) + 's';
    }
  }

  getObjectKeys(obj: any): string[] {
    if (!obj) return [];
    return Object.keys(obj);
  }

  isObject(val: any): boolean {
    return val !== null && typeof val === 'object';
  }

  formatMetaValue(val: any): string {
    if (val === null || val === undefined) {
      return '';
    }
    if (Array.isArray(val)) {
      if (val.length === 0) return 'none';
      if (typeof val[0] === 'object') {
        return JSON.stringify(val);
      }
      return val.join(', ');
    }
    if (typeof val === 'object') {
      return JSON.stringify(val);
    }
    return String(val);
  }
}
