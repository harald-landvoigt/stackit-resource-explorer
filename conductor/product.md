# Initial Concept
A resource explorer backend service.

---

# Product Guide: StackIT Resource Explorer

## 1. Vision & Overview
The **StackIT Resource Explorer** consists of a high-performance Java/Quarkus backend service and a responsive Angular frontend. Its purpose is to provide developer-friendly, automated discovery, cataloging, querying, and visual exploration of resources within the StackIT Hyperscaler environment. It serves as a unified discovery registry, enabling teams to view and filter active resources with minimal latency and high reliability.

## 2. Core Features
- **Comprehensive Resource Cataloging & Registry**: Automatically discover, index, and tag resources provisioned on the StackIT cloud hyperscaler:
  - **Compute (VMs)**: Server instances with power state, machine type, availability zone, boot volume, attached block storage, security groups, and IP addresses.
  - **Storage**: Object Storage buckets and Block Storage VM disks:
    - **Object Storage**: Automated data-plane security auditing via ephemeral JIT S3 credentials:
      - Bucket ACL analysis detecting public access grants (`AllUsers`, `AuthenticatedUsers`).
      - Bucket Policy extraction storing raw JSON policies in metadata and flagging wildcard principals or missing TLS enforcement.
      - Retention & compliance locks (`COMPLIANCE`, `GOVERNANCE`).
      - Security risk scoring and consolidated public access flags (`isPublic`, `publicAccessType`, `securityFindings`).
    - **VM Disks (Block Storage & Attachment Tracking)**: Persistent disk volumes with performance class, bootable flag, and server attachments. Cross-references active compute instances to detect attached servers (including deallocated/shelved servers) and resolve parent server names. Accurately identifies idle/orphan volumes (`attached: false`, `attachmentStatus: "UNATTACHED"`), indexed in PostgreSQL for full-text search.
  - **Networking**:
    - **Virtual Private Clouds (VPC)**: Network topologies, CIDR prefixes, and gateway routing.
    - **Load Balancers**: Application Load Balancers, listeners, and target pools.
    - **Public IPs (Attached & Unattached / Floating)**: Standalone public IP allocations queried directly via the STACKIT IaaS v2 API (`/v2/projects/{projectId}/regions/{region}/public-ips`) across configured regions (`eu01`, `eu02`). Accurately detects attachment state (`attached: true|false`, `attachmentStatus: "ATTACHED"|"UNATTACHED"`), cross-references compute VM server network interfaces to identify parent server IDs and server names, and flags orphan/floating IPs for instant discovery and cleanup.
  - **Identity & Access Management (IAM)**: Project-level role bindings (members) and defined Service Accounts. Catalogs persistent Object Storage S3 access keys and credentials groups across regions (`eu01`, `eu02`), tracking expiration (`ACTIVE` vs. `EXPIRED`), credentials groups, and HMAC key IDs while excluding transient audit keys (`resource-explorer-audit`). Fully supports non-expiring keys ("Never expires") mapped to `expires: "Never"` and status `ACTIVE`. Automatically inspects cryptographic keys and tokens to identify authentication schemes (`Key Flow (RSA_2048)`, `OIDC / Enterprise SSO`, `S3 HMAC Key`, and platform managed). Flags legacy static API secrets (`Token Flow (Deprecated)` - *"The legacy model where a long-lived, static API secret acted directly as a bearer token."*).
  - **DNS Zones & Record Sets**: Scrapes DNS zones (`/v1/projects/{projectId}/zones`) and all configured record sets (A, AAAA, CNAME, MX, TXT, etc.) with automatic multi-page pagination. Automatically cross-references record target IPs against cataloged Public IPs and VM instances, displaying contextual infrastructure link badges (VM, Public IP) in the UI alongside an expandable record sets table. In addition, each record set is cataloged as an independent standalone entity (`dns-record-set`) with dedicated soft-deletion lifecycle auditing, ensuring deleted record sets retain exact deletion timestamps and historical auditability even when removed from active zones.
  - **Billing & Cost Tracking**: Project expenditures and organizational totals for the current calendar month in UTC via the STACKIT Cost API v3.
- **Dynamic Search & Query**: Native PostgreSQL Full-Text Search (FTS) indexing resource properties, tags, and data attributes with relevance ranking (`ts_rank`) and web search syntax (`websearch_to_tsquery`) via `GET /resources?q=...`. Returned resource items are capped at 100 elements for snappy browser rendering, accompanied by exact backend-calculated aggregations across the entire matching dataset. Indexes authentication schemes, bucket policies, public access tags, and IP attachment status for zero-configuration searching (`"Token Flow"`, `"is-public"`, `"unattached vmdisks"`, `"unattached public-ip"`, `"S3 Access Key"`, `"PUBLIC_READ"`, `"compliance"`, `"dns-record-set"`).
- **Hyperscaler Integration**: Purpose-built exclusively for the **StackIT** cloud infrastructure, interfacing directly with StackIT SDKs and REST APIs to fetch resource topologies.
- **Responsive Web Explorer**: A multi-tab web dashboard built on Angular and Angular Material featuring:
  - **Header & Attribution**: Branded header toolbar with Landvoigt IT attribution link (`by landvoigt-it.com`), custom STACKIT SVG/ICO favicon, and an integrated, dismissible error notification banner.
  - **Segmented Tab Navigation**: High-contrast, mobile-responsive segmented navigation bar featuring contextual Material icons (`layers`, `receipt_long`) and live item count badges for resources and billing items.
  - **Authentication & Security Quick Filters**: 1-click quick-filter buttons located below the search bar:
    - **Token Flow (Red)**: Fast filter for service accounts and members utilizing deprecated static API tokens (`"Token Flow"`).
    - **Key Flow (Orange)**: Fast filter for service accounts utilizing modern asymmetric RSA key pairs (`"Key Flow"`).
    - **Public Buckets (Rose)**: Fast filter for S3 buckets exposed publicly to the internet (`is-public: true`).
    - **Unattached Disks (Amber)**: Fast filter for orphan / idle block storage disks (`"unattached vmdisks"`).
    - **Unattached IPs (Purple)**: Fast filter for orphan / unattached floating public IPs (`"unattached public-ip"`).
    - **S3 Keys (Sky Blue)**: Fast filter for persistent Object Storage S3 access keys (`"S3 Access Key"`).
    - **DNS Zones (Purple)**: Fast filter for DNS zones and their associated record sets (`"dns-zone"`).
  - **Security, Identity & Attachment Badges & Expandable Inspector**:
    - **Disk Attachment Badges**: 🟡 **`[Unattached]`** (amber warning for idle disks), 🟢 **`[Attached: <serverName>]`** (green badge with VM name), and 🔵 **`[Boot Disk]`** (blue badge for OS root volumes).
    - **Public IP Badges**: 🟢 **`[Attached (VM: <serverName>)]`** (green badge indicating server attachment) and 🟣 **`[Unattached / Floating]`** (purple badge for unassigned public IPs).
    - **S3 Access Key Badges**: 🔷 **`[S3 Key: <groupName>]`** (sky-blue credentials group badge) and 🔴 **`[EXPIRED]`** (red warning for expired keys).
    - **Bucket Status Badges**: 🔴 **Public** / 🟢 **Private** / 🟠 **UNKNOWN** (for unreadable ACLs or restricted permissions) and 🔒 **Compliance** / **Governance** retention badges.
    - **DNS Zone & Match Badges**: 🌐 **`[PRIMARY]`** / **`[SECONDARY]`** (zone type), 📋 **`[N Records]`** (record count), 👁️ **`[Public]`** / **`[Private]`** (visibility), and 🖥️ **`[VM: <serverName>]`** / 📡 **`[Public IP]`** infrastructure match links in the expandable record sets table.
    - **DNS Record Set Badges**: 🏷️ **`[<TYPE>]`** (e.g., A, CNAME, TXT), 🌐 **`[Zone: <zoneName>]`** (parent zone link), ⏱️ **`[TTL: <seconds>s]`**, and 🖥️ **`[VM: <serverName>]`** / 📡 **`[Public IP]`** infrastructure match badges on standalone record set cards with dedicated target rows.
    - **Soft-Deleted Resources**: Decommissioned resources display with red card accents (`.deleted-card`), `DELETED` status badge (`.deleted-status`) with `delete_outline` icon, and a `Deleted At: <timestamp>` detail row.
    - **Expandable S3 Inspector**: Showing active ACL grants table, security findings warnings, and formatted raw bucket policy JSON.
  - **Resource Explorer**: Real-time resource searching, capped list display with `"Showing X of Y items"` indicator, exact Resource UUID display with distinct Resource ID when applicable, formatted metadata, and stacked summary aggregations (By Resource Type, By Project, By Region, and By State) with responsive height limits (`max-height: 70vh`) and custom orange scrollbar. Unfiltered inventory and search queries include soft-deleted resources by default.
  - **Billing Summary**: A summary of up-to-date costs for the current calendar month, strictly formatted with Organization total pinned to the first row, followed by projects ordered descending by cost.
  - **Access Issues View & Project Matrix**: Real-time tracking of scraper permission outcomes across projects and resource types (`compute`, `storage`, `network`, `network-vpc`, `public-ip`, `vmdisks`, `iam`, `dns-zone`, `billing`). Features a live counter badge on the tab label, KPI summary cards (Total Projects Checked, Affected Projects, Total Issues), a Project × Resource Type status matrix with color-coded status badges (🟢 `OK`, 🔴 `DENIED`, ⚪ `N/A`), and a filterable active issues diagnostic table with search and issues-only toggle.
  The dashboard is styled in a high-contrast black and deep orange theme (`#000000` / `#ff6f00`).

## 3. Security & Access Control
- **Service Account Key Authentication**: Authenticates with STACKIT services using modern asymmetric RSA key flow (`scraper.json`) and resilient token management.
- **Service Account Permissions & Custom Scraper Role**:
  - The scraper operates either via predefined roles (`project.auditor` / domain viewers) or via a single unified **Custom Scraper Role** containing 133 exact STACKIT permissions covering Resource Manager, Compute, Block Storage, VPC, Load Balancers, Object Storage, IAM, DNS, Databases, and Billing.
  - **Storage Admin (`objectstorage.admin`) or Custom Scraper Role** is required on target projects to dynamically mint and clean up ephemeral Just-In-Time (JIT) S3 access keys for bucket ACL and policy auditing.
  - **Guaranteed Ephemeral S3 Key Cleanup**: If client initialization or session setup fails partway, the minted access key is immediately cleaned up in error handling before failing out.
  - In projects where only read-only roles (`objectstorage.viewer` or `objectstorage.auditor`) are present, JIT minting fails gracefully (`403`), and the scraper marks the bucket with an orange **UNKNOWN** status (`ACL_NOT_ACCESSIBLE`).
  - **S3 Access Keys Cataloging (IAM)**: Reading persistent credentials groups and S3 access keys only requires read permissions (`object-storage.credentials-group.list`, `object-storage.access-key.list`), satisfied by `objectstorage.auditor` or `objectstorage.viewer`.
- **Authentication Scheme Auditing**: Automatically crawls and audits project service accounts, access keys, and members for compliance, differentiating modern key-based and OIDC flows from deprecated static API tokens.
- **Read-Only REST Architecture**: Mutation endpoints (such as `POST /resources`) are eliminated; all inventory data is ingested through internal cloud scrapers.
- **Internal Network & Port Isolation**: PostgreSQL database (5432) and Quarkus backend (8080) expose no public host ports in production, communicating over internal Docker networks behind an Nginx reverse proxy (8081).
- **Resilient Logging Standards**: Scraping steps and resource counts run at `INFO` level. Catch blocks strictly log at `WARN` or `ERROR` level (never `INFO`) to ensure caught errors and degraded fallback paths remain visible. Non-activated services (HTTP 404) are logged as `INFO`.
- **Database Lifecycle**: Hibernate ORM runs in `validate` mode against Flyway versioned migrations with `quarkus.flyway.baseline-on-migrate=false` to prevent silent migration skipping.

## 4. Technology Stack
- **Backend Language**: Java 21
- **Framework**: Quarkus REST & Quarkus Arc
- **Frontend Language & Framework**: TypeScript / Angular 21.2.22
- **Frontend UI Styling**: Angular Material M3 (black/orange theme)
- **Target Platform**: StackIT Cloud APIs
