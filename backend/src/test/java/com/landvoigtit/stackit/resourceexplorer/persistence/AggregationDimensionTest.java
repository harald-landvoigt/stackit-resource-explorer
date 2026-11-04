package com.landvoigtit.stackit.resourceexplorer.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AggregationDimensionTest {

    @Test
    public void testEnumValuesAndExpressions() {
        for (final AggregationDimension dimension : AggregationDimension.values()) {
            assertNotNull(dimension.getExpression(), "Expression must not be null for " + dimension);
            assertFalse(dimension.getExpression().isBlank(), "Expression must not be blank for " + dimension);
        }

        assertEquals("type", AggregationDimension.TYPE.getExpression());
        assertEquals("coalesce(region, 'global')", AggregationDimension.REGION.getExpression());
        assertEquals("CASE WHEN deleted_at IS NOT NULL THEN 'DELETED' ELSE UPPER(coalesce(status, 'UNKNOWN')) END",
                AggregationDimension.STATUS.getExpression());
        assertEquals("CASE WHEN project_id IS NULL OR project_id = '' OR LOWER(project_id) = 'unknown' THEN 'Global / No Project' ELSE project_id END",
                AggregationDimension.PROJECT.getExpression());
    }

    @Test
    public void testValueOf() {
        assertEquals(AggregationDimension.TYPE, AggregationDimension.valueOf("TYPE"));
        assertEquals(AggregationDimension.REGION, AggregationDimension.valueOf("REGION"));
        assertEquals(AggregationDimension.STATUS, AggregationDimension.valueOf("STATUS"));
        assertEquals(AggregationDimension.PROJECT, AggregationDimension.valueOf("PROJECT"));
    }
}
