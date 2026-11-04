-- Migration V1.2.1: Synchronize status and data.status/state for deleted resources
UPDATE stackit_resources
SET status = 'DELETED',
    data = CASE 
        WHEN data IS NOT NULL AND data ? 'status' AND data ? 'state' THEN 
            jsonb_set(jsonb_set(data, '{status}', '"deleted"', false), '{state}', '"deleted"', false)
        WHEN data IS NOT NULL AND data ? 'status' THEN 
            jsonb_set(data, '{status}', '"deleted"', false)
        WHEN data IS NOT NULL AND data ? 'state' THEN 
            jsonb_set(data, '{state}', '"deleted"', false)
        ELSE data
    END
WHERE deleted_at IS NOT NULL
  AND (status != 'DELETED' OR (data IS NOT NULL AND ((data ? 'status' AND data->>'status' != 'deleted') OR (data ? 'state' AND data->>'state' != 'deleted'))));
