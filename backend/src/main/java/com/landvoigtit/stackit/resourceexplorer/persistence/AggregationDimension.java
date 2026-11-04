package com.landvoigtit.stackit.resourceexplorer.persistence;

import java.util.Objects;

/**
 * Type-safe enumeration of SQL aggregation dimensions for stackit_resources.
 * Guarantees that only compile-time verified, immutable SQL column expressions
 * can be evaluated during database aggregation queries.
 */
public enum AggregationDimension {

    TYPE("type"),
    REGION("coalesce(region, 'global')"),
    STATUS("CASE WHEN deleted_at IS NOT NULL THEN 'DELETED' ELSE UPPER(coalesce(status, 'UNKNOWN')) END"),
    PROJECT("CASE WHEN project_id IS NULL OR project_id = '' OR LOWER(project_id) = 'unknown' THEN 'Global / No Project' ELSE project_id END");

    private final String expression;

    AggregationDimension(final String expression) {
        this.expression = Objects.requireNonNull(expression, "Expression must not be null");
    }

    /**
     * Gets the trusted SQL column expression for this dimension.
     *
     * @return the SQL expression fragment
     */
    public String getExpression() {
        return expression;
    }
}
