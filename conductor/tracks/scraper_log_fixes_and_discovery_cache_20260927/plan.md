# Plan: Scraper Log Cleanup, Error Classification & Discovery Caching

This plan addresses issues **1, 2, 4, 5, and 7** identified in the STACKIT Resource Explorer runtime logs.

---

## Targeted Issues Overview

1. **Item 1: Excessive Project Discovery & Lack of Caching:**
   - Every scrape cycle and user query (`GET /resources`, `GET /resources/billing-summary`, `GET /resources?q=...`) triggers recursive Resource Manager API calls across the organization tree.
   - Fix: Thread-safe TTL caching and request coalescing in [`StackitProjectDiscoveryService`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/StackitProjectDiscoveryService.java).
2. **Item 2: ALB False-Positive "Permission Denied" & Access Issue Flag:**
   - STACKIT ALB API returns HTTP 403 with `{"message":"Service not enabled"}` when ALB is disabled in a region. This is incorrectly treated as a permission failure, logging `Permission denied` and marking `ACCESS_DENIED` in [`AccessIssueRegistry`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/access/AccessIssueRegistry.java).
   - Fix: Differentiate disabled services from real permission denials in [`StackitConstants`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/config/StackitConstants.java) and [`NetworkResourceScraper`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/network/NetworkResourceScraper.java).
3. **Item 4: AWS S3 `GetPublicAccessBlock` HTTP 501 Not Implemented:**
   - STACKIT Object Storage does not support AWS S3 `GetPublicAccessBlock`, returning HTTP 501. The scraper logs a warning for every bucket.
   - Fix: Catch HTTP 501 explicitly in [`StorageResourceScraper`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/storage/StorageResourceScraper.java) and log at `DEBUG` level.
4. **Item 5: False Warning on S3 Buckets Without Custom Bucket Policy:**
   - Buckets without policies (HTTP 404 / `NoSuchBucketPolicy`) log at `WARN` severity despite being the standard bucket configuration.
   - Fix: Demote log level to `DEBUG` in [`StorageResourceScraper`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/storage/StorageResourceScraper.java).
5. **Item 7: Noisy Multiline Warnings for Unused / Disabled Regional Services (eu02):**
   - Inactive regional services return HTTP 404, which scrapers log at `WARN` severity while dumping multiline SDK `ApiException` messages with raw Istio HTTP headers.
   - Fix: Demote expected disabled services to `DEBUG` across all scrapers and clean up error message formatting.

---

## Implementation Steps

### Phase 1: In-Memory TTL Caching & Coalescing for Project Discovery (Item 1) [checkpoint: cf56cbd]
- [x] Add TTL cache configuration property `stackit.discovery.cache-ttl` (default: `10m`) to [`StackitSdkConfig.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/config/StackitSdkConfig.java) and [`application.properties`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/resources/application.properties). [cff8934]
- [x] Update [`StackitProjectDiscoveryService.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/StackitProjectDiscoveryService.java): [cf7cf2e]
  - [x] Add cached `List<Project>` and cached `Map<String, String>` (ID to name lookup) with timestamp tracking (`Instant cachedAt`).
  - [x] Implement thread-safe synchronization/coalescing around `discoverProjects()` so parallel startup scrapers share a single remote discovery call.
  - [x] Add `discoverProjects(boolean forceRefresh)` and `getProjectNamesMap()` helper methods.
  - [x] Cache discovered organization ID so access token claims/parent hierarchy queries run only once.
- [x] Update [`StackitResourceService.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/StackitResourceService.java): [bb7b4ec]
  - [x] In `searchResources()`, `getBillingSummary()`, and `getAccessIssues()`, use cached project name mappings instead of triggering remote discovery.
- [x] Add unit tests in `StackitProjectDiscoveryServiceTest.java`: [cf7cf2e]
  - [x] Verify discovery caching returns cached data within TTL.
  - [x] Verify `forceRefresh=true` bypasses cache.
  - [x] Verify concurrent requests coalesce into a single remote API execution.

### Phase 2: Error Classification & ALB False-Positive Access Denial Fix (Item 2) [checkpoint: 183d815]
- [x] Update [`StackitConstants.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/config/StackitConstants.java): [c09abe8]
  - [x] Add `isServiceDisabled(String msg)` and `isServiceDisabled(Throwable t)` checking for:
    - `"service not enabled"`, `"servicenotenabled"`, `"not enabled"`, `"project.not_found"`, or HTTP 404.
    - HTTP 403 where body/message explicitly states `"Service not enabled"`.
  - [x] Refactor `isPermissionIssue(msg)` to ensure `isServiceDisabled(msg)` takes precedence (returns `false` if the message is merely a disabled service).
- [x] Update [`NetworkResourceScraper.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/network/NetworkResourceScraper.java): [5e2e568]
  - [x] Check `StackitConstants.isServiceDisabled(msg)` before `isPermissionIssue(msg)`.
  - [x] If disabled, log at `DEBUG` or clean `INFO` (`"ALB not enabled for project {} in region {}"`).
  - [x] Ensure disabled services do NOT mark `permissionDenied = true` and do NOT record `ACCESS_DENIED` in [`AccessIssueRegistry`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/access/AccessIssueRegistry.java).
- [x] Add unit tests in [`NetworkResourceScraperTest.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/test/java/com/landvoigtit/stackit/resourceexplorer/network/NetworkResourceScraperTest.java): [5e2e568]
  - [x] Verify HTTP 403 with `"Service not enabled"` does not produce a warning or register an access issue.
  - [x] Verify true HTTP 403 (unauthorized/forbidden role) still registers `ACCESS_DENIED`.

### Phase 3: S3 PublicAccessBlock (501) & Bucket Policy (404) Log Cleanup (Items 4 & 5) [checkpoint: 963ee84]
- [x] Update [`StorageResourceScraper.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/storage/StorageResourceScraper.java): [85a6634]
  - [x] In `enrichWithS3` step 2 (Bucket Policy): Change log level for `NoSuchBucketPolicy` / 404 from `log.warn(...)` to `log.debug(...)`.
  - [x] In `enrichWithS3` step 3 (Public Access Block): Catch `S3Exception` where `statusCode() == 501` or error code is `NotImplemented`; log at `log.debug(...)` without logging a warning.
  - [x] In `enrichRetention`: Check whether compliance lock is active on the project; if inactive or 409 `compliance_lock.required` is returned, handle cleanly at `DEBUG` without warning logs.
- [x] Add unit tests in [`StorageResourceScraperTest.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/test/java/com/landvoigtit/stackit/resourceexplorer/storage/StorageResourceScraperTest.java): [85a6634]
  - [x] Verify S3 501 Not Implemented on `getPublicAccessBlock` logs at `DEBUG` and defaults to standard security evaluation.
  - [x] Verify 404 on bucket policy does not log a warning.

### Phase 4: Multi-region Disabled Service Noise Reduction & Error Sanitization (Item 7)
- [x] Update [`StackitConstants.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/config/StackitConstants.java): [7be0714]
  - [x] Add `cleanErrorMessage(Throwable t)` / `cleanErrorMessage(String msg)` to extract concise descriptions (e.g. `HTTP 404: Not Found`) rather than printing multiline Istio headers and raw payloads.
- [x] Update regional scrapers: [7be0714]
  - [x] [`ComputeResourceScraper.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/compute/ComputeResourceScraper.java): Use `isServiceDisabled` and log disabled services at `DEBUG`.
  - [x] [`VmDiskResourceScraper.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/storage/VmDiskResourceScraper.java): Use `isServiceDisabled` and log disabled services at `DEBUG`.
  - [x] [`NetworkVpcResourceScraper.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/network/NetworkVpcResourceScraper.java): Use `isServiceDisabled` and log disabled services at `DEBUG`.
  - [x] [`PublicIpResourceScraper.java`](file:///home/hadi/workspace/landvoigt-it-sources/stackit/resource-explorer/backend/src/main/java/com/landvoigtit/stackit/resourceexplorer/network/PublicIpResourceScraper.java): Clean up 404 message formatting so raw HTTP headers are not logged.
- [x] Add/update scraper unit tests to verify concise error logging for disabled services. [7be0714]

### Phase 5: Verification & Quality Gate
- [ ] Run full Maven test suite (`./mvnw clean test`) and ensure 100% pass rate.
- [ ] Run Angular frontend test suite (`npm test -- --watch=false`).
- [ ] Verify in Docker logs that:
  - Startup project discovery runs once instead of 8 times.
  - Search queries and billing summary requests do not log repeated project discovery scans.
  - No 501 PublicAccessBlock warnings are logged.
  - No bucket policy missing warnings are logged.
  - No multiline HTTP 404 warnings appear for `eu02` disabled services.
  - No false `ACCESS_DENIED` status appears on `/resources/access-issues` for ALB.
