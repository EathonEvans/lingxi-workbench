BEGIN;

DROP INDEX IF EXISTS lingxi_vector.idx_irv_embedding_contract;
DROP INDEX IF EXISTS lingxi_vector.uk_irv_record;
DROP INDEX IF EXISTS lingxi_core.uk_ai_interaction_record_assistant;

ALTER TABLE lingxi_vector.interaction_record_vector
    DROP CONSTRAINT IF EXISTS ck_irv_embedding_dimension,
    DROP COLUMN IF EXISTS embedding_dimension,
    DROP COLUMN IF EXISTS embedding_model_instance_id;
ALTER TABLE lingxi_core.ai_interaction_record
    DROP COLUMN IF EXISTS assistant_message_id;
ALTER TABLE lingxi_core.ai_user_profile
    DROP COLUMN IF EXISTS source_message_id;
ALTER TABLE lingxi_core.ai_conversation
    DROP COLUMN IF EXISTS summary_sequence_no;

COMMIT;
