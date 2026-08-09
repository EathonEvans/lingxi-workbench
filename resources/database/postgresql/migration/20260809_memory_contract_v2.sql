BEGIN;

-- 补齐上下文工程运行时读取的会话摘要游标。
ALTER TABLE lingxi_core.ai_conversation
    ADD COLUMN IF NOT EXISTS summary_sequence_no INTEGER;
COMMENT ON COLUMN lingxi_core.ai_conversation.summary_sequence_no IS '摘要覆盖到的最后用户消息序号';

-- 旧开发库已经存在记忆表时，CREATE TABLE IF NOT EXISTS 不会补充后续新增字段。
ALTER TABLE lingxi_core.ai_user_profile
    ADD COLUMN IF NOT EXISTS source_message_id BIGINT;
COMMENT ON COLUMN lingxi_core.ai_user_profile.source_message_id IS '画像来源消息逻辑外键';

ALTER TABLE lingxi_core.ai_interaction_record
    ADD COLUMN IF NOT EXISTS assistant_message_id BIGINT;

ALTER TABLE lingxi_vector.interaction_record_vector
    ADD COLUMN IF NOT EXISTS embedding_model_instance_id BIGINT,
    ADD COLUMN IF NOT EXISTS embedding_dimension INTEGER;

-- 新项目不迁就无法证明来源的旧记忆数据，存在待回填记录时终止迁移并人工处理。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM lingxi_core.ai_interaction_record
        WHERE assistant_message_id IS NULL
    ) THEN
        RAISE EXCEPTION 'ai_interaction_record 存在无法回填 assistant_message_id 的旧数据';
    END IF;
    IF EXISTS (
        SELECT 1 FROM lingxi_vector.interaction_record_vector
        WHERE embedding_model_instance_id IS NULL OR embedding_dimension IS NULL
    ) THEN
        RAISE EXCEPTION 'interaction_record_vector 存在无法回填 Embedding 契约的旧数据';
    END IF;
END
$$;

ALTER TABLE lingxi_core.ai_interaction_record
    ALTER COLUMN assistant_message_id SET NOT NULL;
ALTER TABLE lingxi_vector.interaction_record_vector
    ALTER COLUMN embedding_model_instance_id SET NOT NULL,
    ALTER COLUMN embedding_dimension SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_interaction_record_assistant
    ON lingxi_core.ai_interaction_record (assistant_message_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_irv_record
    ON lingxi_vector.interaction_record_vector (record_id);
CREATE INDEX IF NOT EXISTS idx_irv_embedding_contract
    ON lingxi_vector.interaction_record_vector
    (embedding_model_instance_id, embedding_dimension, record_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_irv_embedding_dimension'
          AND conrelid = 'lingxi_vector.interaction_record_vector'::regclass
    ) THEN
        ALTER TABLE lingxi_vector.interaction_record_vector
            ADD CONSTRAINT ck_irv_embedding_dimension CHECK (embedding_dimension > 0);
    END IF;
END
$$;

COMMENT ON COLUMN lingxi_core.ai_interaction_record.assistant_message_id IS 'Assistant 消息逻辑外键，同时作为记忆后处理幂等键';
COMMENT ON COLUMN lingxi_vector.interaction_record_vector.record_id IS '交互记录逻辑外键，由服务校验和维护生命周期';

COMMIT;
