DROP TABLE IF EXISTS lingxi_core.ai_evaluation_run;

DELETE FROM lingxi_core.sys_role_menu
WHERE menu_id IN (
    SELECT id FROM lingxi_core.sys_menu
    WHERE code IN ('EVALUATION_MANAGE', 'EVALUATION_EXECUTE')
);

DELETE FROM lingxi_core.sys_menu
WHERE code IN ('EVALUATION_MANAGE', 'EVALUATION_EXECUTE');

UPDATE lingxi_core.sys_menu
SET permission_code = NULL
WHERE code = 'EVALUATION_CENTER';
