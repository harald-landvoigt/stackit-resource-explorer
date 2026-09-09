-- Clean up stale duplicate storage resources created prior to region-scoped UUID hashing
DELETE FROM stackit_resources sr_old
WHERE sr_old.type = 'storage'
  AND EXISTS (
    SELECT 1
    FROM stackit_resources sr_new
    WHERE sr_new.type = sr_old.type
      AND sr_new.project_id = sr_old.project_id
      AND sr_new.resource_id = sr_old.resource_id
      AND (
        sr_new.updated_at > sr_old.updated_at
        OR (sr_new.updated_at = sr_old.updated_at AND sr_new.created_at > sr_old.created_at)
        OR (sr_new.updated_at = sr_old.updated_at AND sr_new.created_at = sr_old.created_at AND sr_new.id > sr_old.id)
      )
  );

-- Create unique index to guarantee no active duplicates exist per (type, project_id, resource_id)
CREATE UNIQUE INDEX IF NOT EXISTS uq_stackit_resources_active
    ON stackit_resources(type, project_id, resource_id)
    WHERE deleted_at IS NULL;
