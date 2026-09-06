import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ResourceService } from './resource.service';
import { StackitResource } from '../models/resource.model';

describe('ResourceService', () => {
  let service: ResourceService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ResourceService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(ResourceService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch resource list from /resources endpoint', () => {
    const mockSearchResult = {
      resources: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          resourceId: 'vm-123',
          name: 'test-vm',
          type: 'compute',
          status: 'active',
          region: 'eu-central-1',
          projectId: 'project-abc'
        }
      ],
      totalCount: 1,
      typeAggregations: [{ key: 'VMs', count: 1 }],
      regionAggregations: [{ key: 'eu-central-1', count: 1 }],
      statusAggregations: [{ key: 'ACTIVE', count: 1 }],
      aggregations: [{ key: 'VMs', count: 1 }]
    };

    service.getResources().subscribe((result) => {
      expect(result.resources.length).toBe(1);
      expect(result.totalCount).toBe(1);
      expect(result.typeAggregations.length).toBe(1);
      expect(result.regionAggregations.length).toBe(1);
      expect(result.statusAggregations.length).toBe(1);
      expect(result).toEqual(mockSearchResult);
    });

    const req = httpMock.expectOne('/resources');
    expect(req.request.method).toBe('GET');
    req.flush(mockSearchResult);
  });

  it('should fetch resource list with q parameter from /resources?q=... endpoint', () => {
    const mockSearchResult = {
      resources: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          resourceId: 'vm-123',
          name: 'test-vm',
          type: 'compute',
          status: 'active',
          region: 'eu-central-1',
          projectId: 'project-abc'
        }
      ],
      totalCount: 1,
      typeAggregations: [{ key: 'VMs', count: 1 }],
      regionAggregations: [{ key: 'eu-central-1', count: 1 }],
      statusAggregations: [{ key: 'ACTIVE', count: 1 }],
      aggregations: [{ key: 'VMs', count: 1 }]
    };

    service.getResources('k8s').subscribe((result) => {
      expect(result.resources.length).toBe(1);
      expect(result.totalCount).toBe(1);
      expect(result.typeAggregations.length).toBe(1);
      expect(result).toEqual(mockSearchResult);
    });

    const req = httpMock.expectOne((r) => r.url === '/resources' && r.params.get('q') === 'k8s');
    expect(req.request.method).toBe('GET');
    req.flush(mockSearchResult);
  });
});
