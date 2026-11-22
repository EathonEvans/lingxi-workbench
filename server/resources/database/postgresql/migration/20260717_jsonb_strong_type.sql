-- PostgreSQL JSONB强类型映射迁移。
-- 执行范围：已经初始化的数据库；全量备份完成后执行。
-- 本脚本清理旧版无效JSON结构，并增加与Java强类型字段一致的约束。

BEGIN;

-- 保存被清理数据的原始值，确保回滚时能够精确恢复。
CREATE TABLE IF NOT EXISTS lingxi_audit.jsonb_mapping_backup_20260717
(
    table_name    VARCHAR(128) NOT NULL,
    record_id     BIGINT       NOT NULL,
    column_name   VARCHAR(128) NOT NULL,
    original_value JSONB,
    backed_up_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (table_name, record_id, column_name)
);

INSERT INTO lingxi_audit.jsonb_mapping_backup_20260717
    (table_name, record_id, column_name, original_value)
SELECT 'lingxi_core.ai_model_instance', id, 'capabilities', capabilities
FROM lingxi_core.ai_model_instance
WHERE jsonb_typeof(capabilities) <> 'array'
   OR NOT capabilities <@ '["STREAMING","TOOL_CALLING","STRUCTURED_OUTPUT","VISION"]'::JSONB
ON CONFLICT DO NOTHING;

INSERT INTO lingxi_audit.jsonb_mapping_backup_20260717
    (table_name, record_id, column_name, original_value)
SELECT 'lingxi_core.ai_model_instance', id, 'stop_sequences', stop_sequences
FROM lingxi_core.ai_model_instance
WHERE jsonb_typeof(stop_sequences) <> 'array'
   OR jsonb_path_exists(stop_sequences, '$[*] ? (@.type() != "string")')
ON CONFLICT DO NOTHING;

INSERT INTO lingxi_audit.jsonb_mapping_backup_20260717
    (table_name, record_id, column_name, original_value)
SELECT 'lingxi_core.ai_prompt_version', id, 'variables', variables
FROM lingxi_core.ai_prompt_version
WHERE jsonb_typeof(variables) <> 'array'
   OR jsonb_path_exists(variables, '$[*] ? (@.type() != "object")')
ON CONFLICT DO NOTHING;

-- 旧版capabilities曾混入CHAT、EMBEDDING、RERANK等模型类型，只保留有效能力枚举。
UPDATE lingxi_core.ai_model_instance target
SET capabilities = (
    SELECT COALESCE(JSONB_AGG(item.element ORDER BY item.ordinal), '[]'::JSONB) AS value
    FROM JSONB_ARRAY_ELEMENTS(
        CASE
            WHEN jsonb_typeof(target.capabilities) = 'array' THEN target.capabilities
            ELSE '[]'::JSONB
        END
    ) WITH ORDINALITY AS item(element, ordinal)
    WHERE jsonb_typeof(item.element) = 'string'
      AND item.element #>> '{}' IN (
          'STREAMING', 'TOOL_CALLING', 'STRUCTURED_OUTPUT', 'VISION'
      )
)
WHERE jsonb_typeof(target.capabilities) <> 'array'
   OR NOT target.capabilities <@ '["STREAMING","TOOL_CALLING","STRUCTURED_OUTPUT","VISION"]'::JSONB;

-- 停止序列只允许字符串数组，尽可能保留历史数组中的合法字符串元素。
UPDATE lingxi_core.ai_model_instance target
SET stop_sequences = (
    SELECT COALESCE(JSONB_AGG(item.element ORDER BY item.ordinal), '[]'::JSONB) AS value
    FROM JSONB_ARRAY_ELEMENTS(
        CASE
            WHEN jsonb_typeof(target.stop_sequences) = 'array' THEN target.stop_sequences
            ELSE '[]'::JSONB
        END
    ) WITH ORDINALITY AS item(element, ordinal)
    WHERE jsonb_typeof(item.element) = 'string'
)
WHERE jsonb_typeof(target.stop_sequences) <> 'array'
   OR jsonb_path_exists(target.stop_sequences, '$[*] ? (@.type() != "string")');

-- Prompt变量只允许对象数组，尽可能保留历史数组中的合法对象元素。
UPDATE lingxi_core.ai_prompt_version target
SET variables = (
    SELECT COALESCE(JSONB_AGG(item.element ORDER BY item.ordinal), '[]'::JSONB) AS value
    FROM JSONB_ARRAY_ELEMENTS(
        CASE
            WHEN jsonb_typeof(target.variables) = 'array' THEN target.variables
            ELSE '[]'::JSONB
        END
    ) WITH ORDINALITY AS item(element, ordinal)
    WHERE jsonb_typeof(item.element) = 'object'
)
WHERE jsonb_typeof(target.variables) <> 'array'
   OR jsonb_path_exists(target.variables, '$[*] ? (@.type() != "object")');

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_ai_model_instance_capabilities_jsonb'
          AND conrelid = 'lingxi_core.ai_model_instance'::REGCLASS
    ) THEN
        ALTER TABLE lingxi_core.ai_model_instance
            ADD CONSTRAINT ck_ai_model_instance_capabilities_jsonb CHECK (
                jsonb_typeof(capabilities) = 'array'
                AND capabilities <@ '["STREAMING","TOOL_CALLING","STRUCTURED_OUTPUT","VISION"]'::JSONB
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_ai_model_instance_stop_sequences_jsonb'
          AND conrelid = 'lingxi_core.ai_model_instance'::REGCLASS
    ) THEN
        ALTER TABLE lingxi_core.ai_model_instance
            ADD CONSTRAINT ck_ai_model_instance_stop_sequences_jsonb CHECK (
                jsonb_typeof(stop_sequences) = 'array'
                AND NOT jsonb_path_exists(
                    stop_sequences, '$[*] ? (@.type() != "string")'
                )
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_ai_prompt_version_variables_jsonb'
          AND conrelid = 'lingxi_core.ai_prompt_version'::REGCLASS
    ) THEN
        ALTER TABLE lingxi_core.ai_prompt_version
            ADD CONSTRAINT ck_ai_prompt_version_variables_jsonb CHECK (
                jsonb_typeof(variables) = 'array'
                AND NOT jsonb_path_exists(variables, '$[*] ? (@.type() != "object")')
            );
    END IF;
END
$$;

COMMIT;
