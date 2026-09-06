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
  aggregations?: AggregationItem[];
}

