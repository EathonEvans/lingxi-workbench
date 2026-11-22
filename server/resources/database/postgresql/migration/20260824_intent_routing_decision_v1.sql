BEGIN;

-- 每次路由只追加一条安全决策事实，不保存用户原文、Prompt、历史、槽位值或模型推理。
CREATE TABLE lingxi_core.ai_intent_routing_decision
(
    id                          BIGINT       PRIMARY KEY,
    conversation_id             BIGINT       NOT NULL,
    user_id                     BIGINT       NOT NULL,
    user_message_id             BIGINT       NOT NULL,
    assistant_message_id        BIGINT       NOT NULL,
    application_id              BIGINT       NOT NULL,
    application_version_id      BIGINT       NOT NULL,
    intent_tree_id              BIGINT,
    intent_tree_version_id      BIGINT,
    embedding_model_instance_id BIGINT,
    chat_model_instance_id      BIGINT,
    prompt_version_id           BIGINT,
    action                      VARCHAR(32)  NOT NULL,
    reason                      VARCHAR(48)  NOT NULL,
    selected_intent_code        VARCHAR(64),
    capability_code             VARCHAR(64),
    candidate_evidence          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    semantic_decision           JSONB,
    input_tokens                INTEGER,
    output_tokens               INTEGER,
    total_tokens                INTEGER,
    model_calls                 INTEGER      NOT NULL DEFAULT 0,
    retry_count                 INTEGER      NOT NULL DEFAULT 0,
    duration_millis             BIGINT       NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_intent_routing_decision_tokens CHECK (
        (input_tokens IS NULL OR input_tokens >= 0)
            AND (output_tokens IS NULL OR output_tokens >= 0)
            AND (total_tokens IS NULL OR total_tokens >= 0)),
    CONSTRAINT ck_intent_routing_decision_calls CHECK (
        model_calls >= 0 AND retry_count >= 0 AND duration_millis >= 0)
);

CREATE INDEX idx_intent_routing_decision_conversation
    ON lingxi_core.ai_intent_routing_decision (conversation_id, created_at DESC);
CREATE INDEX idx_intent_routing_decision_version
    ON lingxi_core.ai_intent_routing_decision (application_version_id, intent_tree_version_id, created_at DESC);
CREATE INDEX idx_intent_routing_decision_action
    ON lingxi_core.ai_intent_routing_decision (action, reason, created_at DESC);

COMMIT;
