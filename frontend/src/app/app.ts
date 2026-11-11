import { Component, OnInit, OnDestroy, inject, signal, computed, ChangeDetectionStrategy } from '@angular/core';
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
import { StackitResource, BillingSummary, AggregationItem, StorageResourceData, VmDiskResourceData, S3AccessKeyResourceData, PublicIpHistoryRecord, ComputeResourceData, AccessIssuesSummary, AccessIssueRecord, ProjectAccessMatrixRow, AccessStatus, PublicIpResourceData, DnsZoneResourceData, DnsRecordSet, DnsRecordSetResourceData, DatabaseResourceData } from './models/resource.model';

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
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App implements OnInit, OnDestroy {
  private readonly resourceService = inject(ResourceService);

  private readonly objectKeysCache = new WeakMap<object, string[]>();
  private readonly ipHistoryCache = new WeakMap<object, PublicIpHistoryRecord[]>();
  private readonly formattedPolicyCache = new Map<string, string>();

  // Feedback state for Copy Share Link button
  readonly linkCopied = signal<boolean>(false);
  private linkCopiedTimeout: ReturnType<typeof setTimeout> | null = null;

  // Popstate event listener reference for cleanup on destroy
  private readonly popstateListener = () => {
    this.handlePopState();
  };

  // All resources loaded from the backend (capped to maximum 100)
  readonly resources = signal<StackitResource[]>([]);

  // Total matching resource count returned by the backend across the full query
  readonly totalCount = signal<number>(0);

  // Aggregated billing summary loaded from the backend
  readonly billingSummary = signal<BillingSummary[]>([]);

  // Error message to display in the header banner when an error occurs
  readonly errorMessage = signal<string | null>(null);
  private lastErrorSource: 'resources' | 'billing' | 'access' | null = null;

  // Selected tab index
  readonly selectedIndex = signal<number>(0);

  // Search string entered by the user
  readonly searchString = signal<string>('');

  // Active search query (submitted on "Search" button click or press enter)
  readonly activeSearchQuery = signal<string>('');

  // Active facet filters from sidebar aggregation chips
  readonly selectedTypeFilter = signal<string | null>(null);
  readonly selectedProjectFilter = signal<string | null>(null);
  readonly selectedRegionFilter = signal<string | null>(null);
  readonly selectedStatusFilter = signal<string | null>(null);

  readonly hasActiveFacetFilters = computed(() =>
    !!this.selectedTypeFilter() ||
    !!this.selectedProjectFilter() ||
    !!this.selectedRegionFilter() ||
    !!this.selectedStatusFilter()
  );

  // Filtered resources list based on the active search query and selected aggregation facet filters
  readonly filteredResources = computed(() => {
    const all = this.resources();
    const typeFilter = this.selectedTypeFilter();
    const projectFilter = this.selectedProjectFilter();
    const regionFilter = this.selectedRegionFilter();
    const statusFilter = this.selectedStatusFilter();

    if (!typeFilter && !projectFilter && !regionFilter && !statusFilter) {
      return all;
    }

    return all.filter((res) => {
      if (typeFilter) {
        const typeLabel = this.formatTypeLabel(res.type);
        if (typeLabel !== typeFilter && res.type !== typeFilter) {
          return false;
        }
      }
      if (projectFilter) {
        if (res.projectName) {
          if (res.projectName !== projectFilter && res.projectId !== projectFilter) {
            return false;
          }
        } else if (res.projectId !== projectFilter) {
          return false;
        }
      }
      if (regionFilter) {
        const normFilter = this.normalizeRegionCode(regionFilter);
        const normRes = this.normalizeRegionCode(res.region);
        if (normFilter !== normRes && res.region?.toLowerCase() !== regionFilter.toLowerCase()) {
          return false;
        }
      }
      if (statusFilter) {
        const isDeleted = !!res.deletedAt;
        if (statusFilter.toUpperCase() === 'DELETED') {
          if (!isDeleted) return false;
        } else {
          if (isDeleted) return false;
          const resStatus = (res.status || 'UNKNOWN').toUpperCase();
          if (resStatus !== statusFilter.toUpperCase()) {
            return false;
          }
        }
      }
      return true;
    });
  });

  // Exact type aggregations calculated by the backend across the full query dataset
  readonly typeAggregations = signal<AggregationItem[]>([]);

  // Exact region aggregations calculated by the backend across the full query dataset
  readonly regionAggregations = signal<AggregationItem[]>([]);

  // Exact status/state aggregations calculated by the backend across the full query dataset
  readonly statusAggregations = signal<AggregationItem[]>([]);

  // Expanded S3 policy and ACL detail views
  readonly expandedPolicyIds = signal<Set<string>>(new Set());
  // Expanded DNS Zone record set detail views
  readonly expandedDnsZoneIds = signal<Set<string>>(new Set());
  // Exact project aggregations calculated by the backend across the full query dataset
  readonly projectAggregations = signal<AggregationItem[]>([]);

  // Access issues summary loaded from backend
  readonly accessSummary = signal<AccessIssuesSummary | null>(null);

  // Search filter for access issues tab
  readonly accessSearchString = signal<string>('');

  // Filter mode for matrix: 'ALL' or 'ISSUES_ONLY'
  readonly accessFilterMode = signal<'ALL' | 'ISSUES_ONLY'>('ALL');

  // Resource types displayed in the matrix columns
  readonly matrixResourceTypes = [
    { key: 'compute', label: 'Compute' },
    { key: 'storage', label: 'Storage' },
    { key: 'vmdisks', label: 'VM Disks' },
    { key: 'network', label: 'Load Balancers' },
    { key: 'network-vpc', label: 'VPCs' },
    { key: 'iam', label: 'IAM' },
    { key: 'billing', label: 'Billing' },
    { key: 'dns-zone', label: 'DNS Zones' },
    { key: 'database', label: 'Databases' }
  ];

  // Filtered matrix rows based on search query and issues-only mode
  readonly filteredMatrixRows = computed(() => {
    const summary = this.accessSummary();
    if (!summary || !summary.matrix) return [];
    const query = this.accessSearchString().trim().toLowerCase();
    const mode = this.accessFilterMode();

    return summary.matrix.filter((row) => {
      if (mode === 'ISSUES_ONLY' && !row.hasAccessIssues) {
        return false;
      }
      if (!query) {
        return true;
      }
      return (
        (row.projectName && row.projectName.toLowerCase().includes(query)) ||
        (row.projectId && row.projectId.toLowerCase().includes(query))
      );
    });
  });

  // Filtered active access issues based on search query
  readonly filteredAccessIssues = computed(() => {
    const summary = this.accessSummary();
    if (!summary || !summary.issues) return [];
    const query = this.accessSearchString().trim().toLowerCase();
    if (!query) return summary.issues;

    return summary.issues.filter((issue) => {
      return (
        (issue.projectName && issue.projectName.toLowerCase().includes(query)) ||
        (issue.projectId && issue.projectId.toLowerCase().includes(query)) ||
        (issue.resourceType && issue.resourceType.toLowerCase().includes(query)) ||
        (issue.region && issue.region.toLowerCase().includes(query)) ||
        (issue.errorMessage && issue.errorMessage.toLowerCase().includes(query))
      );
    });
  });

  ngOnInit(): void {
    if (typeof window !== 'undefined') {
      if (window.location && window.location.search && window.location.search !== '?') {
        this.applyUrlParameters(window.location.search);
      }
      window.addEventListener('popstate', this.popstateListener);
    }
    const initialQuery = this.activeSearchQuery() || undefined;
    this.loadResources(initialQuery);
    this.loadBillingSummary();
    this.loadAccessIssues();
  }

  ngOnDestroy(): void {
    if (typeof window !== 'undefined') {
      window.removeEventListener('popstate', this.popstateListener);
    }
    if (this.linkCopiedTimeout) {
      clearTimeout(this.linkCopiedTimeout);
      this.linkCopiedTimeout = null;
    }
  }

  /**
   * Resets all search queries, facet filters, tab selection, and access issue filters to defaults.
   */
  resetFiltersToDefaults(): void {
    this.searchString.set('');
    this.activeSearchQuery.set('');
    this.selectedTypeFilter.set(null);
    this.selectedProjectFilter.set(null);
    this.selectedRegionFilter.set(null);
    this.selectedStatusFilter.set(null);
    this.selectedIndex.set(0);
    this.accessSearchString.set('');
    this.accessFilterMode.set('ALL');
  }

  /**
   * Parses query parameters from window.location.search (or an explicit query string)
   * and initializes the reactive filter signals.
   */
  applyUrlParameters(queryString?: string): void {
    if (queryString === undefined && typeof window !== 'undefined' && window.location) {
      queryString = window.location.search;
    }
    const params = new URLSearchParams(queryString || '');

    // Search query: 'q'
    const q = params.get('q');
    if (q !== null && q.trim() !== '') {
      this.searchString.set(q);
      this.activeSearchQuery.set(q);
    } else {
      this.searchString.set('');
      this.activeSearchQuery.set('');
    }

    // Facet filters: 'type', 'project', 'region', 'status'
    this.selectedTypeFilter.set(params.get('type') || null);
    this.selectedProjectFilter.set(params.get('project') || null);
    this.selectedRegionFilter.set(params.get('region') || null);
    this.selectedStatusFilter.set(params.get('status') || null);

    // Tab navigation: 'tab' ('billing' -> 1, 'access' -> 2, otherwise 0)
    const tabParam = params.get('tab')?.toLowerCase();
    if (tabParam === 'billing' || tabParam === '1') {
      this.selectedIndex.set(1);
    } else if (tabParam === 'access' || tabParam === '2') {
      this.selectedIndex.set(2);
    } else {
      this.selectedIndex.set(0);
    }

    // Access Issues filters: 'aq', 'amode'
    this.accessSearchString.set(params.get('aq') || '');
    const amodeParam = params.get('amode')?.toUpperCase();
    this.accessFilterMode.set(amodeParam === 'ISSUES_ONLY' ? 'ISSUES_ONLY' : 'ALL');
  }

  /**
   * Synchronizes current search, facet filter, tab, and access issue states to the browser URL query string.
   * If replace is false, creates a new browser history entry; if true, replaces current history entry.
   */
  syncUrlToState(replace: boolean = false): void {
    if (typeof window === 'undefined' || !window.history || !window.location) return;

    try {
      const params = new URLSearchParams();

      const query = this.activeSearchQuery().trim();
      if (query) {
        params.set('q', query);
      }

      const typeFilter = this.selectedTypeFilter();
      if (typeFilter) {
        params.set('type', typeFilter);
      }

      const projectFilter = this.selectedProjectFilter();
      if (projectFilter) {
        params.set('project', projectFilter);
      }

      const regionFilter = this.selectedRegionFilter();
      if (regionFilter) {
        params.set('region', regionFilter);
      }

      const statusFilter = this.selectedStatusFilter();
      if (statusFilter) {
        params.set('status', statusFilter);
      }

      const tab = this.selectedIndex();
      if (tab === 1) {
        params.set('tab', 'billing');
      } else if (tab === 2) {
        params.set('tab', 'access');
      }

      const aq = this.accessSearchString().trim();
      if (aq) {
        params.set('aq', aq);
      }

      const amode = this.accessFilterMode();
      if (amode === 'ISSUES_ONLY') {
        params.set('amode', 'ISSUES_ONLY');
      }

      const qs = params.toString();
      const newUrl = qs ? `${window.location.pathname}?${qs}` : window.location.pathname;
      const currentUrl = `${window.location.pathname}${window.location.search}`;

      if (newUrl !== currentUrl) {
        if (replace) {
          window.history.replaceState(null, '', newUrl);
        } else {
          window.history.pushState(null, '', newUrl);
        }
      }
    } catch {
      // Gracefully ignore restrictions in restrictive iframe/embedded test environments
    }
  }

  /**
   * Handles browser back/forward popstate events to restore filters and re-run search if needed.
   */
  private handlePopState(): void {
    if (typeof window === 'undefined' || !window.location) return;
    const prevQuery = this.activeSearchQuery();
    if (window.location.search && window.location.search !== '?') {
      this.applyUrlParameters(window.location.search);
    } else {
      this.resetFiltersToDefaults();
    }
    if (prevQuery !== this.activeSearchQuery()) {
      this.loadResources(this.activeSearchQuery() || undefined);
    }
  }

  /**
   * Switches the active tab and synchronizes the tab state to the URL.
   */
  onTabChange(index: number): void {
    this.selectedIndex.set(index);
    this.syncUrlToState();
  }

  /**
   * Copies the current deep link URL (including active search & filters) to the clipboard
   * and triggers visual button confirmation feedback.
   */
  copyShareLink(): void {
    if (typeof window === 'undefined') return;
    this.syncUrlToState(true);
    const url = window.location.href;

    this.linkCopied.set(true);
    if (this.linkCopiedTimeout) {
      clearTimeout(this.linkCopiedTimeout);
    }
    this.linkCopiedTimeout = setTimeout(() => {
      this.linkCopied.set(false);
    }, 2000);

    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(url).catch(() => {});
    }
  }

  loadResources(query?: string): void {
    this.resourceService.getResources(query).subscribe({
      next: (data) => {
        this.resources.set(data?.resources || []);
        this.totalCount.set(data?.totalCount ?? (data?.resources?.length || 0));
        this.typeAggregations.set(data?.typeAggregations || data?.aggregations || []);
        this.regionAggregations.set(data?.regionAggregations || []);
        this.statusAggregations.set(data?.statusAggregations || []);
        this.projectAggregations.set(data?.projectAggregations || []);
        if (this.lastErrorSource === 'resources') {
          this.errorMessage.set(null);
          this.lastErrorSource = null;
        }
      },
      error: (err) => {
        console.error('Failed to load resources', err);
        this.projectAggregations.set([]);
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

  loadAccessIssues(): void {
    this.resourceService.getAccessIssues().subscribe({
      next: (data) => {
        this.accessSummary.set(data);
        if (this.lastErrorSource === 'access') {
          this.errorMessage.set(null);
          this.lastErrorSource = null;
        }
      },
      error: (err) => {
        console.error('Failed to load access issues', err);
        this.lastErrorSource = 'access';
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
    this.syncUrlToState();
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

  filterPublicBuckets(): void {
    if (this.searchString() === 'is-public: true') {
      this.searchString.set('');
    } else {
      this.searchString.set('is-public: true');
    }
    this.onSearch();
  }

  filterUnattachedDisks(): void {
    if (this.searchString().trim().toLowerCase() === 'unattached vmdisks') {
      this.searchString.set('');
    } else {
      this.searchString.set('unattached vmdisks');
    }
    this.onSearch();
  }

  filterUnattachedIps(): void {
    if (this.searchString() === 'unattached public-ip') {
      this.searchString.set('');
    } else {
      this.searchString.set('unattached public-ip');
    }
    this.onSearch();
  }

  filterS3Keys(): void {
    if (this.searchString() === 'S3 Access Key') {
      this.searchString.set('');
    } else {
      this.searchString.set('S3 Access Key');
    }
    this.onSearch();
  }

  filterDnsZones(): void {
    if (this.searchString() === 'dns-zone' || this.searchString() === 'DNS Zones') {
      this.searchString.set('');
    } else {
      this.searchString.set('dns-zone');
    }
    this.onSearch();
  }

  filterDatabases(): void {
    if (this.searchString() === 'database' || this.searchString() === 'Databases') {
      this.searchString.set('');
    } else {
      this.searchString.set('database');
    }
    this.onSearch();
  }

  toggleTypeFilter(key?: string): void {
    if (!key) return;
    this.selectedTypeFilter.update((curr) => (curr === key ? null : key));
    this.syncUrlToState();
  }

  toggleProjectFilter(key?: string): void {
    if (!key) return;
    this.selectedProjectFilter.update((curr) => (curr === key ? null : key));
    this.syncUrlToState();
  }

  toggleRegionFilter(key?: string): void {
    if (!key) return;
    this.selectedRegionFilter.update((curr) => (curr === key ? null : key));
    this.syncUrlToState();
  }

  toggleStatusFilter(key?: string): void {
    if (!key) return;
    this.selectedStatusFilter.update((curr) => (curr === key ? null : key));
    this.syncUrlToState();
  }

  clearAllFacetFilters(): void {
    this.selectedTypeFilter.set(null);
    this.selectedProjectFilter.set(null);
    this.selectedRegionFilter.set(null);
    this.selectedStatusFilter.set(null);
    this.syncUrlToState();
  }

  isDatabase(res: StackitResource): boolean {
    if (!res) return false;
    return res.type === 'database' || res.type === 'databases' || res.type === 'db' || res.type === 'dbs';
  }

  getDatabaseData(res: StackitResource): DatabaseResourceData | undefined {
    return res.data as DatabaseResourceData | undefined;
  }

  formatDatabaseEngine(engine?: string): string {
    if (!engine) return 'Database';
    const norm = engine.toLowerCase();
    if (norm === 'postgresql' || norm === 'postgres') return 'PostgreSQL';
    if (norm === 'mongodb' || norm === 'mongo') return 'MongoDB';
    if (norm === 'opensearch') return 'OpenSearch';
    if (norm === 'redis') return 'Redis';
    if (norm === 'valkey' || norm.includes('valkey') || norm.includes('key-value') || norm.includes('keyvalue')) return 'Key-Value Store (Valkey)';
    if (norm === 'mariadb') return 'MariaDB';
    if (norm === 'sqlserver') return 'SQL Server';
    return engine.charAt(0).toUpperCase() + engine.slice(1);
  }

  getDatabaseEngineIcon(engine?: string): string {
    if (!engine) return 'storage';
    const norm = engine.toLowerCase();
    if (norm.includes('postgres') || norm.includes('sql')) return 'dataset';
    if (norm.includes('mongo')) return 'folder_special';
    if (norm.includes('redis') || norm.includes('valkey') || norm.includes('key-value') || norm.includes('keyvalue')) return 'bolt';
    if (norm.includes('search')) return 'search';
    return 'storage';
  }

  isDnsZone(res: StackitResource): boolean {
    if (!res) return false;
    return res.type === 'dns-zone' || res.type === 'dns' || res.type === 'dnszone' || res.type === 'dnszones';
  }

  getDnsZoneData(res: StackitResource): DnsZoneResourceData | undefined {
    return res.data as DnsZoneResourceData | undefined;
  }

  hasDnsRecordSets(res: StackitResource): boolean {
    const data = this.getDnsZoneData(res);
    return Array.isArray(data?.recordSets) && data!.recordSets.length > 0;
  }

  getDnsRecordSets(res: StackitResource): DnsRecordSet[] {
    const data = this.getDnsZoneData(res);
    return data?.recordSets || [];
  }

  toggleDnsRecordSets(id: string): void {
    this.expandedDnsZoneIds.update((set) => {
      const next = new Set(set);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  isDnsZoneExpanded(id: string): boolean {
    return this.expandedDnsZoneIds().has(id);
  }

  formatRecordTargets(rs?: { records?: string[] } | null): string {
    if (!rs || !rs.records || rs.records.length === 0) {
      return '-';
    }
    return rs.records.join(', ');
  }

  isDnsRecordSet(res: StackitResource): boolean {
    if (!res) return false;
    return res.type === 'dns-record-set' || res.type === 'dns-record' || res.type === 'dnsrecord' || res.type === 'dnsrecordset' || res.type === 'dnsrecordsets';
  }

  getDnsRecordSetData(res: StackitResource): DnsRecordSetResourceData | undefined {
    return res.data as DnsRecordSetResourceData | undefined;
  }

  isPublicIp(res: StackitResource): boolean {
    if (!res) return false;
    return res.type === 'public-ip' || res.type === 'publicip' || res.type === 'publicips';
  }

  isPublicIpAttached(res: StackitResource): boolean {
    if (!this.isPublicIp(res)) return false;
    return res.data?.['attached'] === true || !!res.data?.['serverId'] || res.status === 'ATTACHED' || res.tags?.['attached'] === 'true';
  }

  isPublicIpUnattached(res: StackitResource): boolean {
    if (!this.isPublicIp(res)) return false;
    return res.data?.['attached'] === false || (!res.data?.['serverId'] && (res.status === 'UNATTACHED' || res.status === 'AVAILABLE')) || res.tags?.['attached'] === 'false';
  }

  getPublicIpData(res: StackitResource): PublicIpResourceData | undefined {
    return res.data as PublicIpResourceData | undefined;
  }

  isS3AccessKey(res: StackitResource): boolean {
    if (!res) return false;
    return (
      (res.type === 'iam' && res.data?.['identityType'] === 'S3 Access Key') ||
      res.data?.['authScheme'] === 'S3 HMAC Key' ||
      res.tags?.['identity-type'] === 's3-access-key'
    );
  }

  isS3AccessKeyExpired(res: StackitResource): boolean {
    if (!this.isS3AccessKey(res)) return false;
    return (
      res.status === 'EXPIRED' ||
      res.status === 'expired' ||
      res.data?.['expired'] === true ||
      res.tags?.['expired'] === 'true'
    );
  }

  getS3AccessKeyData(res: StackitResource): S3AccessKeyResourceData | undefined {
    return res.data as S3AccessKeyResourceData | undefined;
  }

  isDiskAttached(res: StackitResource): boolean {
    if (!res || (res.type !== 'vmdisks' && res.type !== 'volume' && res.type !== 'disk')) return false;
    return res.data?.['attached'] === true || !!res.data?.['serverId'];
  }

  isDiskUnattached(res: StackitResource): boolean {
    if (!res || (res.type !== 'vmdisks' && res.type !== 'volume' && res.type !== 'disk')) return false;
    return res.data?.['attached'] === false || (!res.data?.['serverId'] && (res.status === 'AVAILABLE' || res.status === 'available'));
  }

  getVmDiskData(res: StackitResource): VmDiskResourceData | undefined {
    return res.data as VmDiskResourceData | undefined;
  }

  hasPublicIpHistory(res: StackitResource): boolean {
    if (!res || !res.data) return false;
    const history = res.data['publicIpHistory'];
    return Array.isArray(history) && history.length > 0;
  }

  getPublicIpHistory(res: StackitResource): PublicIpHistoryRecord[] {
    if (!this.hasPublicIpHistory(res) || !res.data) return [];
    let sorted = this.ipHistoryCache.get(res.data);
    if (!sorted) {
      const list = (res.data['publicIpHistory'] as PublicIpHistoryRecord[]).slice();
      sorted = list.sort((a, b) => {
        if (a.active && !b.active) return -1;
        if (!a.active && b.active) return 1;
        return (b.lastSeen || '').localeCompare(a.lastSeen || '');
      });
      this.ipHistoryCache.set(res.data, sorted);
    }
    return sorted;
  }

  formatIpDate(dateStr: string | null | undefined): string {
    if (!dateStr) return '';
    try {
      const d = new Date(dateStr);
      if (isNaN(d.getTime())) return dateStr;
      return d.toISOString().replace('T', ' ').substring(0, 16) + ' UTC';
    } catch {
      return String(dateStr);
    }
  }

  togglePolicyDetails(id: string): void {
    this.expandedPolicyIds.update((set) => {
      const next = new Set(set);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  isPolicyExpanded(id: string): boolean {
    return this.expandedPolicyIds().has(id);
  }

  formatPolicyJson(policy: string | null | undefined): string {
    if (!policy) return '';
    const cached = this.formattedPolicyCache.get(policy);
    if (cached !== undefined) return cached;
    try {
      const parsed = typeof policy === 'string' ? JSON.parse(policy) : policy;
      const formatted = JSON.stringify(parsed, null, 2);
      this.formattedPolicyCache.set(policy, formatted);
      return formatted;
    } catch {
      const fallback = String(policy);
      this.formattedPolicyCache.set(policy, fallback);
      return fallback;
    }
  }

  hasStorageSecurityDetails(res: StackitResource): boolean {
    if (!res || !res.data) return false;
    return !!(res.data['bucketPolicy'] || res.data['acl'] || res.data['securityFindings'] || res.data['retention']);
  }

  getStorageData(res: StackitResource): StorageResourceData | undefined {
    return res.data as StorageResourceData | undefined;
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
        this.syncUrlToState();
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
      case 'public-ip':
      case 'publicip':
      case 'publicips':
        return 'Public IPs';
      case 'dns-zone':
      case 'dns':
      case 'dnszone':
      case 'dnszones':
        return 'DNS Zones';
      case 'dns-record-set':
      case 'dns-record':
      case 'dnsrecord':
      case 'dnsrecordset':
      case 'dnsrecordsets':
        return 'DNS Record Sets';
      case 'database':
      case 'databases':
      case 'db':
      case 'dbs':
        return 'Databases';
      default:
        return type.charAt(0).toUpperCase() + type.slice(1) + 's';
    }
  }

  formatRegionLabel(region?: string): string {
    if (!region) return 'global';
    const norm = region.toLowerCase().trim();
    if (norm === 'eu01' || norm === 'eu-01' || norm === 'eu-central-1' || norm.startsWith('eu01-') || norm.includes('germany')) {
      return 'eu01 (Germany)';
    }
    if (norm === 'eu02' || norm === 'eu-02' || norm.startsWith('eu02-') || norm.includes('austria')) {
      return 'eu02 (Austria)';
    }
    if (norm === 'global') return 'global';
    return region;
  }

  normalizeRegionCode(region?: string): string {
    if (!region) return 'global';
    const norm = region.toLowerCase().trim();
    if (norm === 'eu01' || norm === 'eu-01' || norm === 'eu-central-1' || norm.startsWith('eu01-') || norm.includes('germany')) {
      return 'eu01';
    }
    if (norm === 'eu02' || norm === 'eu-02' || norm.startsWith('eu02-') || norm.includes('austria')) {
      return 'eu02';
    }
    if (norm === 'global') return 'global';
    return norm;
  }

  getObjectKeys(obj: any): string[] {
    if (!obj || typeof obj !== 'object') return [];
    let keys = this.objectKeysCache.get(obj);
    if (!keys) {
      keys = Object.keys(obj);
      this.objectKeysCache.set(obj, keys);
    }
    return keys;
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
      const entries = Object.entries(val);
      if (entries.length === 0) return 'none';
      const isFlat = entries.every(([, v]) => v === null || typeof v !== 'object');
      if (isFlat) {
        return entries.map(([k, v]) => `${k}=${v}`).join(', ');
      }
      return JSON.stringify(val);
    }
    return String(val);
  }

  getMatrixStatus(row: ProjectAccessMatrixRow, resourceType: string): AccessStatus {
    return row.statuses?.[resourceType] || 'NOT_CHECKED';
  }

  toggleAccessFilterMode(): void {
    this.accessFilterMode.update((mode) => (mode === 'ALL' ? 'ISSUES_ONLY' : 'ALL'));
    this.syncUrlToState();
  }

  formatAccessResourceType(type: string): string {
    const match = this.matrixResourceTypes.find((r) => r.key === type);
    return match ? match.label : type;
  }

  onAccessSearchChange(value: string): void {
    this.accessSearchString.set(value);
    this.syncUrlToState(true);
  }
}
