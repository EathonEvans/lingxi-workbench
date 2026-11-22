BEGIN;

DELETE FROM lingxi_core.sys_role_menu
 WHERE menu_id IN (
     SELECT id FROM lingxi_core.sys_menu
      WHERE code IN ('AGENT_MANAGE', 'AGENT_PUBLISH', 'AGENT_LIST', 'AGENT')
 );
DELETE FROM lingxi_core.sys_menu
 WHERE code IN ('AGENT_MANAGE', 'AGENT_PUBLISH');
DELETE FROM lingxi_core.sys_menu
 WHERE code = 'AGENT_LIST';
DELETE FROM lingxi_core.sys_menu
 WHERE code = 'AGENT';

ALTER TABLE IF EXISTS lingxi_core.ai_agent
    DROP CONSTRAINT IF EXISTS fk_ai_agent_published_version;
ALTER TABLE IF EXISTS lingxi_core.ai_agent_draft
    DROP CONSTRAINT IF EXISTS fk_ai_agent_draft_base_version;

DROP TABLE IF EXISTS lingxi_core.ai_agent_version_knowledge;
DROP TABLE IF EXISTS lingxi_core.ai_agent_version;
DROP TABLE IF EXISTS lingxi_core.ai_agent_draft_knowledge;
DROP TABLE IF EXISTS lingxi_core.ai_agent_draft;
DROP TABLE IF EXISTS lingxi_core.ai_agent;

COMMIT;
