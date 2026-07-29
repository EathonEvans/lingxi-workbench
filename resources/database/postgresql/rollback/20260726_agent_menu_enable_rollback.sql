BEGIN;

UPDATE lingxi_core.sys_menu
   SET visible = FALSE
 WHERE code IN ('AGENT', 'AGENT_LIST');

COMMIT;
