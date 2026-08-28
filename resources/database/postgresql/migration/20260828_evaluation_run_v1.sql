-- 评测运行是 Lingxi 的版本化业务事实；Langfuse 继续保存人工评分和可编辑数据集。
CREATE TABLE IF NOT EXISTS lingxi_core.ai_evaluation_run
(
    id                       BIGSERIAL PRIMARY KEY,
    name                     VARCHAR(120)             NOT NULL,
    application_id           BIGINT                   NOT NULL,
    application_version_id   BIGINT                   NOT NULL,
    dataset_name             VARCHAR(100)             NOT NULL,
    dataset_version          TIMESTAMPTZ              NOT NULL,
    status                   VARCHAR(32)              NOT NULL,
    execution_token          VARCHAR(36),
    sample_count             INTEGER                  NOT NULL,
    completed_count          INTEGER                  NOT NULL DEFAULT 0,
    passed_count             INTEGER                  NOT NULL DEFAULT 0,
    failed_count             INTEGER                  NOT NULL DEFAULT 0,
    production_gate_passed   BOOLEAN,
    dataset_snapshot         JSONB                    NOT NULL,
    report_snapshot          JSONB,
    error_message            VARCHAR(500),
    created_by               BIGINT                   NOT NULL,
    started_at               TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ              NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ              NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_evaluation_run_counts CHECK (
        sample_count > 0
        AND completed_count >= 0
        AND completed_count <= sample_count
        AND passed_count >= 0
        AND failed_count >= 0
        AND passed_count + failed_count <= completed_count
    ),
    CONSTRAINT ck_evaluation_run_execution_token CHECK (
        (status = 'RUNNING' AND execution_token IS NOT NULL)
        OR (status <> 'RUNNING' AND execution_token IS NULL)
    ),
    CONSTRAINT ck_evaluation_run_dataset_snapshot CHECK (jsonb_typeof(dataset_snapshot) = 'object'),
    CONSTRAINT ck_evaluation_run_report_snapshot CHECK (
        report_snapshot IS NULL OR jsonb_typeof(report_snapshot) = 'object'
    )
);

CREATE INDEX IF NOT EXISTS idx_evaluation_run_application_version
    ON lingxi_core.ai_evaluation_run (application_id, application_version_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_evaluation_run_dataset
    ON lingxi_core.ai_evaluation_run (dataset_name, dataset_version DESC);

CREATE INDEX IF NOT EXISTS idx_evaluation_run_status
    ON lingxi_core.ai_evaluation_run (status, created_at);

UPDATE lingxi_core.sys_menu
SET permission_code = 'evaluation:list'
WHERE code = 'EVALUATION_CENTER';

INSERT INTO lingxi_core.sys_menu
    (parent_id, name, code, menu_type, permission_code, sort_order, visible, status)
SELECT parent.id, action.name, action.code, 'BUTTON', action.permission_code,
       action.sort_order, FALSE, 'ACTIVE'
FROM (VALUES
    ('评测数据维护', 'EVALUATION_MANAGE', 'evaluation:manage', 10),
    ('执行评测任务', 'EVALUATION_EXECUTE', 'evaluation:execute', 20)
) AS action(name, code, permission_code, sort_order)
JOIN lingxi_core.sys_menu parent ON parent.code = 'EVALUATION_CENTER'
ON CONFLICT (code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name = EXCLUDED.name,
    permission_code = EXCLUDED.permission_code,
    sort_order = EXCLUDED.sort_order,
    visible = FALSE,
    status = 'ACTIVE';

-- 沿用现有Application职责分配迁移角色权限，但运行时只认独立evaluation权限。
INSERT INTO lingxi_core.sys_role_menu (role_id, menu_id)
SELECT source.role_id, target.id
FROM lingxi_core.sys_role_menu source
JOIN lingxi_core.sys_menu source_menu ON source_menu.id = source.menu_id
JOIN lingxi_core.sys_menu target ON target.code = 'EVALUATION_CENTER'
WHERE source_menu.permission_code = 'application:list'
ON CONFLICT DO NOTHING;

INSERT INTO lingxi_core.sys_role_menu (role_id, menu_id)
SELECT source.role_id, target.id
FROM lingxi_core.sys_role_menu source
JOIN lingxi_core.sys_menu source_menu ON source_menu.id = source.menu_id
JOIN lingxi_core.sys_menu target ON target.code = 'EVALUATION_MANAGE'
WHERE source_menu.permission_code = 'application:manage'
ON CONFLICT DO NOTHING;

INSERT INTO lingxi_core.sys_role_menu (role_id, menu_id)
SELECT source.role_id, target.id
FROM lingxi_core.sys_role_menu source
JOIN lingxi_core.sys_menu source_menu ON source_menu.id = source.menu_id
JOIN lingxi_core.sys_menu target ON target.code = 'EVALUATION_EXECUTE'
WHERE source_menu.permission_code = 'application:publish'
ON CONFLICT DO NOTHING;
