-- 20260717 JSONB 强类型映射迁移回滚。
-- 注意：回滚会恢复迁移时备份的异常数据，覆盖这些记录迁移后对相同字段的修改。

BEGIN;

ALTER TABLE lingxi_core.ai_model_instance
    DROP CONSTRAINT IF EXISTS ck_ai_model_instance_capabilities_jsonb;
ALTER TABLE lingxi_core.ai_model_instance
    DROP CONSTRAINT IF EXISTS ck_ai_model_instance_stop_sequences_jsonb;
ALTER TABLE lingxi_core.ai_prompt_version
    DROP CONSTRAINT IF EXISTS ck_ai_prompt_version_variables_jsonb;

UPDATE lingxi_core.ai_model_instance target
SET capabilities = backup.original_value
FROM lingxi_audit.jsonb_mapping_backup_20260717 backup
WHERE backup.table_name = 'lingxi_core.ai_model_instance'
  AND backup.column_name = 'capabilities'
  AND backup.record_id = target.id;

UPDATE lingxi_core.ai_model_instance target
SET stop_sequences = backup.original_value
FROM lingxi_audit.jsonb_mapping_backup_20260717 backup
WHERE backup.table_name = 'lingxi_core.ai_model_instance'
  AND backup.column_name = 'stop_sequences'
  AND backup.record_id = target.id;

UPDATE lingxi_core.ai_prompt_version target
SET variables = backup.original_value
FROM lingxi_audit.jsonb_mapping_backup_20260717 backup
WHERE backup.table_name = 'lingxi_core.ai_prompt_version'
  AND backup.column_name = 'variables'
  AND backup.record_id = target.id;

DROP TABLE IF EXISTS lingxi_audit.jsonb_mapping_backup_20260717;

COMMIT;
