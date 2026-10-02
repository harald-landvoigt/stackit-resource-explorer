# Specification: DNS Zones & Record Sets Scraper

## 1. Context & Motivation
Domain Name System (DNS) management is a critical networking component in the STACKIT cloud ecosystem. Organizations manage root domains, internal and public subdomains, and routing configurations across multiple cloud projects.
Currently, the STACKIT Resource Explorer catalogs VMs, VPC networks, public IPs, and load balancers, but has no visibility into DNS zones or routing records.

This track introduces a dedicated **DNS Resources Scraper** to:
1. Discover and catalog all DNS Zones across accessible STACKIT projects.
2. Index DNS Record Sets (A, AAAA, CNAME, TXT, MX, NS, SRV, CAA, etc.) and record targets.
3. Cross-reference DNS A/AAAA records against cataloged **Public IPs** and **Load Balancers** to map hostnames to physical cloud infrastructure.
4. Support full-text search across domain names, subdomains, TXT verification strings, and IP endpoints.

---

## 2. STACKIT DNS API & Pagination Analysis

### 2.1 API Endpoints
* **List Zones:** `GET /v1/projects/{projectId}/zones`
* **List Record Sets:** `GET /v1/projects/{projectId}/zones/{zoneId}/rrsets`
* **Authorization Actions:** `dns.zone.list`, `dns.recordSet.list` (roles: `dns.viewer`, `dns.auditor`, `project.auditor`)

### 2.2 Paging Requirement Assessment
> [!IMPORTANT]
> **Pagination IS REQUIRED for STACKIT DNS**:
> In the official STACKIT DNS OpenAPI specification (`services/dns/v1/dns.json`):
> 
> 1. **Zone Listing (`/v1/projects/{projectId}/zones`)**:
>    - Parameters:
>      - `page`: 1-based page index (default: `1`, minimum: `1`).
>      - `pageSize`: Items per page (default: `100`, maximum: `10000`).
>    - Response envelope (`ListZonesResponse`):
>      ```json
>      {
>        "itemsPerPage": 100,
>        "totalItems": 142,
>        "totalPages": 2,
>        "zones": [ ... ]
>      }
>      ```
> 
> 2. **Record Set Listing (`/v1/projects/{projectId}/zones/{zoneId}/rrsets`)**:
>    - Parameters:
>      - `page`: 1-based page index (default: `1`, minimum: `1`).
>      - `pageSize`: Items per page (default: `100`, maximum: `10000`).
>    - Response envelope (`ListRecordSetsResponse`):
>      ```json
>      {
>        "itemsPerPage": 100,
>        "totalItems": 450,
>        "totalPages": 5,
>        "rrSets": [ ... ]
>      }
>      ```
> 
> **Scraping Strategy**:
> - The scraper MUST implement a pagination loop: start at `page = 1` and continue while `page <= totalPages`.
> - Recommended `pageSize` parameter is `100` (or `500`) to balance memory footprint and request round-trips.

---

## 3. Functional Requirements

### 3.1 Primary Resource: DNS Zone
- **Entity Properties:**
  - `id`: Deterministic UUID derived from `projectId + "/" + zone.getId()` or Zone UUID.
  - `resourceId`: `zone.getId()`
  - `name`: `zone.getDnsName()` (e.g. `example.com.`)
  - `type`: `StackitConstants.RESOURCE_TYPE_DNS_ZONE` (`"dns-zone"`)
  - `region`: `"global"` (DNS zones are globally distributed)
  - `projectId`: STACKIT Project UUID
  - `status`: Zone state (`CREATE_SUCCEEDED` -> `ACTIVE`, `DELETING`, `CREATE_FAILED`, etc.)
  - `tags`:
    - `zone-type`: `"primary"` | `"secondary"`
    - `visibility`: `"public"`
    - `is-reverse`: `"true"` | `"false"`
    - `active`: `"true"` | `"false"`
    - `record-count`: string count of records
    - User-defined labels (`labelsMap`)
  - `data` JSON payload:
    - `dnsName`: `zone.getDnsName()`
    - `description`: `zone.getDescription()`
    - `primaryNameServer`: `zone.getPrimaryNameServer()`
    - `contactEmail`: `zone.getContactEmail()`
    - `defaultTTL`: `zone.getDefaultTTL()`
    - `serialNumber`: `zone.getSerialNumber()`
    - `recordCount`: `zone.getRecordCount()`
    - `acl`: `zone.getAcl()`
    - `recordSetsSummary`: Aggregated counts by record type (e.g. `{"A": 12, "CNAME": 5, "TXT": 8, "MX": 2}`)
    - `recordSets`: Array of key record sets (name, type, ttl, records, active) indexed for deep full-text searching

### 3.2 Infrastructure Cross-Referencing
- During ingestion, inspect A and AAAA record targets:
  - If a record target IP matches an active or historical **Public IP** (`public-ip`), annotate record metadata with `matchedPublicIpId` and `matchedServerName`.
  - Enables instant 1-click navigation from DNS name to cloud VM or Load Balancer in Resource Explorer.

### 3.3 Persistence & Soft-Deletion
- Validate DTOs with Jakarta Bean Validation.
- Persist or update using `repository.persistOrUpdate(entity)`.
- Soft-delete removed zones via `repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_DNS_ZONE, projectId, currentResourceIds)`.

### 3.4 Scheduled Scraping & Access Tracking
- Schedule: `@Scheduled(every = "${stackit.dns.schedule:1h}")`.
- Record access outcomes in `AccessIssueRegistry` under `dns`.
- Inactive / disabled DNS service (HTTP 404) logged as `INFO`.
- Permission denials (HTTP 401/403) logged as `WARN` and recorded as `AccessStatus.DENIED`.

### 3.5 Frontend UI & Search Integration
- Format type label in backend and frontend as `"DNS Zones"`.
- Quick-filter button in UI toolbar: **DNS Zones** (`.dns-filter-btn`, `"DNS Zones"`).
- Card presentation:
  - Display domain name prominently (e.g. `api.example.com.`) with DNS icon (`dns` Material icon).
  - Badges: 🌐 `[Primary]`, 🔒 `[Public]`, 🏷️ `[24 Records]`.
  - Expandable record sets table showing Record Name, Type (A, CNAME, TXT, MX), TTL, and Target values.
  - Target matching badge: Green `[VM: web-prod-01]` if target IP matches a cataloged server.

---

## 4. Non-Functional Requirements
- **Cognitive Complexity**: Keep all scraper methods strictly `< 15` using decomposed pagination and mapping helpers.
- **Final Modifier**: Enforce `final` on all method parameters and local variables per `GEMINI.md`.
- **TDD & High Coverage**: >80% test coverage with unit tests for DTO, mapper, and paginated scraper client.
