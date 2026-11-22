BEGIN;

-- Application V13 规定运行端只接受 Admin 已完整解析的发布快照；可编辑草稿保留可选配置的省略能力。
ALTER TABLE lingxi_core.ai_application_draft
    ALTER COLUMN schema_version SET DEFAULT 13,
    DROP CONSTRAINT IF EXISTS ck_ai_application_draft_schema;

UPDATE lingxi_core.ai_application_draft
SET schema_version = 13,
    definition = jsonb_set(definition, '{schemaVersion}', '13'::jsonb, TRUE);

ALTER TABLE lingxi_core.ai_application_draft
    ADD CONSTRAINT ck_ai_application_draft_schema CHECK (schema_version = 13);

-- Application 归属领域是发布引用校验的前置条件；不为历史脏数据静默补默认值。
DO
$$
BEGIN
    IF EXISTS (SELECT 1 FROM lingxi_core.ai_application WHERE domain_id IS NULL) THEN
        RAISE EXCEPTION 'ai_application.domain_id 存在空值，必须先补齐所属一级领域后再执行迁移';
    END IF;
END
$$;

ALTER TABLE lingxi_core.ai_application
    ALTER COLUMN domain_id SET NOT NULL,
    DROP CONSTRAINT IF EXISTS ck_ai_application_domain_id,
    ADD CONSTRAINT ck_ai_application_domain_id CHECK (domain_id > 0);

COMMENT ON COLUMN lingxi_core.ai_application.domain_id IS
    'Application 所属一级领域逻辑外键；发布时必须校验领域和冻结引用归属，不使用物理外键';

COMMIT;
