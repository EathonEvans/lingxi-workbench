-- Prompt变量Schema V1迁移。
-- 旧版无命名空间变量统一迁移到input作用域，并补齐变量元数据。

BEGIN;

CREATE TABLE IF NOT EXISTS lingxi_audit.prompt_variable_schema_backup_20260717
(
    prompt_version_id BIGINT PRIMARY KEY,
    prompt_content    TEXT  NOT NULL,
    variables         JSONB NOT NULL,
    backed_up_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO lingxi_audit.prompt_variable_schema_backup_20260717
    (prompt_version_id, prompt_content, variables)
SELECT id, prompt_content, variables
FROM lingxi_core.ai_prompt_version
WHERE EXISTS (
    SELECT 1
    FROM JSONB_ARRAY_ELEMENTS(variables) item
    WHERE item ->> 'type' IS NULL
       OR item ->> 'name' NOT LIKE '%.%'
)
ON CONFLICT DO NOTHING;

-- 先替换模板中的旧变量引用，变量名只允许字母、数字和下划线，不存在正则转义风险。
DO $$
DECLARE
    variable_record RECORD;
BEGIN
    FOR variable_record IN
        SELECT DISTINCT version.id AS version_id, item ->> 'name' AS old_name
        FROM lingxi_core.ai_prompt_version version,
             JSONB_ARRAY_ELEMENTS(version.variables) item
        WHERE item ->> 'name' NOT LIKE '%.%'
    LOOP
        UPDATE lingxi_core.ai_prompt_version
        SET prompt_content = REGEXP_REPLACE(
                prompt_content,
                '\{\{\s*' || variable_record.old_name || '\s*\}\}',
                '{{input.' || variable_record.old_name || '}}',
                'g'
            )
        WHERE id = variable_record.version_id;
    END LOOP;
END
$$;

UPDATE lingxi_core.ai_prompt_version version
SET variables = migrated.variables
FROM (
    SELECT source.id,
           JSONB_AGG(
               CASE
                   WHEN item.element ->> 'type' IS NOT NULL
                        AND item.element ->> 'name' LIKE '%.%'
                       THEN item.element
                   ELSE JSONB_BUILD_OBJECT(
                       'name', 'input.' || (item.element ->> 'name'),
                       'displayName', COALESCE(
                           NULLIF(item.element ->> 'description', ''),
                           item.element ->> 'name'
                       ),
                       'type', 'STRING',
                       'scope', 'INPUT',
                       'source', 'USER_INPUT',
                       'required', COALESCE((item.element ->> 'required')::BOOLEAN, FALSE),
                       'defaultValue', NULLIF(item.element ->> 'defaultValue', ''),
                       'description', item.element ->> 'description',
                       'example', NULL,
                       'maxLength', 2000,
                       'maxItems', NULL,
                       'priority', 50,
                       'overflowStrategy', 'REJECT',
                       'sensitive', FALSE,
                       'userEditable', TRUE
                   )
               END
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
      WHERE item ->> 'type' IS NULL
         OR item ->> 'name' NOT LIKE '%.%'
  );

COMMIT;
