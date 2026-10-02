# Plan: DNS Zones & Record Sets Scraper

## Implementation Steps

- [x] **Phase 1: Domain Constants, DTO, and Mappers**
  - [x] Add `RESOURCE_TYPE_DNS_ZONE = "dns-zone"` to [`StackitConstants.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/config/StackitConstants.java) [4722a35]
  - [x] Add schedule configuration `stackit.dns.schedule=1h` in [`application.properties`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/resources/application.properties) [861954c]
  - [x] Create `DnsZoneResourceDto.java` and nested `DnsRecordSetDto.java` [77b66fe]
  - [x] Create `DnsZoneResourceMapper.java` mapping DNS Zone and Record Set API models to `DnsZoneResourceDto` and `StackitEntity` [4fcd38c]
  - [x] Create unit tests in `DnsZoneResourceMapperTest.java` [4fcd38c]
  - [x] Update `StackitResourceService.formatTypeLabel` to return `"DNS Zones"` for `"dns-zone"` [97cb389]

- [x] **Phase 2: Paginated DNS API Client Implementation**
  - [x] Create `DnsApiClient.java` using `OkHttpClient` and `ResilientKeyFlowAuthenticator` [9f08b13]
  - [x] Implement `listZones(projectId, page, pageSize)` with automatic pagination loop (`while (page <= totalPages)`) [9f08b13]
  - [x] Implement `listRecordSets(projectId, zoneId, page, pageSize)` with automatic pagination loop [9f08b13]
  - [x] Create unit tests in `DnsApiClientTest.java` verifying multi-page responses, query parameters, and error handling [9f08b13]

- [x] **Phase 3: Scraper Implementation & Infrastructure Cross-Referencing**
  - [x] Create `DnsResourceScraper.java` with `@Scheduled(every = "${stackit.dns.schedule:1h}")` [48ea43e]
  - [x] Cross-reference record target IPs with cataloged Public IPs and server instances [48ea43e]
  - [x] Record outcomes in `AccessIssueRegistry` under `dns` [48ea43e]
  - [x] Implement soft-deletion via `repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_DNS_ZONE, projectId, currentResourceIds)` [48ea43e]
  - [x] Keep method Cognitive Complexity `< 15` via single-responsibility private helper methods [48ea43e]
  - [x] Create unit and mock scraper tests in `DnsResourceScraperTest.java` [48ea43e]

- [x] **Phase 4: Frontend UI, Record Table & Search Integration**
  - [x] Add `DnsZoneResourceData` and `DnsRecordSet` interfaces to [`resource.model.ts`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/frontend/src/app/models/resource.model.ts) [e30e05a]
  - [x] Add "DNS Zones" type formatting across frontend components [e30e05a]
  - [x] Add quick-filter button for DNS Zones in [`app.html`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/frontend/src/app/app.html) and [`app.ts`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/frontend/src/app/app.ts) [e30e05a]
  - [x] Implement dedicated DNS card presentation: [e30e05a]
    - Display domain name with DNS icon
    - Zone type badge (Primary / Secondary) and record count chip
    - Expandable record sets table (Name, Type, TTL, Target)
    - Matched VM/Public IP link badge
  - [x] Add frontend unit tests in [`app.spec.ts`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/frontend/src/app/app.spec.ts) [e30e05a]

- [x] **Phase 5: End-to-End Verification & Checkpointing**
  - [x] Run full backend test suite (`./mvnw test`) [e35b1f2]
  - [x] Run full frontend test suite (`npm test -- --watch=false`) [e35b1f2]
  - [x] Update `docker-compose.yml` with `STACKIT_DNS_SCHEDULE` environment variable [e35b1f2]
  - [x] Update documentation in [`README.md`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/README.md) and [`product.md`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/conductor/product.md) [e35b1f2]

- [x] **Phase 6: Standalone DNS Record Set Entities (`dns-record-set`) & Record Deletion Tracking**
  - [x] Add `RESOURCE_TYPE_DNS_RECORD_SET = "dns-record-set"` to [`StackitConstants.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/config/StackitConstants.java) and label mapping in [`StackitResourceService.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/service/StackitResourceService.java) [ade3663]
  - [x] Implement `mapRecordSetToEntity` in [`DnsZoneResourceMapper.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/dns/DnsZoneResourceMapper.java) and unit test in [`DnsZoneResourceMapperTest.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/test/java/com/landvoigtit/stackit/resourceexplorer/dns/DnsZoneResourceMapperTest.java) [ade3663]
  - [x] Update [`DnsResourceScraper.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/dns/DnsResourceScraper.java) to persist record set entities and run `softDeleteMissing` for `dns-record-set` [ade3663]
  - [x] Update backend tests in [`DnsResourceScraperTest.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/test/java/com/landvoigtit/stackit/resourceexplorer/dns/DnsResourceScraperTest.java) [ade3663]
  - [x] Add frontend models, `formatTypeLabel`, and card presentation in [`resource.model.ts`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/frontend/src/app/models/resource.model.ts), [`app.ts`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/frontend/src/app/app.ts), [`app.html`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/frontend/src/app/app.html), [`app.scss`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/frontend/src/app/app.scss) [ade3663]
  - [x] Add frontend unit tests in [`app.spec.ts`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/frontend/src/app/app.spec.ts) [ade3663]
  - [x] Run full test suites, update documentation, and mark track complete [ade3663]

