# STACKIT Resource Explorer

A comprehensive resource discovery, cataloging, querying, and visual exploration platform designed for the **STACKIT** cloud hyperscaler.

The application consists of a high-performance **Quarkus (Java 21)** backend, an **Angular (Angular Material M3)** frontend with a modern black-and-orange theme, and a **PostgreSQL** relational database.

> [!WARNING]
> **Early Project Status**: This project is in active, early-stage development. APIs, data schemas, and UI components may evolve rapidly. Feedback, issues, and contributions are welcome!

---

## UI Preview

### 1. Unified Cloud Dashboard & Multi-Dimensional Aggregations
![STACKIT Resource Explorer - Overview](docs/assets/Screenshot-1.png)

### 2. Live Full-Text Search & Real-Time Filtering
![STACKIT Resource Explorer - Search Filtering](docs/assets/Screenshot-2.png)

### 3. Deep Metadata & IP Address Search
![STACKIT Resource Explorer - Deep IP Search](docs/assets/Screenshot-3.png)

## Architecture Overview

```
                          ┌───────────────────────────┐
                          │  Angular Web Application  │
                          │   (Port 8081 via Nginx)   │
                          └─────────────┬─────────────┘
                                        │ /resources
                                        ▼
                          ┌───────────────────────────┐
                          │   Quarkus Backend (JVM)   │
                          │        (Port 8080)        │
                          └───────┬───────────┬───────┘
                                  │           │
                 Scrapes Cloud    │           │ Persists / Queries
                                  ▼           ▼
                     ┌──────────────────┐  ┌──────────────────────┐
                     │   STACKIT APIs   │  │ PostgreSQL Database  │
                     │  - Resource Mgr  │  │     (Port 5432)      │
                     │  - IaaS (VM/VPC) │  └──────────────────────┘
                     │  - Block Storage │
                     │  - Load Balancer │
                     │  - ObjectStorage │
                     │  - IAM / SA      │
                     │  - Cost API v3   │
                     └──────────────────┘
```

---

## Core Capabilities

- **Automatic Multi-Project Discovery**: Automatically discovers the parent organization and recursively traverses the entire folder hierarchy to crawl all nested projects using the STACKIT Resource Manager API.
- **Compute Scraper (Virtual Machines)**:
  - Scrapes VM instances across all discovered projects via the STACKIT IaaS API (`/v1/projects/{projectId}/servers`).
  - Captures rich metadata: Availability Zone (mapped into region), power status (`RUNNING`, `SHUTOFF`), machine type/size, boot volume ID & termination policy, attached volume IDs, security groups, SSH keypair names, and IPv4/public IP addresses.
  - Automatically parses server labels and maps them to resource tags.
- **Storage Scrapers**:
  - **Object Storage & S3 Security Analysis**: Catalogs buckets and regional endpoints. Uses dynamic Just-In-Time (JIT) S3 access credentials to inspect bucket ACLs, raw bucket policy JSON, and compliance locks (Object Lock & retention periods). Evaluates public exposure risks, applying status badges: 🔴 **Public** (with exposure method), 🟢 **Private**, or 🟠 **UNKNOWN** (when ACL data is unreadable or JIT access is forbidden). Provides an expandable policy and ACL viewer in the UI.
  - **VM Disks (Block Storage & Attachment Tracking)**: Catalogs persistent block storage volumes (`/v1/projects/{projectId}/volumes`), capturing volume size, status, performance class, source, and server attachments. Cross-references active compute instances in the project to reliably detect attached servers (including deallocated/shelved servers where Cinder reports `volume.getServerId() == null`) and resolve parent server names. Accurately identifies idle/orphan volumes with `attached: false` and `attachmentStatus: "UNATTACHED"`, indexed in PostgreSQL for instant full-text discovery.
- **Network Scrapers**:
  - **Virtual Private Clouds (VPC)**: Catalogs network VPC topologies (`/v1/projects/{projectId}/networks`), capturing prefixes, gateway routing, and labels.
  - **Load Balancers**: Catalogs application load balancers, listeners, and target pools via the STACKIT Load Balancer API.
- **IAM & Authentication Scraper**: Recursively catalogs identities, permissions, and authentication flows across all discovered projects:
  - **Members (Access Control)**: Project-level role bindings for users, groups, and service accounts via the STACKIT Authorization API (`/v2/project/{projectId}/members`). Correlates project members to service accounts to inherit authentication scheme metadata.
  - **Service Accounts (Defined Identities)**: Service accounts defined within each project via the STACKIT Service Account API (`/v2/projects/{projectId}/service-accounts`).
  - **Authentication Scheme & Deprecation Detection**:
    - Distinguishes modern asymmetric RSA/ECDSA key pairs (`Key Flow (RSA_2048)`), human SSO (`OIDC / Enterprise SSO`), and platform-managed identities.
    - Detects and tags legacy static API secrets (`Token Flow (Deprecated)` - *"The legacy model where a long-lived, static API secret acted directly as a bearer token."*), exposing active token counts and expiration dates.
- **Cost & Consumption Scraper**: Periodically queries the **STACKIT Cost API v3** (`https://cost.api.stackit.cloud/v3/costs/{customerAccountId}`) for the current calendar month in UTC:
  - Catalogs expenses for each project (`billing`) and computes the aggregate organization total (`billing-org`).
  - Automatically converts amounts from cents to EUR.
  - Features an on-demand fallback: when the `/resources/billing-summary` endpoint is queried, if no records exist in cache yet, it triggers an immediate scrape.
- **Interactive UI Dashboard**:
  - **Authentication & Security Quick Filters**:
    - **Public Buckets (Rose)**: 1-click filter for publicly exposed storage buckets (`is-public: true`).
    - **Unattached Disks (Amber)**: 1-click filter for idle / orphan block storage disks (`"unattached"`), enabling quick identification of wasted storage spend.
    - **Token Flow (Red)**: Filters service accounts and users utilizing deprecated static API tokens (`"Token Flow"`).
    - **Key Flow (Orange)**: Filters service accounts utilizing modern asymmetric RSA key pairs (`"Key Flow"`).
    - Prominent warning chips on resource cards utilizing deprecated static token credentials (searchable anytime via `"Token Flow"`).
  - **Resource Explorer**: Search and filter discovered resources in real time via PostgreSQL Full-Text Search. Returns results capped at 100 elements for ultra-fast rendering while displaying a `"Showing X of Y items"` indicator.
  - **Resource Details, Badges & UUIDs**:
    - Displays the exact **Resource UUID** alongside any distinct human-readable **Resource ID** (such as bucket names or IAM accounts). Cleanly formats complex metadata (arrays of IPs or volumes) and excludes blank fields.
    - **Disk Attachment Badges**: VM disks display clear color-coded badges: 🟡 **`[Unattached]`** (amber warning for idle/orphan volumes), 🟢 **`[Attached: <serverName>]`** (green badge with parent VM name), and 🔵 **`[Boot Disk]`** (blue badge for OS root volumes).
  - **Multi-Dimensional Summary Aggregations**: Backend-calculated exact counts stacked across four distinct dimensions with a responsive scrollable container (`max-height: 70vh`) and custom orange scrollbar matching the resource explorer:
    - **By Resource Type** (*VMs*, *Buckets*, *VM Disks*, *Invoices*, *Networks*, *IAM Policies*)
    - **By Project** (e.g. *resource-explorer*, *sandbox-1*, *sandbox-2*, or *Global / No Project* with automatic project ID-to-name resolution)
    - **By Region** (e.g. *eu01*, *eu01-1*, *eu01-3*, *global*)
    - **By State** (e.g. *ACTIVE*, *RUNNING*, *AVAILABLE*, and *DELETED* with warning accents)
  - **Billing Summary**: Aggregated project and organization consumption for the current calendar month in UTC with currency conversions. The Organization total is pinned to the first row, followed by projects ordered descending by costs.
- **Production-Ready Persistence & Flyway Migrations**:
  - Schema lifecycle and GIN full-text index managed via versioned Flyway migrations (`V1.0.0__init_schema_and_fts_gin_index.sql`).
  - Hibernate ORM runs in `validate` mode to safeguard against schema drift.

---

## What's Missing / Planned Roadmap

While the Resource Explorer provides robust automated discovery and real-time search across core STACKIT infrastructure, the following architectural and enterprise capabilities are planned for upcoming releases:

- **Instance Profiles & Workload Identity (Zero Static Secrets)**:
  - Currently, the backend authenticates using a mounted service account JSON key file (`scraper.json`).
  - *Planned*: Support for STACKIT VM metadata service / instance profiles and SKE Workload Identity tokens, eliminating the need to generate, rotate, and mount static JSON key files.

- **User Authentication & Role-Based Access Control (OIDC / SSO / RBAC)**:
  - Currently, the web dashboard and REST APIs operate without authentication, intended for secure internal network deployments.
  - *Planned*: OpenID Connect (OIDC) / OAuth2 authentication integrating with STACKIT SSO or enterprise identity providers (e.g. Keycloak, Azure AD/Entra ID), with granular RBAC to scope access (e.g. restricting billing summaries or project visibility by user team).

- **Expanded STACKIT Resource Coverage**:
  - **STACKIT Kubernetes Engine (SKE)**: Clusters, node pools, Kubernetes versions, maintenance schedules, and cluster health states.
  - **Database as a Service (DaaS)**: Managed PostgreSQL, MariaDB, Redis, RabbitMQ, and OpenSearch service instances.
  - **DNS Engine**: Forward/reverse DNS zones, record sets, and routing policies.
  - **Secrets Manager**: Vault instances, active secrets status, and encryption key rotation states.
  - **Observability (Argus / LogMe)**: Centralized monitoring, alerting, and OpenSearch logging clusters.

- **Near Real-Time Event-Driven Ingestion**:
  - Currently, cataloging relies on scheduled polling intervals (`1h` default).
  - *Planned*: Event-driven ingestion using STACKIT audit log streaming or webhooks for near real-time updates upon resource creation, modification, or teardown.

- **Historical Change Auditing & Drift Tracking**:
  - Currently, resources update their current state snapshot and track soft-deletion (`deletedAt`).
  - *Planned*: Point-in-time configuration history and change timeline (e.g., detecting when a private S3 bucket became public or when security group rules were modified).

- **Cost Optimization & FinOps Recommendations**:
  - Expanding on unattached disk detection to provide automated cost-saving recommendations (e.g., calculating monthly savings for purging orphan block storage, identifying unused public IPs, or right-sizing underutilized VMs).

- **Compliance & Inventory Export**:
  - 1-click export of discovered resources, security findings, and orphan disks to CSV / JSON / PDF.
  - Native Prometheus `/metrics` endpoint exposing discovery counts, scraper latency, and security finding gauges.

- **Automated Terraform Setup for Scraper Service Account & IAM Roles**:
  - Currently, administrators must manually provision the service account and assign organization or per-service roles (e.g. `objectstorage.admin`, `project.auditor`, etc.).
  - *Planned*: Provide ready-to-use Terraform / OpenTofu modules leveraging the official STACKIT Terraform Provider to automatically provision the scraper service account, bind minimal required roles across organization or folder hierarchies, and export credentials.

- **Native Multi-Architecture Container Images (ARM64 / Apple Silicon)**:
  - Currently, pre-built container images published to GHCR are built for `linux/amd64` only; running them on Apple Silicon Macs relies on Docker Desktop or OrbStack emulation (Rosetta 2 / QEMU) with platform mismatch warnings.
  - *Planned*: Configure CI/CD (`publish-containers.yml`) to publish multi-platform container manifests (`linux/amd64,linux/arm64`) for both backend (Quarkus Jib) and frontend (Docker Buildx) for seamless, native execution on ARM64 hardware.

---

## Scraper Service Account Prerequisites & IAM Permissions

To crawl projects, services, and billing across an organization or project hierarchy, the scraper service account key (`scraper.json`) requires appropriate STACKIT IAM permissions.

### Recommended Roles
* **Organization / Folder Level**:
  * `project.auditor` or `reader` / `viewer` across the organization or folder tree.
* **Per-Service Roles (if using granular permissions)**:

| Service Domain | Recommended Role | Required Permissions / Capabilities |
| :--- | :--- | :--- |
| **Resource Manager** | `resourcemanager.organization.viewer`, `resourcemanager.project.viewer` | Discovery of folders and child projects |
| **Compute (VMs)** | `iaas.viewer` or `iaas.admin` | `iaas.server.read` to list servers |
| **VM Disks (Storage)** | `iaas.viewer` or `iaas.admin` | `iaas.volume.read` to list block storage volumes |
| **Network VPC** | `iaas.viewer` or `iaas.admin` | `iaas.network.read` to list VPC networks |
| **Load Balancers** | `loadbalancer.auditor` or `loadbalancer.viewer` | `loadbalancer.loadbalancer.read` |
| **Object Storage (Basic Discovery)** | `objectstorage.auditor` or `objectstorage.viewer` | `object-storage.bucket.list`, `object-storage.service.list` (lists buckets; public exposure status will be UNKNOWN) |
| **Object Storage (S3 Security Audit)** | `objectstorage.admin` or custom role | Full 10 scraper permissions (see details below) for JIT S3 key minting, compliance lock inspection, and ACL/policy scraping |
| **IAM Members** | `authorization.auditor` | `authorization.member.read` |
| **Service Accounts & Credentials** | `service-account.viewer` or `service-account.auditor` | `serviceaccount.serviceaccount.read`, `serviceaccount.token.read`, `serviceaccount.key.read` |
| **Billing / Cost** | `cost.viewer` or `billing.viewer` | Read access to STACKIT Cost API v3 |

### Object Storage Scraper Role & Granular IAM Permissions

To enable the full S3 Security Audit (inspecting bucket ACLs, bucket policies, and compliance locks without degradation), the scraper service account requires either the predefined `objectstorage.admin` role or a custom IAM role with the following **10 permissions**:

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

#### Why Each Permission is Needed:
* **`object-storage.bucket.list`**: Lists all storage buckets in each region (`GET /v1/projects/{projectId}/buckets`).
* **`object-storage.service.list`**: Discovers enabled Object Storage service regions (`GET /v1/projects/{projectId}/regions`).
* **`object-storage.compliance-lock.list`**: Reads project-level compliance lock and retention configurations (`GET /v1/projects/{projectId}/compliance-lock`).
* **`object-storage.credentials-group.create`**: Creates the dedicated ephemeral audit credentials group (`resource-explorer-audit`) in the target project.
* **`object-storage.credentials-group.list`**: Checks for an existing audit credentials group before creating a new one.
* **`object-storage.credentials-group.delete`**: Cleans up the audit credentials group upon cleanup.
* **`object-storage.access-key.create`**: Mints a short-lived (15-minute) S3 access key pair (`accessKey` / `secretAccessKey`) used exclusively for data-plane inspection.
* **`object-storage.access-key.list`**: Queries active access keys within the audit credentials group.
* **`object-storage.access-key.delete`**: Immediately deletes the ephemeral access key upon completion of the scrape in a `finally` block.
* **`object-storage.service-account.list`**: Lists service accounts associated with Object Storage credentials and groups.

> [!WARNING]
> ### Storage Admin (`objectstorage.admin`) or Custom Scraper Role Required for S3 ACL & Policy Scraping
> In STACKIT Object Storage, S3 access keys cannot have fine-grained permissions attached directly, nor can S3 data-plane credentials be derived from a service user without creating credentials groups and access keys.
> To inspect bucket ACLs, bucket policies, and compliance locks, the scraper uses **dynamic Just-In-Time (JIT) S3 credential minting**: it creates an ephemeral audit credentials group (`resource-explorer-audit`) and a temporary access key, queries the regional S3 data plane, and immediately deletes both the access key and credentials group in a `finally` block.
> 
> **You MUST assign either the `objectstorage.admin` (Storage Admin) role or a custom role containing the 10 permissions above** to the service account on target projects (or at the organization/folder level).
> 
> - **With `objectstorage.admin` or custom scraper role**: The scraper evaluates bucket exposure as **Public** (🔴) or **Private** (🟢), and records granular ACL grants and bucket policy statements.
> - **Without these permissions** (e.g., if only `objectstorage.viewer` or `objectstorage.auditor` is assigned): The scraper can list bucket names via the control-plane API, but JIT key generation will fail with `403 Forbidden`. The scraper gracefully degrades by recording the `ACL_NOT_ACCESSIBLE` security finding, tagging the bucket with `is-public: unknown`, and rendering an **orange badge for UNKNOWN** (🟠).

> **Note**: If a service is not enabled for a project or the service account lacks access to a specific project, the scrapers log a non-fatal warning (`403 Forbidden` / `404 Not Found`) and continue processing remaining projects.

---

## Quickstart with Docker Compose

### Option A: Run Pre-built Images from GitHub Container Registry (Recommended)

You can run the entire stack without cloning the repository or installing build dependencies:

1. **Download the Docker Compose file**:
   ```bash
   curl -sSL -O https://raw.githubusercontent.com/harald-landvoigt/stackit-resource-explorer/main/docker/docker-compose.yml
   ```

2. **Provide your STACKIT Service Account Key**:
   - **Default**: Place your key as `scraper.json` in the same directory:
     ```bash
     cp /path/to/your/sa-key.json ./scraper.json
     ```
   - **Custom path via `.env`**:
     ```bash
     echo "STACKIT_KEY_FILE=/absolute/path/to/my-sa-key.json" > .env
     ```
   - **Custom path via inline variable**:
     ```bash
     STACKIT_KEY_FILE=/absolute/path/to/my-sa-key.json docker compose up -d
     ```

3. **Start the containers**:
   ```bash
   docker compose up -d
   ```
   Docker will automatically pull the pre-built images from GHCR:
   - Backend: `ghcr.io/harald-landvoigt/stackit-resource-explorer/backend:latest`
   - Frontend: `ghcr.io/harald-landvoigt/stackit-resource-explorer/frontend:latest`
   - Database: `postgres:15-alpine`

---

### Option B: Build & Run from Source (Local Development)

If you have cloned the repository and wish to build containers locally from source:

1. **Provide Service Account Key**:
   - By default, the compose override looks for `.keys/scraper.json` at the repo root (`../../.keys/scraper.json` from `docker/`).
   - Or configure your key path using `.env`:
     ```bash
     cd docker
     cp .env.example .env
     # Edit STACKIT_KEY_FILE=/path/to/your/sa-key.json in .env
     ```
   - Or pass it inline:
     ```bash
     STACKIT_KEY_FILE=/path/to/your/sa-key.json docker compose up -d --build
     ```

2. **Start the Stack with Local Build**:
   ```bash
   cd docker
   docker compose up -d --build
   ```
   *(Docker Compose automatically merges `docker-compose.override.yml` to build backend and frontend images from local source).*

3. **Alternatively, build the backend image directly with Quarkus Jib**:
   ```bash
   cd ../backend
   ./mvnw package -DskipTests -Dquarkus.container-image.build=true
   ```

### Services & Port Mappings

| Service | Port | Description |
| :--- | :--- | :--- |
| **Frontend** | `8081` | Angular Web Dashboard & Nginx reverse proxy |
| **Backend** | `8080` | Quarkus REST API & Scheduled Scraper Engine |
| **Database** | `5432` | PostgreSQL persistence store |

---

## Configuration & Environment Variables

### Docker Compose Variables (Host Level)

These variables can be set in a `.env` file (see `docker/.env.example`) or passed directly on the command line:

| Variable | Default (Standalone) | Default (Local Repo) | Description |
| :--- | :--- | :--- | :--- |
| `STACKIT_KEY_FILE` | `./scraper.json` | `../../.keys/scraper.json` | Host path to your STACKIT service account JSON key (supports absolute or relative paths) |
| `IMAGE_TAG` | `latest` | `latest` | Container image tag pulled from GHCR (`backend` & `frontend`) |
| `DB_PASSWORD` | `stackit` | `stackit` | PostgreSQL database password |

### Backend Scraper & Application Variables (Container Level)

The backend can be configured via `application.properties` or overridden with environment variables:

| Property | Environment Variable | Default | Description |
| :--- | :--- | :--- | :--- |
| `stackit.sdk.service-account-key-path` | `STACKIT_SERVICE_ACCOUNT_KEY_PATH` | `/app/keys/scraper.json` | Internal container path where the service account key is mounted |
| `stackit.storage.s3.endpoint-template` | `STACKIT_S3_ENDPOINT_TEMPLATE` | `https://object.storage.%s.onstackit.cloud` | Regional S3 data-plane endpoint template (`%s` is replaced by region, e.g. `eu01`) |
| `stackit.compute.schedule` | `STACKIT_COMPUTE_SCHEDULE` | `1h` | Schedule for Compute VM Scraper (`1h`, cron, or `off`) |
| `stackit.storage.schedule` | `STACKIT_STORAGE_SCHEDULE` | `1h` | Schedule for Object Storage Scraper |
| `stackit.vmdisks.schedule` | `STACKIT_VMDISKS_SCHEDULE` | `1h` | Schedule for VM Disk (Block Storage) Scraper |
| `stackit.network.schedule` | `STACKIT_NETWORK_SCHEDULE` | `1h` | Schedule for Load Balancer Scraper |
| `stackit.network-vpc.schedule` | `STACKIT_NETWORK_VPC_SCHEDULE` | `1h` | Schedule for Network VPC Scraper |
| `stackit.iam.schedule` | `STACKIT_IAM_SCHEDULE` | `1h` | Schedule for IAM Scraper |
| `stackit.billing.schedule` | `STACKIT_BILLING_SCHEDULE` | `1h` | Schedule for Cost & Billing Scraper |

---

## REST API Endpoints

- `GET /resources?q={query}`: Retrieves resources and exact aggregations matching the search query. Capped at a maximum of 100 resource items for performance, returning a complete aggregation envelope:
  ```json
  {
    "resources": [
      {
        "id": "9ae87fb6-c501-489c-84f1-bdc367ad44a3",
        "resourceId": "9ae87fb6-c501-489c-84f1-bdc367ad44a3",
        "name": "sbx-1-vm-1",
        "type": "compute",
        "status": "ACTIVE",
        "region": "eu01-3",
        "projectId": "f58b4f27-68d7-4bd6-b0f3-2e36a783ad1a",
        "tags": {
          "cost-center": "4711",
          "owner": "harald.landvoigt"
        },
        "data": {
          "machineType": "g1r.1d",
          "powerStatus": "RUNNING",
          "availabilityZone": "eu01-3",
          "bootVolumeId": "ad390c83-58d6-46ee-aa5a-9decaddc187f",
          "attachedVolumes": ["ad390c83-58d6-46ee-aa5a-9decaddc187f"],
          "ipAddresses": ["192.168.1.10", "193.148.160.5"]
        }
      }
    ],
    "totalCount": 1450,
    "typeAggregations": [
      { "key": "VMs", "count": 850 },
      { "key": "Buckets", "count": 400 },
      { "key": "Networks", "count": 150 },
      { "key": "IAM Policies", "count": 50 }
    ],
    "projectAggregations": [
      { "key": "resource-explorer", "count": 750 },
      { "key": "sandbox-1", "count": 500 },
      { "key": "sandbox-2", "count": 150 },
      { "key": "Global / No Project", "count": 50 }
    ],
    "regionAggregations": [
      { "key": "eu01-3", "count": 850 },
      { "key": "eu01", "count": 550 },
      { "key": "global", "count": 50 }
    ],
    "statusAggregations": [
      { "key": "ACTIVE", "count": 900 },
      { "key": "RUNNING", "count": 500 },
      { "key": "DELETED", "count": 50 }
    ]
  }
  ```
- `GET /resources/{id}`: Retrieves details for a specific resource by UUID.
- `GET /resources/billing-summary`: Returns aggregated current-month expenses grouped by project and organization in EUR. Automatically triggers an on-demand scrape if the database cache is empty.

---

## Local Development & Testing

### Backend (Quarkus / Java 21)
```bash
cd backend
./mvnw test                  # Run unit and integration test suite (90 tests)
./mvnw quarkus:dev           # Run dev mode with hot reload (Dev UI at http://localhost:8080/q/dev)
```

### Frontend (Angular 21 / Vitest)
```bash
cd frontend
npm test -- --watch=false    # Run unit tests via Vitest (43 tests)
ng serve                     # Start development server on port 4200 (proxies backend to 8080)
```
