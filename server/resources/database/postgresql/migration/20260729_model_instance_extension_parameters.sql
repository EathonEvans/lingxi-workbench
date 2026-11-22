-- 模型实例扩展参数：承载 OpenAI 兼容供应商的私有请求体字段。
-- 该字段不建立物理外键，运行时由模型实例配置直接读取。
ALTER TABLE lingxi_core.ai_model_instance
    ADD COLUMN IF NOT EXISTS extension_parameters JSONB NOT NULL DEFAULT '{}'::JSONB;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_ai_model_instance_extension_parameters'
          AND conrelid = 'lingxi_core.ai_model_instance'::REGCLASS
    ) THEN
        ALTER TABLE lingxi_core.ai_model_instance
            ADD CONSTRAINT ck_ai_model_instance_extension_parameters
                CHECK (jsonb_typeof(extension_parameters) = 'object');
    END IF;
END
$$;

COMMENT ON COLUMN lingxi_core.ai_model_instance.extension_parameters
    IS 'OpenAI兼容供应商的Chat请求体扩展参数，仅允许JSON对象';
