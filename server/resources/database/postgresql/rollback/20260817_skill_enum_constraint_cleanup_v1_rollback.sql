BEGIN;

ALTER TABLE lingxi_core.ai_skill
    ADD CONSTRAINT ck_ai_skill_source CHECK (
        source_type IN ('SYSTEM_PRESET', 'USER_CREATED', 'THIRD_PARTY_IMPORTED')
    ),
    ADD CONSTRAINT ck_ai_skill_visibility CHECK (visibility IN ('PRIVATE', 'PROJECT', 'SYSTEM')),
    ADD CONSTRAINT ck_ai_skill_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    ADD CONSTRAINT ck_ai_skill_security CHECK (
        security_status IN ('NORMAL', 'QUARANTINED', 'REVOKED')
    );

ALTER TABLE lingxi_core.ai_skill_draft
    ADD CONSTRAINT ck_ai_skill_draft_activation CHECK (
        activation_mode IN ('EXPLICIT_ONLY', 'MODEL_ROUTED')
    );

COMMIT;
