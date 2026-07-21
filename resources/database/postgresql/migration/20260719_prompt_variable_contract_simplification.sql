-- 精简Prompt变量契约。
-- 模板仅声明变量名称、类型、必填性、说明和测试示例；变量来源等运行时策略由Agent或工作流负责。

BEGIN;

CREATE TABLE IF NOT EXISTS lingxi_audit.prompt_variable_contract_backup_20260719
(
    prompt_version_id BIGINT PRIMARY KEY,
    variables         JSONB       NOT NULL,
    backed_up_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO lingxi_audit.prompt_variable_contract_backup_20260719
    (prompt_version_id, variables)
SELECT version.id, version.variables
FROM lingxi_core.ai_prompt_version version
WHERE EXISTS (
    SELECT 1
    FROM JSONB_ARRAY_ELEMENTS(version.variables) item
    WHERE item ?| ARRAY[
        'displayName', 'scope', 'source', 'defaultValue', 'maxLength', 'maxItems',
        'priority', 'overflowStrategy', 'sensitive', 'userEditable'
    ]
)
ON CONFLICT DO NOTHING;

UPDATE lingxi_core.ai_prompt_version version
SET variables = migrated.variables
FROM (
    SELECT source.id,
           JSONB_AGG(
               item.element - ARRAY[
                   'displayName', 'scope', 'source', 'defaultValue', 'maxLength', 'maxItems',
                   'priority', 'overflowStrategy', 'sensitive', 'userEditable'
               ]
               ORDER BY item.ordinal
           ) AS variables
    FROM lingxi_core.ai_prompt_version source,
         JSONB_ARRAY_ELEMENTS(source.variables) WITH ORDINALITY item(element, ordinal)
    GROUP BY source.id
) migrated
WHERE version.id = migrated.id
  AND EXISTS (
      SELECT 1
      FROM JSONB_ARRAY_ELEMENTS(version.variables) item
      WHERE item ?| ARRAY[
          'displayName', 'scope', 'source', 'defaultValue', 'maxLength', 'maxItems',
          'priority', 'overflowStrategy', 'sensitive', 'userEditable'
      ]
  );

COMMIT;
