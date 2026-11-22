BEGIN;

DELETE FROM lingxi_core.sys_role_menu
WHERE menu_id = (SELECT id FROM lingxi_core.sys_menu WHERE code = 'EVALUATION_CENTER');

DELETE FROM lingxi_core.sys_menu WHERE code = 'EVALUATION_CENTER';

COMMIT;
