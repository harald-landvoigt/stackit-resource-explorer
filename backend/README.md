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

### Testing
Executes unit tests and integration tests against containerized PostgreSQL and mocked/live STACKIT APIs:
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
3. Recursively crawls the organization's folder hierarchy to collect all project IDs and project names.
4. Provides cached, resilient lookup of project names for display in the UI and billing summaries.

### 2. Scheduled Scraper Services
Each scraper implements independent schedules (configurable via `application.properties` or environment variables). If a service is not activated on a project or access is restricted, scrapers catch HTTP `403` / `404` errors gracefully and proceed to the next project.

| Scraper Class | Resource Type | Target STACKIT API | Default Schedule |
| :--- | :--- | :--- | :--- |
| `ComputeResourceScraper` | `compute` | IaaS API (`/v1/projects/{projectId}/servers`) | `1h` |
| `VmDiskResourceScraper` | `vm-disk` | IaaS API (`/v1/projects/{projectId}/volumes`) | `1h` |
| `NetworkVpcResourceScraper` | `network-vpc` | IaaS API (`/v1/projects/{projectId}/networks`) | `1h` |
| `NetworkResourceScraper` | `network` | Load Balancer API | `1h` |
| `StorageResourceScraper` | `storage` | Object Storage API (`/v1/projects/{projectId}/buckets`) | `1h` |
| `IamResourceScraper` | `iam` | Authorization API (`/v2/project/.../members`) & Service Account API | `1h` |
| `BillingResourceScraper` | `billing` / `billing-org` | Cost API v3 (`/v3/costs/{customerAccountId}`) | `1h` |

#### Compute Scraper Details
- Maps instance availability zones (e.g. `eu01-3`) to `StackitEntity.region` (falling back to `DEFAULT_REGION`).
- Converts server labels into `StackitEntity.tags` for display as badges in the frontend.
- Captures granular metadata:
  - `machineType` & `size`
  - `powerStatus` (`RUNNING`, `SHUTOFF`, etc.)
  - `bootVolumeId` and `bootVolumeDeleteOnTermination`
  - `attachedVolumes` (list of volume UUIDs)
  - `ipAddresses` (aggregates IPv4 and public IPs across all server NICs)
  - `securityGroups`
  - `keypairName`
  - `launchedAt`

#### VM Disk Scraper Details
- Maps persistent block storage volumes with size in GB, status (`AVAILABLE`, `ATTACHED`), performance class, source, and attached server IDs.

#### Network VPC Scraper Details
- Maps virtual networks with IPv4/IPv6 CIDR prefixes, default gateway IPs, and network tags.

#### IAM & Authentication Scraper Details
- Scrapes project role bindings (`/v2/projects/{projectId}/members`) and project-defined service accounts (`/v2/projects/{projectId}/service-accounts`).
- Inspects active static API tokens (`/tokens`) and cryptographic public keys (`/keys`) for each service account.
- Identifies authentication schemes:
  - **Key Flow**: Modern asymmetric RSA/ECDSA key pairs (e.g., `Key Flow (RSA_2048)`).
  - **OIDC / Enterprise SSO**: Human user identities authenticated via corporate identity providers (captures `idpDomain`).
  - **Platform Managed**: Internal platform-managed identities.
  - **Token Flow (Deprecated)**: Detects legacy static API secrets (*"The legacy model where a long-lived, static API secret acted directly as a bearer token."*), flagging `deprecated = true`, active static token counts, expiration timestamps, and tagging with `auth-flow: "token-flow-deprecated"`.
- Correlates project members to service accounts so project-level access entries automatically inherit their service account's authentication scheme.
- Full-Text Search indexing enables instant querying by `"Token Flow"`, `"Token Flow (Deprecated)"`, `"static API secret"`, or `"token-flow-deprecated"`.

#### Billing / Cost Scraper Details
- Aggregates current calendar month usage in UTC.
- Converts amounts from cents to EUR (`charge / 100.0`).
- Features an on-demand fallback: when `/resources/billing-summary` is called, if the database has no billing records, an immediate scrape is triggered.
- Summary ordering places the Organization total in the first row, followed by child projects sorted descending by cost.

---

## Configuration Properties & Environment Variables

| Property | Environment Variable | Default | Description |
| :--- | :--- | :--- | :--- |
| `stackit.sdk.service-account-key-path` | `STACKIT_SERVICE_ACCOUNT_KEY_PATH` | `/app/keys/scraper.json` | Path to service account JSON key file |
| `stackit.compute.schedule` | `STACKIT_COMPUTE_SCHEDULE` | `1h` | Interval or cron for Compute VM Scraper |
| `stackit.storage.schedule` | `STACKIT_STORAGE_SCHEDULE` | `1h` | Interval or cron for Object Storage Scraper |
| `stackit.vmdisks.schedule` | `STACKIT_VMDISKS_SCHEDULE` | `1h` | Interval or cron for VM Disk (Block Storage) Scraper |
| `stackit.network.schedule` | `STACKIT_NETWORK_SCHEDULE` | `1h` | Interval or cron for Load Balancer Scraper |
| `stackit.network-vpc.schedule` | `STACKIT_NETWORK_VPC_SCHEDULE` | `1h` | Interval or cron for Network VPC Scraper |
| `stackit.iam.schedule` | `STACKIT_IAM_SCHEDULE` | `1h` | Interval or cron for IAM Scraper |
| `stackit.billing.schedule` | `STACKIT_BILLING_SCHEDULE` | `1h` | Interval or cron for Billing / Cost Scraper |

Schedules accept standard Quarkus interval strings (`1h`, `30m`), standard cron expressions, or `off` to disable.

---

## Database & Flyway Migrations

- **Flyway Versioning**: Schema lifecycle and indexing are handled via Flyway scripts in `src/main/resources/db/migration/`.
- **Validation**: `quarkus.hibernate-orm.schema-management.strategy=validate` ensures Hibernate entities strictly adhere to Flyway-created schemas.
- **Full-Text Search**: Uses a stored generated `tsvector` column (`search_vector`) indexed with PostgreSQL GIN (`USING gin (search_vector)`).
- **Ranking**: Matches are ranked using `ts_rank` evaluated against `websearch_to_tsquery('simple', query)`.

---

## REST API Endpoints

### `GET /resources?q={query}`
Searches discovered resources using Full-Text Search.
- Capped at 100 resources (`LIMIT 100`) for low-latency response times.
- Returns exact total count and multi-dimensional aggregations:
  - `typeAggregations`: Categorized counts (*VMs*, *Buckets*, *Invoices*, *Networks*, *IAM Policies*).
  - `regionAggregations`: Counts by cloud region / AZ (*eu01*, *eu01-3*, *global*).
  - `statusAggregations`: Counts by resource lifecycle state (*ACTIVE*, *RUNNING*, *AVAILABLE*, *DELETED*).

### `GET /resources/{id}`
Retrieves a specific resource entity by its database UUID.

### `GET /resources/billing-summary`
Returns current-month expenditure aggregated per project and organization in EUR.
