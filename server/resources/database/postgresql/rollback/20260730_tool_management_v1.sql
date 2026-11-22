BEGIN;

-- 先删除叶子菜单，再删除父菜单，避免逻辑层级残留。
DELETE FROM lingxi_core.sys_menu
WHERE code IN ('TOOL_MANAGE', 'TOOL_LIST', 'TOOL');

DROP TABLE IF EXISTS lingxi_core.ai_tool;

COMMIT;
