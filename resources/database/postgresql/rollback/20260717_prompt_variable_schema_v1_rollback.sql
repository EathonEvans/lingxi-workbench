-- Prompt变量Schema V1迁移回滚。
-- 注意：回滚会覆盖迁移后对已备份Prompt版本的正文和变量Schema修改。

BEGIN;

UPDATE lingxi_core.ai_prompt_version version
SET prompt_content = backup.prompt_content,
    variables = backup.variables
FROM lingxi_audit.prompt_variable_schema_backup_20260717 backup
WHERE version.id = backup.prompt_version_id;

DROP TABLE IF EXISTS lingxi_audit.prompt_variable_schema_backup_20260717;

COMMIT;
