BEGIN;

DELETE FROM lingxi_core.sys_menu
WHERE code IN ('SKILL_MANAGE', 'SKILL_PUBLISH', 'SKILL_REVOKE');
DELETE FROM lingxi_core.sys_menu WHERE code = 'SKILL_LIST';

DROP TABLE IF EXISTS lingxi_core.ai_skill_version;
DROP TABLE IF EXISTS lingxi_core.ai_skill_draft;
DROP TABLE IF EXISTS lingxi_core.ai_skill;

COMMIT;
