export interface StackitResource {
  id: string;
  resourceId: string;
  name: string;
  type: string;
  status: string;
  region: string;
  projectId: string;
  createdAt?: string;
  updatedAt?: string;
  deletedAt?: string;
  tags?: Record<string, string>;
  data?: Record<string, any>;
}

export interface BillingSummary {
  id: string;
  name: string;
  type: string;
  amount: number;
  currency: string;
}

export interface AggregationItem {
  key: string;
  count: number;
  type?: string;
}

export type TypeAggregation = AggregationItem;

export interface ResourceSearchResult {
  resources: StackitResource[];
  totalCount: number;
  typeAggregations: AggregationItem[];
  regionAggregations: AggregationItem[];
  statusAggregations: AggregationItem[];
  projectAggregations?: AggregationItem[];
  aggregations?: AggregationItem[];
}
export interface StorageGrant {
  grantee?: string;
  granteeType?: string;
  permission?: string;
}

export interface StorageAcl {
  owner?: string;
  ownerId?: string;
  grants?: StorageGrant[];
}

export interface StorageRetention {
  mode?: string;
  retentionDays?: number;
  defaultRetentionSet?: boolean;
  projectMaxRetentionDays?: number;
}

export interface StorageResourceData {
  storageClass?: string;
  objectLockEnabled?: boolean;
  urlPathStyle?: string;
  urlVirtualHostedStyle?: string;
  isPublic?: boolean | null;
  publicAccessType?: string;
  bucketPolicy?: string;
  acl?: StorageAcl;
  retention?: StorageRetention;
  securityFindings?: string[];
  [key: string]: any;
}

export interface VmDiskResourceData {
  sizeGb?: number;
  performanceClass?: string;
  availabilityZone?: string;
  bootable?: boolean;
  encrypted?: boolean;
  sourceType?: string;
  sourceId?: string;
  attached?: boolean;
  attachmentStatus?: string;
  serverId?: string;
  serverName?: string;
  bootVolume?: boolean;
  [key: string]: any;
}

