-- Migration V1.2.2: Normalize region names to canonical STACKIT region IDs (global, eu01, eu02)
UPDATE stackit_resources
SET region = 'eu01'
WHERE LOWER(region) IN ('eu01-1', 'eu01-2', 'eu01-3', 'eu-central-1', 'eu-01')
   OR LOWER(region) LIKE 'eu01-%';

UPDATE stackit_resources
SET region = 'eu02'
WHERE LOWER(region) IN ('eu02-1', 'eu02-2', 'eu-02')
   OR LOWER(region) LIKE 'eu02-%';

UPDATE stackit_resources
SET region = 'global'
WHERE region IS NULL OR LOWER(region) IN ('', 'unknown', 'global');
