# STACKIT Resource Explorer - Backend

This project is a high-performance cloud resource scraper, indexer, and query backend for the **STACKIT** cloud platform, powered by **Quarkus (Java 21)**, **Hibernate ORM with Panache**, and **PostgreSQL** with native Full-Text Search.

---

## Running the Application

### Development Mode
Runs the application with hot-reload enabled and starts testcontainers Dev Services for PostgreSQL:
```bash
./mvnw quarkus:dev
```
> **_NOTE:_** The Quarkus Dev UI is available at <http://localhost:8080/q/dev/>.

#### Testing
Executes unit tests and integration tests against containerized PostgreSQL and mocked/live STACKIT APIs (232 tests):
```bash
./mvnw test
```

### Packaging & Production Run
```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

Or build an über-jar:
```bash
./mvnw package -Dquarkus.package.jar.type=uber-jar
java -jar target/*-runner.jar
```

### Native Executable
```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
./target/resourceexplorer-1.0.0-SNAPSHOT-runner
```

---

## Architecture & Domain Services

### 1. Project Discovery (`StackitProjectDiscoveryService`)
1. Automatically discovers accessible organization and projects dynamically via the STACKIT Resource Manager API and service account credentials.
2. Interrogates the STACKIT Resource Manager API to discover the parent organization ID.
3. Recursively crawls the organization's folder hierarchy to collect all project IDs and project names. Projects and folders are fetched with offset/limit paging (`limit=50`); the end of a listing is detected against the limit echoed by the API, since the Resource Manager silently clamps oversized limits to its own maximum.
4. Provides cached, resilient lookup of project names for display in the UI and billing summaries.

### 2. Scheduled Scraper Services
Each scraper implements independent schedules (configurable via `application.properties` or environment variables). If a service is not activated on a project or access is restricted, scrapers catch HTTP `403` / `404` errors gracefully and proceed to the next project.

| Scraper Class | Resource Type | Target STACKIT API | Default Schedule |
| :--- | :--- | :--- | :--- |
| `ComputeResourceScraper` | `compute` | IaaS API (`/v1/projects/{projectId}/servers`) | `1h` |
| `PublicIpResourceScraper` | `public-ip` | IaaS v2 API (`/v2/projects/{projectId}/regions/{region}/public-ips`) | `1h` |
| `VmDiskResourceScraper` | `vmdisks` | IaaS API (`/v1/projects/{projectId}/volumes`) | `1h` |
| `NetworkVpcResourceScraper` | `network-vpc` | IaaS API (`/v1/projects/{projectId}/networks`) | `1h` |
| `NetworkResourceScraper` | `network` | Application Load Balancer API v2 (`/v2/projects/{projectId}/regions/{region}/load-balancers`) | `1h` |
| `StorageResourceScraper` | `storage` | Object Storage API (`/v1/projects/{projectId}/buckets`) | `1h` |
| `IamResourceScraper` | `iam` | Authorization API (`/v2/project/.../members`) & Service Account API | `1h` |
| `DnsResourceScraper` | `dns-zone` / `dns-record-set` | DNS API (`/v1/projects/{projectId}/zones`) | `1h` |
| `DatabaseResourceScraper` | `database` | PostgreSQL Flex, SQL Server Flex, MongoDB Flex, OpenSearch & MariaDB APIs | `1h` |
| `BillingResourceScraper` | `billing` / `billing-org` | Cost API v3 (`/v3/costs/{customerAccountId}`) | `1h` |

#### Public IP Scraper Details
- Scrapes standalone public IP allocations directly via the STACKIT IaaS v2 API (`/v2/projects/{projectId}/regions/{region}/public-ips`) across configured regions (`eu01`, `eu02`).
- Provides native multi-region support across both `eu01` and `eu02` where the legacy IaaS v1 API is not deployed.
- Determines attachment status: detects whether an IP is bound to a network interface (`attached: true`, `attachmentStatus: "ATTACHED"`) or idle/floating (`attached: false`, `attachmentStatus: "UNATTACHED"`).
- Cross-references active compute server entities within the project from the database to map server NICs to `serverId` and resolve `serverName`.
- Captures IP labels as resource tags, records soft-deletion when an IP is unallocated/released, and reports access permissions to `AccessIssueRegistry`.
- Indexed in PostgreSQL full-text search for instant discovery via `"unattached"` or IP addresses.
- Built with a modular, decomposed architecture: `scrapeProjectPublicIps` encapsulates server IP extraction, regional query execution, validation, error handling, and access recording into cohesive single-responsibility methods meeting strict SonarQube cognitive complexity constraints (complexity = 1, well below the 15 threshold).


#### Compute Scraper Details
- Maps instance availability zones (e.g. `eu01-3`) to `StackitEntity.region` (falling back to `DEFAULT_REGION`).
- Converts server labels into `StackitEntity.tags` for display as badges in the frontend.
- Captures granular metadata:
  - `machineType` & `size`
  - `powerStatus` (`RUNNING`, `SHUTOFF`, etc.)
  - `bootVolumeId` and `bootVolumeDeleteOnTermination`
  - `attachedVolumes` (list of volume UUIDs)
  - `ipAddresses` (aggregates IPv4 and public IPs across all server NICs)
  - `publicIps` (list of currently active public IPs)
  - `publicIpHistory` (accumulated history with `ip`, `firstSeen`, `lastSeen`, and `active` status)
  - `securityGroups`
  - `keypairName`
  - `launchedAt`

#### VM Disk Scraper Details
- Maps persistent block storage volumes with size in GB, status (`AVAILABLE`, `IN_USE`), availability zone, performance class, source type/ID, and bootable state.
- **Attachment & Orphan Disk Tracking**:
  - Identifies attachment state (`attached = true` or `false`) and status (`attachmentStatus = "ATTACHED"` or `"UNATTACHED"`).
  - Cross-references active compute server entities in the project from the database to map `bootVolumeId` and `attachedVolumes`.
  - Resolves parent `serverName` and handles edge cases where instances are deallocated or shelved (in which case Cinder may return `volume.getServerId() == null` while the server entity retains its volume reference).
  - Flags OS boot disks with `bootVolume: true`.
  - JSON metadata fields (`attached`, `attachmentStatus`, `serverName`, `serverId`, `bootVolume`) are indexed in the PostgreSQL GIN search vector for instant searchability (e.g. searching `"unattached"` returns all idle/orphan disks).

#### Storage Scraper Details (S3 & Object Storage)
- **Multi-Region Bucket Discovery**: Discovers buckets across configured regions (`eu01`, `eu02`) via `ObjectStorageApi.listBuckets`.
- **Retention & Compliance Lock**: Fetches project-level maximum compliance lock (`getComplianceLock`) and bucket default retention configurations (`getDefaultRetention`).
- **Dynamic JIT S3 Credential Minting (`S3JitKeyManager`)**:
  - Dynamically creates an ephemeral audit credentials group (`resource-explorer-audit`) and a temporary S3 access key (`expires = now + 15m`).
  - Uses regional AWS SDK v2 `S3Client` configured with STACKIT path-style endpoints to inspect bucket ACLs (`getBucketAcl`), raw bucket policies (`getBucketPolicy`), and public access blocks (`getPublicAccessBlock`).
  - **Guaranteed Cleanup & Leak Prevention**: Automatically deletes the access key and credentials group in a `finally` block immediately after inspection. Furthermore, wraps `S3Client` instantiation in a dedicated error-handling block so that if endpoint formatting or region parsing throws, the minted key is deleted immediately before failing out.
- **Security Risk Evaluation (`StorageSecurityEvaluator`)**:
  - Analyzes ACL grants (`AllUsers`, `AuthenticatedUsers`) and bucket policy statements (wildcard principals, TLS enforcement).
  - Categorizes public exposure into `PUBLIC_READ`, `PUBLIC_WRITE`, `PUBLIC_READ_WRITE`, `NOT_PUBLIC`, or `UNKNOWN`.
  - Persists raw bucket policy JSON directly in `StackitEntity.data["bucketPolicy"]` and tags entities with `is-public` (`true`, `false`, or `unknown`).
- **IAM Permission Requirement & Warning**:
  - **`objectstorage.admin` (Storage Admin) or Custom Scraper Role is REQUIRED**:
    To perform Just-In-Time S3 credential minting and evaluate ACLs and bucket policies without degradation, the service account must have `objectstorage.admin` or a custom role with the following **10 permissions**:
    ```
    object-storage.access-key.create
    object-storage.access-key.delete
    object-storage.access-key.list
    object-storage.bucket.list
    object-storage.compliance-lock.list
    object-storage.credentials-group.create
    object-storage.credentials-group.delete
    object-storage.credentials-group.list
    object-storage.service-account.list
    object-storage.service.list
    ```
    - `object-storage.bucket.list`: List buckets in each region (`GET /v1/projects/{projectId}/buckets`).
    - `object-storage.service.list`: Discover enabled service regions (`GET /v1/projects/{projectId}/regions`).
    - `object-storage.compliance-lock.list`: Read compliance lock & retention settings (`GET /v1/projects/{projectId}/compliance-lock`).
    - `object-storage.credentials-group.create`: Create ephemeral audit credentials group (`resource-explorer-audit`).
    - `object-storage.credentials-group.list`: Inspect existing credentials groups.
    - `object-storage.credentials-group.delete`: Delete ephemeral credentials group during cleanup.
    - `object-storage.access-key.create`: Generate short-lived (15-minute) S3 access key pair.
    - `object-storage.access-key.list`: Query active access keys for the credentials group.
    - `object-storage.access-key.delete`: Immediately delete the ephemeral S3 access key in `finally` cleanup.
    - `object-storage.service-account.list`: List service accounts associated with object storage.
  - **Fallback on Read-Only Roles**: If the service account only has `objectstorage.viewer` or `objectstorage.auditor`, JIT key creation will fail with `403 Forbidden`. The scraper gracefully degrades: public exposure is marked `UNKNOWN`, tagged with `is-public: unknown`, and recorded with finding `ACL_NOT_ACCESSIBLE` (rendered as an orange badge in the frontend).

#### Network VPC Scraper Details
- Maps virtual networks with IPv4/IPv6 CIDR prefixes, default gateway IPs, and network tags.

#### IAM & Authentication Scraper Details
- Scrapes project role bindings (`/v2/projects/{projectId}/members`) and project-defined service accounts (`/v2/projects/{projectId}/service-accounts`).
- Scrapes persistent Object Storage S3 access keys and credentials groups across all configured regions (`eu01`, `eu02`) via `ObjectStorageApi.listCredentialsGroups` and `listAccessKeys`.
- **SDK 0.4.0 & Expiration Handling**: Powered by `cloud.stackit.sdk:objectstorage:0.4.0`, which safely deserializes nullable expiration timestamps. Non-expiring keys ("Never expires") are recorded with `data.put("expires", "Never")` and state `ACTIVE`.
- **Audit Exclusion**: Filters out transient audit credentials groups (`resource-explorer-audit`) so ephemeral JIT keys from `S3JitKeyManager` are never cataloged.
- **Granular Permissions**: Auditing S3 access keys requires only read permissions (`object-storage.credentials-group.list` and `object-storage.access-key.list`, satisfied by `objectstorage.auditor` or `objectstorage.viewer`), unlike the full write permissions required for JIT bucket security auditing.
- Inspects active static API tokens (`/tokens`) and cryptographic public keys (`/keys`) for each service account via OkHttp REST calls.
- Identifies authentication schemes:
  - **Key Flow**: Modern asymmetric RSA/ECDSA key pairs (e.g., `Key Flow (RSA_2048)`).
  - **OIDC / Enterprise SSO**: Human user identities authenticated via corporate identity providers (captures `idpDomain`).
  - **Platform Managed**: Internal platform-managed identities.
  - **S3 HMAC Key**: Persistent S3 data-plane credentials with credentials group binding and expiration tracking (`ACTIVE` vs. `EXPIRED`).
  - **Token Flow (Deprecated)**: Detects legacy static API secrets (*"The legacy model where a long-lived, static API secret acted directly as a bearer token."*), flagging `deprecated = true`, active static token counts, expiration timestamps, and tagging with `auth-flow: "token-flow-deprecated"`.
- Correlates project members to service accounts so project-level access entries automatically inherit their service account's authentication scheme.
- Full-Text Search indexing enables instant querying by `"Token Flow"`, `"Token Flow (Deprecated)"`, `"static API secret"`, `"token-flow-deprecated"`, `"S3 Access Key"`, or `"s3-hmac-key"`.
- **Maintainability & Low Cognitive Complexity**: Scraper logic is decomposed into dedicated helper methods (`processServiceAccount`, `processMemberRole`, `scrapeProjectAccessKeysInRegion`, `processAccessKey`) to maintain cognitive complexity strictly below linter thresholds (< 15).

#### Billing / Cost Scraper Details
- Aggregates current calendar month usage in UTC.
- Converts amounts from cents to EUR (`charge / 100.0`).
- Features an on-demand fallback: when `/resources/billing-summary` is called, if the database has no billing records, an immediate scrape is triggered.
- Summary ordering places the Organization total in the first row, followed by child projects sorted descending by cost.

#### DNS Zones & Record Sets Scraper Details
- Queries DNS zones (`/v1/projects/{projectId}/zones`) and all configured record sets (A, AAAA, CNAME, MX, TXT, etc.) with automatic pagination.
- Generates standalone `dns-record-set` entities for each record, enabling dedicated searchability and independent soft-deletion tracking.
- Correlates record set target IPs to cataloged VMs and Public IPs, producing bidirectional infrastructure references.

#### Managed Database Scraper Details
- Catalogs STACKIT Managed Database and caching instances across multiple engines:
  - **Regional Engines**: PostgreSQL Flex, SQL Server Flex, MongoDB Flex, MariaDB, OpenSearch, and Key-Value Store (Valkey) across configured regions (`eu01`, `eu02`). Redis has been deprecated on STACKIT and is excluded from scraping.
- **Per-Instance Detail Enrichment**:
  - For each discovered instance (`id` or `instanceId`), fetches the full instance detail payload via `DatabaseApiClient.getInstance(...)` and merges it onto the summary payload.
  - For `postgresql` and `sqlserver`, merges both `/v2/projects/{projectId}/regions/{region}/instances/{instanceId}` (providing `acl.items`, `storage.class`, `backupSchedule`, `options.retentionDays`) and `/v3/projects/{projectId}/regions/{region}/instances/{instanceId}` (providing `connectionInfo.write` with `host`/`port`, `network.accessScope`, `network.acl`, and custom `labels`).
- **Captured Metadata & Tags**:
  - Engine type, version, replica count, and operational status (`status`, `state`, or `lastOperation.state` / `lastOperation.type`).
  - Sizing & plans: `flavor`, `flavorDescription`, `cpu`, `memory` (synthesizing `<cpu> vCPU / <memory> GB` when `flavor.id` is absent, e.g. SQL Server Flex), and `planId` / `planName` (MariaDB / OpenSearch).
  - Storage: `storage` (GB, including fallback to `parameters.max_disk_threshold`) and `storageClass`.
  - Network & connection settings: `host`, `port`, `accessScope` (`PUBLIC` / `PRIVATE`), and `acls` (extracted from `network.acl`, `acl.items`, `acls`, or comma-separated `parameters.sgw_acl`).
  - Custom `labels`: mapped both to `StackitEntity.tags` and `StackitEntity.data["labels"]`.
  - Cloud Foundry / OSB attributes (MariaDB, OpenSearch & Valkey): `cfGuid`, `cfSpaceGuid`, `cfOrganizationGuid`, `dashboardUrl`, and `parameters` merged into `options` (excluding raw base64 `imageUrl` icons).
- **Structured Operational Logging**: Emits real-time `INFO` logs detailing the target project ID, project name, region, and database engine (`Scraping database type '<engine>' for project <projectId> in region <region>`), alongside counts of discovered instances.
- Robust fault isolation: projects without activated database services (HTTP 404) trigger clean soft-deletion of missing instances without noise; permission restrictions (HTTP 403) are recorded in the `AccessIssueRegistry`.
- Cognitive Complexity (< 15) maintained across pagination, detail merging, and engine dispatch routines.

#### Pagination & Scrape Completeness
Every list call is fully paged so no resource is missed. This matters for correctness, not just coverage: after a successful scrape, `softDeleteMissing` soft-deletes every resource that was not returned, so a truncated listing would wrongly mark live resources as deleted.

| Source | Paging Strategy |
| :--- | :--- |
| Resource Manager (projects, folders) | Offset/limit (`limit=50`); stops on an empty page or a page shorter than the limit echoed by the API. |
| Application Load Balancer API | Cursor paging: `pageSize=1000` (API maximum), follows `nextPageId` until absent. A repeated `nextPageId` aborts the loop. |
| DNS API (zones, record sets) | Page-number paging (`page`, `pageSize=100`) until `totalPages` is reached. |
| Managed Database APIs | Page-number paging (`page`, `size=100`) until `pagination.totalPages`; when that metadata is missing, continues while `count` / `pagination.totalRows` exceeds the items collected. Stops when a page yields no new instance IDs (API ignoring `page`) and after a hard cap of 10,000 pages. |
| IaaS (servers, volumes, networks, public IPs) | Not paged by the API: a single call returns the complete list. |
| Object Storage (buckets, credentials groups, access keys) | Not paged by the API: a single call returns the complete list. |
| Authorization / Service Account / Cost APIs | Consumed as single, unpaged responses. |

### 3. Resilient Scraping & Code Quality Standards
All scraper jobs and backend domain services adhere to uniform coding, quality, and fault-isolation standards:
- **Cognitive Complexity (< 15)**: Every method must maintain a Cognitive Complexity strictly `< 15` (enforcing SonarLint rule `java:S3776`). Workflows with nested branching, loops, or complex error handling must be decomposed into cohesive single-responsibility helper methods.
- **Java 21 Virtual Threads**: Outbound HTTP requests during fan-out inspection—such as service account token and key auditing in `IamResourceScraper` and S3 bucket ACL/policy queries in `StorageResourceScraper`—are executed concurrently on lightweight Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), drastically reducing thread contention and wall-clock scraping time.
- **Concurrency Control & Thundering Herd Protection**: All 10 scrapers implement `concurrentExecution = Scheduled.ConcurrentExecution.SKIP` alongside `AtomicBoolean` run guards to prevent overlapping runs when operations exceed standard intervals.
- **Staggered Startup Delays**: Scrapers configure staggered startup delays (`stackit.*.delay` spanning `0s` to `4m30s`) so scrapers start predictably without competing for network I/O or CPU, while respecting dependency order (e.g. Compute runs before Disks and Public IPs).
- **HTTP Client & Service Account Key Caching**: `StackitSdkConfig` caches parsed service account JSON credentials to eliminate repeated disk I/O. Outbound OkHttp calls share a single centralized client instance with connection pooling, 15s connect, 30s read/write, and 60s call timeouts.
- **Operational Progress (`INFO`)**: Scraping phase transitions (start, project discovery, completion), target database type and region dispatch, and counts of scraped resources per service and project are logged at `INFO` level.
- **Permission Denials (`WARN`)**: When encountering HTTP `401` or `403` (Unauthorized, Forbidden) for any target project or region, scrapers log a structured warning including the project ID, region, and error payload. The failure is isolated—the scraper continues processing remaining projects.
- **Sanitized Diagnostic Logging**: Upstream HTTP error response bodies captured in the `AccessIssueRegistry` are automatically scrubbed and truncated to a maximum of 500 characters to prevent log pollution and shield sensitive headers or auth tokens.
- **Absent / Unactivated Services (`INFO`)**: When a project does not have an optional service enabled (HTTP 404 Not Found), scrapers log the event as standard `INFO` without triggering warnings.
- **Exception Catch Blocks (`WARN` / `ERROR`)**: All catch blocks across scrapers and SDK initialization strictly log at `WARN` or `ERROR` level (never `INFO`) to guarantee visibility of caught exceptions and fallback execution paths.

---

## Configuration Properties & Environment Variables

| Property | Environment Variable | Default | Description |
| :--- | :--- | :--- | :--- |
| `stackit.sdk.service-account-key-path` | `STACKIT_SERVICE_ACCOUNT_KEY_PATH` | `/app/keys/scraper.json` | Path to service account JSON key file |
| `stackit.regions` | `STACKIT_REGIONS` | `eu01,eu02` | Comma-separated list of STACKIT regions to scrape for regional services |
| `stackit.storage.s3.endpoint-template` | `STACKIT_S3_ENDPOINT_TEMPLATE` | `https://object.storage.%s.onstackit.cloud` | S3 data-plane endpoint template (`%s` replaced by region) |
| `stackit.compute.schedule` | `STACKIT_COMPUTE_SCHEDULE` | `1h` | Interval or cron for Compute VM Scraper |
| `stackit.compute.delay` | `STACKIT_COMPUTE_DELAY` | `0s` | Startup delay before initial compute scrape |
| `stackit.vmdisks.schedule` | `STACKIT_VMDISKS_SCHEDULE` | `1h` | Interval or cron for VM Disk (Block Storage) Scraper |
| `stackit.vmdisks.delay` | `STACKIT_VMDISKS_DELAY` | `30s` | Startup delay before initial disk scrape |
| `stackit.publicips.schedule` | `STACKIT_PUBLICIPS_SCHEDULE` | `1h` | Interval or cron for Public IP Scraper |
| `stackit.publicips.delay` | `STACKIT_PUBLICIPS_DELAY` | `1m` | Startup delay before initial public IP scrape |
| `stackit.network.schedule` | `STACKIT_NETWORK_SCHEDULE` | `1h` | Interval or cron for Load Balancer Scraper |
| `stackit.network.delay` | `STACKIT_NETWORK_DELAY` | `1m30s` | Startup delay before initial load balancer scrape |
| `stackit.network-vpc.schedule` | `STACKIT_NETWORK_VPC_SCHEDULE` | `1h` | Interval or cron for Network VPC Scraper |
| `stackit.network-vpc.delay` | `STACKIT_NETWORK_VPC_DELAY` | `2m` | Startup delay before initial network VPC scrape |
| `stackit.storage.schedule` | `STACKIT_STORAGE_SCHEDULE` | `1h` | Interval or cron for Object Storage Scraper |
| `stackit.storage.delay` | `STACKIT_STORAGE_DELAY` | `2m30s` | Startup delay before initial object storage scrape |
| `stackit.database.schedule` | `STACKIT_DATABASE_SCHEDULE` | `1h` | Interval or cron for Managed Database Scraper (`1h`, cron, or `off`) |
| `stackit.database.delay` | `STACKIT_DATABASE_DELAY` | `3m` | Startup delay before initial database scrape |
| `stackit.iam.schedule` | `STACKIT_IAM_SCHEDULE` | `1h` | Interval or cron for IAM Scraper |
| `stackit.iam.delay` | `STACKIT_IAM_DELAY` | `3m30s` | Startup delay before initial IAM scrape |
| `stackit.dns.schedule` | `STACKIT_DNS_SCHEDULE` | `1h` | Interval or cron for DNS Zones & Record Sets Scraper |
| `stackit.dns.delay` | `STACKIT_DNS_DELAY` | `4m` | Startup delay before initial DNS scrape |
| `stackit.billing.schedule` | `STACKIT_BILLING_SCHEDULE` | `1h` | Interval or cron for Billing / Cost Scraper |
| `stackit.billing.delay` | `STACKIT_BILLING_DELAY` | `4m30s` | Startup delay before initial billing scrape |
| `stackit.database.postgres-url` | `STACKIT_DATABASE_POSTGRES_URL` | `https://postgres-flex-service.api.stackit.cloud` | Base URL for PostgreSQL Flex API |
| `stackit.database.mongodb-url` | `STACKIT_DATABASE_MONGODB_URL` | `https://mongodb-flex-service.api.stackit.cloud` | Base URL for MongoDB Flex API |
| `stackit.database.opensearch-url` | `STACKIT_DATABASE_OPENSEARCH_URL` | `https://opensearch.api.stackit.cloud` | Base URL for OpenSearch API |
| `stackit.database.mariadb-url` | `STACKIT_DATABASE_MARIADB_URL` | `https://mariadb.api.stackit.cloud` | Base URL for MariaDB API |
| `stackit.database.sqlserver-url` | `STACKIT_DATABASE_SQLSERVER_URL` | `https://mssql-flex-service.api.stackit.cloud` | Base URL for SQL Server Flex API |
| `stackit.database.valkey-url` | `STACKIT_DATABASE_VALKEY_URL` | `https://valkey.api.stackit.cloud` | Base URL for Key-Value Store (Valkey) API |

Schedules accept standard Quarkus interval strings (`1h`, `30m`), standard cron expressions, or `off` to disable.

---

## Database & Flyway Migrations

- **Flyway Versioning**: Schema lifecycle and indexing are handled via Flyway scripts in `src/main/resources/db/migration/`:
  - `V1.0.0__init_schema_and_fts_gin_index.sql`: Initial schema, table creation, and GIN full-text index on `search_vector`.
  - `V1.1.0__cleanup_duplicate_storage_resources.sql`: Storage resource deduplication migration.
  - `V1.2.0__add_performance_btree_indexes.sql`: High-performance B-Tree indexes on `(created_at DESC)` and `(updated_at DESC)`, alongside partial active indexes on `(type, deleted_at)`, `(region, deleted_at)`, `(status, deleted_at)`, and `(project_id, deleted_at)` to accelerate common aggregation and sorting queries.
  - `V1.2.1__sync_deleted_resources_status.sql`: Synchronizes the lifecycle status of soft-deleted resources (`deleted_at IS NOT NULL`) to `DELETED`.
  - `V1.2.2__normalize_region_names.sql`: Normalizes legacy regional descriptors to canonical region names (`eu01`, `eu02`, `global`).
- **Strict Baseline Policy**: `quarkus.flyway.baseline-on-migrate=false` ensures that baseline migrations are never silently bypassed on non-empty databases.
- **Validation**: `quarkus.hibernate-orm.schema-management.strategy=validate` ensures Hibernate entities strictly adhere to Flyway-created schemas.
- **Single-Query Aggregations (`GROUPING SETS`)**:
  - `StackitResourceRepository.aggregateAll` consolidates facet counts across all four dimensions (`TYPE`, `PROJECT`, `REGION`, `STATUS` via `AggregationDimension` enum) into a single native SQL query.
  - Leverages PostgreSQL `GROUPING SETS ((type), (region), (status), (project_id))` over a single filtered CTE, avoiding multiple round-trips and repeated scans of the resource table.
- **Write Amplification & GIN Index Churn Prevention**:
  - `StackitResourceRepository.persistOrUpdate` compares incoming resource fields against existing database records. Unchanged resources are skipped, avoiding unnecessary updates that trigger expensive PostgreSQL `tsvector` recalculations and GIN index writes.
  - Batch persistence is supported through `persistOrUpdateAll`.
- **Full-Text Search & Lifecycle**:
  - Uses a stored generated `tsvector` column (`search_vector`) indexed with PostgreSQL GIN (`USING gin (search_vector)`). Includes soft-deleted items (`deletedAt != null`) in both full-text search and aggregations to provide an uncompromised resource audit trail.
  - Ranked using `ts_rank` evaluated against `websearch_to_tsquery('simple', query)`.

---

## REST API Endpoints

### `GET /resources?q={query}`
Searches discovered resources using Full-Text Search.
- **Bounded Search Protection**: Queries are validated with `@Size(max = 256)`. Long queries are tokenized and bounded to a maximum of 10 prefix tokens to protect database evaluation against ReDoS or complex AST expansion.
- **Project Name Full-Text Resolution**: When users search by a human-readable project name (e.g., `sandbox-1`), the search engine matches against cached projects from `StackitProjectDiscoveryService`, resolves corresponding project UUIDs, and returns all resources belonging to the project alongside direct text matches.
- **Capped Result Set**: Capped at 100 resources (`LIMIT 100`) for low-latency response times and consistent browser rendering.
- **Soft-Deletion Visibility**: Returns active and soft-deleted resources (marked with `deletedAt`), allowing full lifecycle visibility without requiring specialized filter syntax.
- **Unified Aggregations**: Returns exact total count and multi-dimensional aggregations (including soft-deleted items) computed in a single query via `GROUPING SETS`:
  - `typeAggregations`: Categorized counts (*VMs*, *Public IPs*, *Buckets*, *VM Disks*, *Invoices*, *Networks*, *IAM Policies*).
  - `projectAggregations`: Counts by STACKIT project (*resource-explorer*, *sandbox-1*, *sandbox-2*, *Global / No Project*) with project IDs resolved to human-readable names via `StackitProjectDiscoveryService`.
  - `regionAggregations`: Counts by normalized cloud region (`eu01`, `eu02`, `global`).
  - `statusAggregations`: Counts by resource lifecycle state (*ACTIVE*, *RUNNING*, *AVAILABLE*, *DELETED*).

> [!NOTE]
> **Read-Only API**: Mutation endpoints (e.g. `POST /resources`) are not exposed. Data ingestion is handled exclusively through authenticated internal scrapers.

### `GET /resources/{id}`
Retrieves a specific resource entity by its database UUID.

### `GET /resources/billing-summary`
Returns current-month expenditure aggregated per project and organization in EUR.

### `GET /resources/access-issues`
Returns consolidated access issues summary, Project × Resource Type status matrix, and active permission issue records with diagnostic error messages.

---

## Maintainer & Contact

Developed and maintained by **[Landvoigt IT](https://www.landvoigt-it.com)**.
- **Website**: [https://www.landvoigt-it.com](https://www.landvoigt-it.com)
- **Email**: [harald@landvoigt-it.com](mailto:harald@landvoigt-it.com)

