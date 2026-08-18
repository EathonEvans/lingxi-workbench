BEGIN;

-- 普通枚举由Java稳定code和统一TypeHandler维护，数据库不重复保存枚举白名单。
ALTER TABLE lingxi_core.ai_skill
    DROP CONSTRAINT IF EXISTS ck_ai_skill_source,
    DROP CONSTRAINT IF EXISTS ck_ai_skill_visibility,
    DROP CONSTRAINT IF EXISTS ck_ai_skill_status,
    DROP CONSTRAINT IF EXISTS ck_ai_skill_security;

ALTER TABLE lingxi_core.ai_skill_draft
    DROP CONSTRAINT IF EXISTS ck_ai_skill_draft_activation;

COMMIT;
