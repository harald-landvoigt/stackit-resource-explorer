-- Migration V1.2.0: Performance B-Tree indexes on sort, filter, and aggregation columns
-- Supports fast pagination (ORDER BY created_at DESC) and active resource aggregations (WHERE deleted_at IS NULL)

-- B-Tree indexes for fast sorting and recent resource retrieval
CREATE INDEX IF NOT EXISTS idx_stackit_resources_created_at_desc
    ON stackit_resources (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_stackit_resources_updated_at_desc
    ON stackit_resources (updated_at DESC);

-- Partial B-Tree indexes for active resource filtering and multi-dimensional aggregations
CREATE INDEX IF NOT EXISTS idx_stackit_resources_type_active
    ON stackit_resources (type)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_stackit_resources_region_active
    ON stackit_resources (region)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_stackit_resources_status_active
    ON stackit_resources (status)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_stackit_resources_project_id_active
    ON stackit_resources (project_id)
    WHERE deleted_at IS NULL;
