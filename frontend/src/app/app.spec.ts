import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { ResourceService } from './services/resource.service';
import { of, throwError } from 'rxjs';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { StackitResource } from './models/resource.model';
import { vi } from 'vitest';

describe('App', () => {
  let mockResourceService: any;
  const mockResources: StackitResource[] = [
    {
      id: '11111111-1111-1111-1111-111111111111',
      resourceId: 'vm-1',
      name: 'prod-vm-1',
      type: 'compute',
      status: 'active',
      region: 'eu-central-1',
      projectId: 'proj-1',
      tags: { env: 'prod' },
      data: {}
    },
    {
      id: '22222222-2222-2222-2222-222222222222',
      resourceId: 'invoice-1',
      name: 'invoice-2026-08',
      type: 'billing',
      status: 'paid',
      region: 'eu-central-1',
      projectId: 'proj-1',
      data: { amount: 150.50, currency: 'EUR' }
    }
  ];

  const mockSearchResult = {
    resources: mockResources,
    totalCount: 2,
    typeAggregations: [
      { key: 'VMs', count: 1 },
      { key: 'Invoices', count: 1 }
    ],
    regionAggregations: [
      { key: 'eu-central-1', count: 2 }
    ],
    statusAggregations: [
      { key: 'ACTIVE', count: 1 },
      { key: 'PAID', count: 1 }
    ],
    aggregations: [
      { key: 'VMs', count: 1 },
      { key: 'Invoices', count: 1 }
    ]
  };

  beforeEach(async () => {
    mockResourceService = {
      getResources: vi.fn().mockReturnValue(of(mockSearchResult)),
      getBillingSummary: vi.fn().mockReturnValue(of([
        { id: 'proj-abc', name: 'proj-abc-name', type: 'Project', amount: 200.0, currency: 'EUR' },
        { id: 'org-123', name: 'Organization', type: 'Organization', amount: 1000.0, currency: 'EUR' }
      ]))
    };

    await TestBed.configureTestingModule({
      imports: [App, NoopAnimationsModule],
      providers: [
        { provide: ResourceService, useValue: mockResourceService },
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should load resources from service on init', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(mockResourceService.getResources).toHaveBeenCalled();
    expect(fixture.componentInstance.resources().length).toBe(2);
  });

  it('should render the header banner', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const header = compiled.querySelector('.header-title');
    expect(header?.textContent).toContain('StackIT Resource Explorer');
  });

  it('should render "by landvoigt-it.com" link right before the cloud icon in the header', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const link = compiled.querySelector('.header-by-link') as HTMLAnchorElement;
    expect(link).toBeTruthy();
    expect(link.textContent?.trim()).toBe('by landvoigt-it.com');
    expect(link.getAttribute('href')).toBe('https://www.landvoigt-it.com');
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toContain('noopener');

    const header = compiled.querySelector('.header');
    const headerChildren = Array.from(header?.children || []);
    const linkIndex = headerChildren.indexOf(link);
    const cloudIcon = header?.querySelector('mat-icon');
    const cloudIconIndex = headerChildren.indexOf(cloudIcon!);
    expect(linkIndex).toBeGreaterThan(-1);
    expect(cloudIconIndex).toBe(linkIndex + 1);
  });

  it('should render search input and button', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const searchInput = compiled.querySelector('.search-input');
    const searchButton = compiled.querySelector('.search-button');
    expect(searchInput).toBeTruthy();
    expect(searchButton).toBeTruthy();
  });

  it('should render the two-column dashboard layout', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const leftColumn = compiled.querySelector('.left-column');
    const rightColumn = compiled.querySelector('.right-column');
    expect(leftColumn).toBeTruthy();
    expect(rightColumn).toBeTruthy();
  });

  it('should render resource detailed properties in the right column list', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const resourceCards = compiled.querySelectorAll('.resource-card');
    expect(resourceCards.length).toBe(2);

    const firstCardText = resourceCards[0].textContent;
    expect(firstCardText).toContain('prod-vm-1');
    expect(firstCardText).toContain('eu-central-1');
    expect(firstCardText).toContain('proj-1');
  });

  it('should render billing details for billing resource types', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const billingHighlight = compiled.querySelector('.billing-highlight');
    expect(billingHighlight).toBeTruthy();
    expect(billingHighlight?.textContent).toContain('150.5 EUR');
  });

  it('should display stacked summary aggregations grouped by type, region, and state', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const app = fixture.componentInstance;
    
    expect(app.typeAggregations().length).toBe(2);
    expect(app.typeAggregations()).toContainEqual({ key: 'VMs', count: 1 });
    expect(app.typeAggregations()).toContainEqual({ key: 'Invoices', count: 1 });

    expect(app.regionAggregations().length).toBe(1);
    expect(app.regionAggregations()).toContainEqual({ key: 'eu-central-1', count: 2 });

    expect(app.statusAggregations().length).toBe(2);
    expect(app.statusAggregations()).toContainEqual({ key: 'ACTIVE', count: 1 });
    expect(app.statusAggregations()).toContainEqual({ key: 'PAID', count: 1 });

    const compiled = fixture.nativeElement as HTMLElement;
    const sectionTitles = compiled.querySelectorAll('.agg-section-title');
    expect(sectionTitles.length).toBe(3);
    expect(sectionTitles[0].textContent).toContain('By Resource Type');
    expect(sectionTitles[1].textContent).toContain('By Region');
    expect(sectionTitles[2].textContent).toContain('By State');

    const aggItems = compiled.querySelectorAll('.agg-item');
    expect(aggItems.length).toBe(5);
  });

  it('should query backend resources when Search is executed', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const app = fixture.componentInstance;

    mockResourceService.getResources.mockClear();
    app.searchString.set('invoice');
    app.onSearch();
    fixture.detectChanges();
    expect(mockResourceService.getResources).toHaveBeenCalledWith('invoice');
  });

  it('should reload all resources when search input is cleared after a search', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const app = fixture.componentInstance;

    app.activeSearchQuery.set('previous');
    mockResourceService.getResources.mockClear();
    app.onSearchStringChange('');
    fixture.detectChanges();
    expect(mockResourceService.getResources).toHaveBeenCalledWith(undefined);
  });

  it('should render mat-tab-group with two tabs', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const tabGroup = compiled.querySelector('mat-tab-group');
    expect(tabGroup).toBeTruthy();

    const tabs = compiled.querySelectorAll('.mdc-tab');
    expect(tabs.length).toBe(2);
    expect(tabs[0].textContent).toContain('Resource Explorer');
    expect(tabs[1].textContent).toContain('Billing Summary');
  });

  it('should render the billing summary table with aggregated data', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const app = fixture.componentInstance;
    
    // Check initialized call
    expect(mockResourceService.getBillingSummary).toHaveBeenCalled();

    // Verify billing summary is set in the component signal/property
    expect(app.billingSummary().length).toBe(2);

    // Switch active tab to 1 (Billing Summary) to trigger lazy rendering of tab content
    app.selectedIndex.set(1);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const table = compiled.querySelector('.billing-table');
    expect(table).toBeTruthy();

    const rows = compiled.querySelectorAll('.billing-table-row');
    expect(rows.length).toBe(2);
    // Row 0 must be Organization
    expect(rows[0].textContent).toContain('Organization');
    expect(rows[0].textContent).toContain('1,000.00 EUR');
    expect(rows[0].classList).toContain('org-row');
    // Row 1 must be Project
    expect(rows[1].textContent).toContain('proj-abc-name');
    expect(rows[1].textContent).toContain('Project');
    expect(rows[1].textContent).toContain('200.00 EUR');
  });

  it('should sort Organization in first row and projects ordered by costs descending', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;

    mockResourceService.getBillingSummary.mockReturnValue(of([
      { id: 'proj-low', name: 'low-cost-proj', type: 'Project', amount: 50.0, currency: 'EUR' },
      { id: 'proj-high', name: 'high-cost-proj', type: 'Project', amount: 800.0, currency: 'EUR' },
      { id: 'org-1', name: 'Organization', type: 'Organization', amount: 1250.0, currency: 'EUR' },
      { id: 'proj-mid', name: 'mid-cost-proj', type: 'Project', amount: 350.0, currency: 'EUR' }
    ]));

    app.loadBillingSummary();
    app.selectedIndex.set(1);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const rows = compiled.querySelectorAll('.billing-table-row');
    expect(rows.length).toBe(4);

    // Row 0: Organization
    expect(rows[0].textContent).toContain('Organization');
    expect(rows[0].textContent).toContain('1,250.00 EUR');
    expect(rows[0].classList).toContain('org-row');

    // Row 1: Highest cost project (800 EUR)
    expect(rows[1].textContent).toContain('high-cost-proj');
    expect(rows[1].textContent).toContain('800.00 EUR');

    // Row 2: Medium cost project (350 EUR)
    expect(rows[2].textContent).toContain('mid-cost-proj');
    expect(rows[2].textContent).toContain('350.00 EUR');

    // Row 3: Lowest cost project (50 EUR)
    expect(rows[3].textContent).toContain('low-cost-proj');
    expect(rows[3].textContent).toContain('50.00 EUR');
  });

  describe('Header Error Message Display', () => {
    it('should not display error banner when no errors occur', () => {
      const fixture = TestBed.createComponent(App);
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      const errorBanner = compiled.querySelector('.header-error-banner');
      expect(errorBanner).toBeNull();
    });

    it('should display error banner below the "StackIT Resource Explorer" header when backend is not reachable (status 0)', () => {
      mockResourceService.getResources.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' }))
      );

      const fixture = TestBed.createComponent(App);
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const header = compiled.querySelector('.header');
      expect(header).toBeTruthy();

      const errorBanner = compiled.querySelector('.header-error-banner');
      expect(errorBanner).toBeTruthy();
      expect(errorBanner?.textContent).toContain('Backend service is not reachable');

      // Verify it is positioned below the "StackIT Resource Explorer" header in the DOM
      const appContainer = compiled.querySelector('.app-container');
      const containerChildren = Array.from(appContainer?.children || []);
      const headerIndex = containerChildren.findIndex((el) => el.classList.contains('header'));
      const bannerIndex = containerChildren.findIndex((el) => el.classList.contains('header-error-banner'));
      expect(headerIndex).toBeGreaterThanOrEqual(0);
      expect(bannerIndex).toBeGreaterThan(headerIndex);
    });

    it('should display error message returned from backend (e.g. status 400 Bad Request)', () => {
      mockResourceService.getResources.mockReturnValue(
        throwError(() => new HttpErrorResponse({
          status: 400,
          statusText: 'Bad Request',
          error: { error: 'Invalid search query syntax' }
        }))
      );

      const fixture = TestBed.createComponent(App);
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const errorText = compiled.querySelector('.header-error-text');
      expect(errorText?.textContent).toContain('Invalid search query syntax');
    });

    it('should display error banner when loadBillingSummary fails', () => {
      mockResourceService.getBillingSummary.mockReturnValue(
        throwError(() => new HttpErrorResponse({
          status: 500,
          statusText: 'Internal Server Error',
          error: { message: 'Database connection failed' }
        }))
      );

      const fixture = TestBed.createComponent(App);
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const errorBanner = compiled.querySelector('.header-error-banner');
      expect(errorBanner).toBeTruthy();
      expect(errorBanner?.textContent).toContain('Database connection failed');
    });

    it('should dismiss error banner when close button is clicked', () => {
      const fixture = TestBed.createComponent(App);
      const app = fixture.componentInstance;
      app.errorMessage.set('Temporary backend error');
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      let errorBanner = compiled.querySelector('.header-error-banner');
      expect(errorBanner).toBeTruthy();

      const closeButton = compiled.querySelector('.header-error-close') as HTMLButtonElement;
      expect(closeButton).toBeTruthy();
      closeButton.click();
      fixture.detectChanges();

      errorBanner = compiled.querySelector('.header-error-banner');
      expect(errorBanner).toBeNull();
      expect(app.errorMessage()).toBeNull();
    });

    it('should dismiss error banner when clearError method is called', () => {
      const fixture = TestBed.createComponent(App);
      const app = fixture.componentInstance;
      app.errorMessage.set('Something went wrong');
      fixture.detectChanges();

      app.clearError();
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.header-error-banner')).toBeNull();
      expect(app.errorMessage()).toBeNull();
    });

    it('should clear error banner when a subsequent search succeeds', () => {
      const fixture = TestBed.createComponent(App);
      fixture.detectChanges();
      const app = fixture.componentInstance;

      // First trigger an error during search
      mockResourceService.getResources.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 0 }))
      );
      app.searchString.set('faulty');
      app.onSearch();
      fixture.detectChanges();

      expect(app.errorMessage()).toContain('Backend service is not reachable');
      let compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.header-error-banner')).toBeTruthy();

      // Now recover: search succeeds
      mockResourceService.getResources.mockReturnValue(of(mockSearchResult));
      app.searchString.set('valid');
      app.onSearch();
      fixture.detectChanges();

      expect(app.errorMessage()).toBeNull();
      compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('.header-error-banner')).toBeNull();
    });
  });

  describe('formatTypeLabel', () => {
    it('should format resource types with clean human-readable labels', () => {
      const fixture = TestBed.createComponent(App);
      const app = fixture.componentInstance;
      expect(app.formatTypeLabel('compute')).toBe('VMs');
      expect(app.formatTypeLabel('vm')).toBe('VMs');
      expect(app.formatTypeLabel('storage')).toBe('Buckets');
      expect(app.formatTypeLabel('vmdisks')).toBe('VM Disks');
      expect(app.formatTypeLabel('disk')).toBe('VM Disks');
      expect(app.formatTypeLabel('volume')).toBe('VM Disks');
      expect(app.formatTypeLabel('network-vpc')).toBe('VPCs');
      expect(app.formatTypeLabel('network')).toBe('Load Balancers');
      expect(app.formatTypeLabel('billing')).toBe('Invoices');
      expect(app.formatTypeLabel('billing-org')).toBe('Invoices');
      expect(app.formatTypeLabel('iam')).toBe('IAM Policies');
      expect(app.formatTypeLabel('')).toBe('Unknown');
      expect(app.formatTypeLabel('database')).toBe('Databases');
    });
  });

  describe('Token Flow (Deprecated) support', () => {
    it('should correctly identify deprecated token flow resources via isTokenFlowDeprecated', () => {
      const fixture = TestBed.createComponent(App);
      const app = fixture.componentInstance;

      const deprecatedResource: StackitResource = {
        id: 'sa-1',
        resourceId: 'sa-1',
        name: 'legacy-sa@sa.stackit.cloud',
        type: 'iam',
        status: 'ACTIVE',
        region: 'global',
        projectId: 'proj-1',
        data: {
          authScheme: 'Token Flow (Deprecated)',
          deprecated: true,
          legacyModel: 'The legacy model where a long-lived, static API secret acted directly as a bearer token.'
        }
      };

      const modernResource: StackitResource = {
        id: 'sa-2',
        resourceId: 'sa-2',
        name: 'modern-sa@sa.stackit.cloud',
        type: 'iam',
        status: 'ACTIVE',
        region: 'global',
        projectId: 'proj-1',
        data: {
          authScheme: 'Key Flow (RSA_2048)',
          deprecated: false
        }
      };

      const oidcUser: StackitResource = {
        id: 'u-1',
        resourceId: 'u-1',
        name: 'user@stackit.de',
        type: 'iam',
        status: 'ACTIVE',
        region: 'global',
        projectId: 'proj-1',
        data: {
          authScheme: 'OIDC / Enterprise SSO'
        }
      };

      expect(app.isTokenFlowDeprecated(deprecatedResource)).toBe(true);
      expect(app.isTokenFlowDeprecated(modernResource)).toBe(false);
      expect(app.isTokenFlowDeprecated(oidcUser)).toBe(false);
    });

    it('should toggle search query when filterTokenFlow is called', () => {
      const fixture = TestBed.createComponent(App);
      const app = fixture.componentInstance;

      expect(app.searchString()).toBe('');
      app.filterTokenFlow();
      expect(app.searchString()).toBe('Token Flow');
      expect(mockResourceService.getResources).toHaveBeenCalledWith('Token Flow');

      // Toggle off
      app.filterTokenFlow();
      expect(app.searchString()).toBe('');
      expect(mockResourceService.getResources).toHaveBeenCalledWith('');
    });

    it('should render the Token Flow (red) and Key Flow (orange) filter buttons in order in the UI', () => {
      const fixture = TestBed.createComponent(App);
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      const buttons = compiled.querySelectorAll('.filter-chip-btn');
      expect(buttons.length).toBe(2);

      // First button is Token Flow in red
      expect(buttons[0].classList).toContain('tokenflow-filter-btn');
      expect(buttons[0].textContent).toContain('Token Flow');

      // Second button is Key Flow in orange
      expect(buttons[1].classList).toContain('keyflow-filter-btn');
      expect(buttons[1].textContent).toContain('Key Flow');
    });

    it('should render deprecated warning chip on resources using Token Flow (Deprecated)', () => {
      const deprecatedSa: StackitResource = {
        id: 'sa-111',
        resourceId: 'legacy-sa@sa.stackit.cloud',
        name: 'legacy-sa@sa.stackit.cloud',
        type: 'iam',
        status: 'ACTIVE',
        region: 'global',
        projectId: 'proj-1',
        data: {
          authScheme: 'Token Flow (Deprecated)',
          deprecated: true,
          legacyModel: 'The legacy model where a long-lived, static API secret acted directly as a bearer token.'
        }
      };

      mockResourceService.getResources.mockReturnValue(of({
        resources: [deprecatedSa],
        totalCount: 1,
        typeAggregations: [{ key: 'IAM Policies', count: 1 }],
        regionAggregations: [{ key: 'global', count: 1 }],
        statusAggregations: [{ key: 'ACTIVE', count: 1 }]
      }));

      const fixture = TestBed.createComponent(App);
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const chip = compiled.querySelector('.deprecated-chip');
      expect(chip).toBeTruthy();
      expect(chip?.textContent).toContain('Token Flow (Deprecated)');
    });

    it('should toggle search query when filterKeyFlow is called', () => {
      const fixture = TestBed.createComponent(App);
      const app = fixture.componentInstance;

      expect(app.searchString()).toBe('');
      app.filterKeyFlow();
      expect(app.searchString()).toBe('Key Flow');
      expect(mockResourceService.getResources).toHaveBeenCalledWith('Key Flow');

      // Toggle off
      app.filterKeyFlow();
      expect(app.searchString()).toBe('');
      expect(mockResourceService.getResources).toHaveBeenCalledWith('');
    });

    it('should render the Key Flow filter button in the UI', () => {
      const fixture = TestBed.createComponent(App);
      fixture.detectChanges();
      const compiled = fixture.nativeElement as HTMLElement;
      const keyFlowBtn = compiled.querySelector('.keyflow-filter-btn');
      expect(keyFlowBtn).toBeTruthy();
      expect(keyFlowBtn?.textContent).toContain('Key Flow');
    });
  });
});
