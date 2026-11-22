ALTER TABLE lingxi_core.ai_model_instance
    DROP CONSTRAINT IF EXISTS ck_ai_model_instance_extension_parameters;

ALTER TABLE lingxi_core.ai_model_instance
    DROP COLUMN IF EXISTS extension_parameters;
