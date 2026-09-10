# STACKIT Resource Explorer - Frontend

The frontend is a responsive, high-performance web dashboard built with **Angular 21** and **Angular Material**, designed with a custom black-and-deep-orange theme matching the Landvoigt IT / STACKIT brand identity.

It provides real-time exploration, multi-attribute filtering, type aggregations, and calendar-month cost tracking for cloud resources discovered across the STACKIT hyperscaler environment.

---

## Features & UI Layout

### 1. Header Banner & Branding
- Persistent top toolbar displaying the platform branding **StackIT Resource Explorer**.
- **Branded Favicon**: Modern SVG and high-resolution multi-size ICO favicon custom-designed with the STACKIT black-and-orange motif.
- **Attribution Link**: `by landvoigt-it.com` hyperlink directly before the cloud icon, opening Landvoigt IT in a new window.
- **Dismissible Error Banner**: Sits directly beneath the header; dynamically renders backend error messages or connectivity failures (HTTP status 0) and can be dismissed manually or clears upon successful requests.

### 2. Segmented Navigation Bar (Tabs)
The top navigation uses a custom segmented pill-style container (`mat-tab-group`) engineered for high contrast, rapid visual recognition, and responsive layout:
- **Resource Explorer**:
  - Contextual `layers` Material icon.
  - Dynamic count badge displaying the exact total inventory count (`totalCount`) in real time, even when display items are capped.
  - Active state highlighted with a warm orange gradient (`rgba(255, 111, 0, 0.18)`), glowing border, and bright orange text (`#ff9233`).
- **Billing Summary**:
  - Contextual `receipt_long` Material icon.
  - Dynamic count badge displaying the number of cost items for the current calendar month.
  - High-contrast inactive text (`#e2e8f0`) with hover brightness transitions.
- **Mobile-Adaptive**: Smooth horizontal scrolling and auto-adjusted padding on smaller viewports (`<= 600px`).

### 3. Resource Explorer Tab
- **Real-Time Search Bar**: Full-text searching across resource name, type, region, status, tags, and data attributes via PostgreSQL FTS.
- **1-Click Quick Filters**: Fast toggle filter buttons directly below the search input:
  - **Token Flow (Red)**: Filters service accounts and members utilizing deprecated static API tokens (`"Token Flow"`). Styled with `.tokenflow-filter-btn` and red warning accent (`#ef4444`).
  - **Key Flow (Orange)**: Filters service accounts utilizing modern asymmetric RSA key pairs (`"Key Flow"`). Styled with `.keyflow-filter-btn` and deep orange key accent (`#ff6f00`).
  - **Public Buckets (Rose)**: Filters publicly accessible S3 storage buckets (`"is-public: true"`). Styled with `.public-filter-btn` and rose accent (`#f43f5e`).
  - **Unattached Disks (Amber)**: Filters unattached / orphan VM block storage disks (`"unattached"`). Styled with `.unattached-filter-btn` and amber accent (`#f59e0b`).
  - All quick filter buttons toggle on/off with a single click.
- **Summary Aggregations Card (Left Column)**: Stacked sections displaying exact backend-computed breakdowns across the full dataset. Features a responsive height constraint (`max-height: 70vh` on desktop, `45vh` on mobile) with a custom orange scrollbar matching the resource explorer:
  - **By Resource Type**: Counts for *VMs*, *Buckets*, *VM Disks*, *Invoices*, *Networks*, *IAM Policies*.
  - **By Project**: Counts partitioned per STACKIT project (e.g. *resource-explorer*, *sandbox-1*, *sandbox-2*, or *Global / No Project*).
  - **By Region**: Counts by region / availability zone (e.g. *eu01*, *eu01-3*, *global*).
  - **By State**: Counts by status and lifecycle (*ACTIVE*, *RUNNING*, *AVAILABLE*, and *DELETED* with red accent).
- **Discovered Resources Card (Right Column)**: Scrollable list of resource cards capped at 100 elements for optimal browser performance, showing `"Showing X of Y items"` subtitle when capped:
  - **Disk Attachment Badges**: VM disks render color-coded attachment chips:
    - 🟡 **`[Unattached]`** (`.unattached-badge`): Warning badge for orphan/idle disks.
    - 🟢 **`[Attached: <serverName>]`** (`.attached-badge`): Safe green badge displaying the parent VM name.
    - 🔵 **`[Boot Disk]`** (`.boot-badge`): Blue badge designating root operating system boot volumes.
  - **Storage Exposure Badges & Policy Viewer**: Object storage buckets display 🔴 **Public**, 🟢 **Private**, or 🟠 **UNKNOWN** badges, retention compliance badges, and an expandable viewer for bucket policies and ACL grantee tables.
  - **Deprecated Auth Warning Chip**: Prominent red/amber `Token Flow (Deprecated)` chip rendered in the card header for any service account or member relying on legacy static tokens.
  - **Status & Type Chips**: Visual status badges with green accents for active states.
  - **Resource UUID**: Always renders the primary database UUID (`res.id`).
  - **Resource ID**: Rendered when a distinct resource identifier exists that differs from the UUID (e.g., Object Storage bucket names or IAM service account emails).
  - **Region & Project ID**: Location details (including availability zone for VMs).
  - **Highlighted Billing Details**: Highlighted amount and currency for billing items.
  - **Rich Metadata Grid**: Clean key-value grid excluding empty or null values; complex objects and arrays (such as attached volume IDs or IP lists) are neatly formatted as comma-separated values via `formatMetaValue()`.
  - **Tags**: Rendered as stylized badge chips for quick visual inspection.

### 4. Billing Summary Tab
- **Cost Table**: Displays current calendar month costs (UTC) grouped by project and organization.
- **Strict Sorting & Highlighting**: The **Organization** summary is pinned to the very first row (styled with `.org-row` and persistent warm orange tint), followed by projects ordered descending by costs.
- Shows resource name, project ID, classification type (Project vs. Organization), and formatted cost in EUR.

---

## Design System & Styling

- **Theme Palette**:
  - Background: Pure Black (`#000000`)
  - Elevated Surfaces: `#101010`, `#121212`, `#1c1c1c`
  - Accent Color: Deep Orange (`#ff6f00` / `#ff851b`)
  - Text: High-contrast white (`#ffffff`) and soft silver (`#e2e8f0` / `#d6d6d6`)
- **Component Styling**: Angular Material components (`mat-toolbar`, `mat-card`, `mat-chips`, `mat-tab-group`, `mat-form-field`) customized via SCSS custom properties and targeted overrides. Features dedicated `.deprecated-chip` warning badges, `.unattached-badge`, `.attached-badge`, `.boot-badge`, `.tokenflow-filter-btn`, `.keyflow-filter-btn`, `.public-filter-btn`, `.unattached-filter-btn`, and `.org-row` high-visibility table row styling.
- **Performance Budgets**: Strictly meets Angular build budgets (`maximumWarning: 16kB` for component styles) with optimized SCSS structure and global tab navigation theme styles in `styles.scss`.

---

## Architecture & State Management

- **Angular Signals**: Modern reactive state management using Angular signals (`signal`, `computed`):
  - `resources`: Resource collection capped at 100 elements for fast rendering.
  - `totalCount`: Exact total matching count across the backend database.
  - `typeAggregations`: Signal storing exact category breakdown.
  - `projectAggregations`: Signal storing exact project breakdown.
  - `regionAggregations`: Signal storing exact cloud region breakdown.
  - `statusAggregations`: Signal storing exact state/lifecycle breakdown.
  - `billingSummary`: Current-month cost summaries.
  - `errorMessage`: Active error message string, rendered in the header error banner.
  - `selectedIndex`: Active navigation tab index.
  - `searchString`: Input query string.
  - `filteredResources`: Computed signal deriving matching items from search terms.
- **Service Layer**: `ResourceService` encapsulates HTTP communication:
  - `GET /resources?q=...`: Retrieves `ResourceSearchResult` containing capped resources, `totalCount`, and all 4 aggregation arrays.
  - `GET /resources/billing-summary`: Retrieves aggregated calendar-month costs.

---

## Development & Testing Commands

### Prerequisites
- Node.js 20+
- npm 11+

### Install Dependencies
```bash
npm install
```

### Start Local Development Server
```bash
npm start
# or: ng serve
```
Runs at `http://localhost:4200/`. API calls to `/resources` are proxied to `http://localhost:8080` via `proxy.conf.json`.

### Run Unit Tests (Vitest)
Unit tests are powered by **Vitest** and Angular Testing Utilities (43 unit tests):
```bash
npm test -- --watch=false
```

### Production Build
```bash
npm run build
```
Compiles and optimizes the output to `dist/frontend/browser/`.

---

## Docker Deployment

The frontend is packaged as a multi-stage Docker build:
1. **Build Stage**: Compiles the Angular production bundle using `node:20-alpine`.
2. **Runtime Stage**: Serves the static assets via `nginx:alpine` and reverse-proxies `/resources` requests to the Quarkus backend on port `8080`.
Exposed on host port **8081**.

---

## Maintainer & Contact

Developed and maintained by **[Landvoigt IT](https://www.landvoigt-it.com)**.
- **Website**: [https://www.landvoigt-it.com](https://www.landvoigt-it.com)
- **Email**: [harald@landvoigt-it.com](mailto:harald@landvoigt-it.com)

