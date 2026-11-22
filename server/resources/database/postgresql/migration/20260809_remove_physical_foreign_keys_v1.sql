BEGIN;

-- 关联完整性统一由服务层校验；数据库仅保留逻辑引用字段和查询索引。
ALTER TABLE lingxi_core.sys_department
    DROP CONSTRAINT IF EXISTS fk_sys_department_parent;

ALTER TABLE lingxi_core.sys_user
    DROP CONSTRAINT IF EXISTS fk_sys_user_department;

ALTER TABLE lingxi_core.sys_user_application_access
    DROP CONSTRAINT IF EXISTS fk_sys_user_application_access_user,
    DROP CONSTRAINT IF EXISTS fk_sys_user_application_access_operator;

ALTER TABLE lingxi_audit.operation_log
    DROP CONSTRAINT IF EXISTS fk_operation_log_operator;

ALTER TABLE lingxi_core.sys_menu
    DROP CONSTRAINT IF EXISTS fk_sys_menu_parent;

ALTER TABLE lingxi_core.sys_user_role
    DROP CONSTRAINT IF EXISTS fk_sys_user_role_user,
    DROP CONSTRAINT IF EXISTS fk_sys_user_role_role;

ALTER TABLE lingxi_core.sys_role_menu
    DROP CONSTRAINT IF EXISTS fk_sys_role_menu_role,
    DROP CONSTRAINT IF EXISTS fk_sys_role_menu_menu;

ALTER TABLE lingxi_core.sys_role_department
    DROP CONSTRAINT IF EXISTS fk_sys_role_department_role,
    DROP CONSTRAINT IF EXISTS fk_sys_role_department_department;

ALTER TABLE lingxi_core.ai_model_credential
    DROP CONSTRAINT IF EXISTS fk_ai_model_credential_provider;

ALTER TABLE lingxi_core.ai_model_instance
    DROP CONSTRAINT IF EXISTS fk_ai_model_instance_provider,
    DROP CONSTRAINT IF EXISTS fk_ai_model_instance_credential;

ALTER TABLE lingxi_core.ai_prompt_version
    DROP CONSTRAINT IF EXISTS fk_ai_prompt_version_prompt;

ALTER TABLE lingxi_core.ai_prompt
    DROP CONSTRAINT IF EXISTS fk_ai_prompt_published_version;

ALTER TABLE lingxi_core.ai_workflow
    DROP CONSTRAINT IF EXISTS fk_ai_workflow_published_version;

ALTER TABLE lingxi_core.ai_workflow_draft
    DROP CONSTRAINT IF EXISTS fk_ai_workflow_draft_workflow,
    DROP CONSTRAINT IF EXISTS fk_ai_workflow_draft_base_version;

ALTER TABLE lingxi_core.ai_workflow_version
    DROP CONSTRAINT IF EXISTS fk_ai_workflow_version_workflow;

ALTER TABLE lingxi_core.ai_conversation
    DROP CONSTRAINT IF EXISTS fk_ai_conversation_user;

ALTER TABLE lingxi_core.ai_conversation_message
    DROP CONSTRAINT IF EXISTS fk_ai_conversation_message_conversation,
    DROP CONSTRAINT IF EXISTS fk_ai_conversation_message_model;

CREATE INDEX IF NOT EXISTS idx_sys_user_application_access_operator
    ON lingxi_core.sys_user_application_access (updated_by)
    WHERE updated_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ai_model_instance_provider
    ON lingxi_core.ai_model_instance (provider_id);
CREATE INDEX IF NOT EXISTS idx_ai_model_instance_credential
    ON lingxi_core.ai_model_instance (credential_id);
CREATE INDEX IF NOT EXISTS idx_ai_prompt_published_version
    ON lingxi_core.ai_prompt (published_version_id)
    WHERE published_version_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ai_workflow_published_version
    ON lingxi_core.ai_workflow (published_version_id)
    WHERE published_version_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ai_workflow_draft_base_version
    ON lingxi_core.ai_workflow_draft (base_version_id)
    WHERE base_version_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ai_conversation_message_model
    ON lingxi_core.ai_conversation_message (model_instance_id)
    WHERE model_instance_id IS NOT NULL;

COMMIT;
