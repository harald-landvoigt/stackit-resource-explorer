CREATE TABLE IF NOT EXISTS stackit_resources (
    id UUID NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    region VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    tags JSONB,
    data JSONB,
    search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(resource_id, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(type, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(tags::text, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(status, '')), 'C') ||
        setweight(to_tsvector('english', coalesce(region, '')), 'C') ||
        setweight(to_tsvector('english', coalesce(project_id, '')), 'C') ||
        setweight(to_tsvector('english', coalesce(data::text, '')), 'C')
    ) STORED,
    CONSTRAINT pk_stackit_resources PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_stackit_resources_search_vector_gin
    ON stackit_resources USING gin(search_vector);
