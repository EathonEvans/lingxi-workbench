BEGIN;

UPDATE lingxi_core.sys_menu
SET parent_id = NULL,
    name = '工具管理',
    route_path = '/tool',
    component = 'Layout',
    icon = 'ri:tools-line',
    sort_order = 85,
    visible = TRUE,
    status = 'ACTIVE'
WHERE code = 'TOOL';

UPDATE lingxi_core.sys_menu tool_menu
SET parent_id = tool_directory.id,
    name = 'Tool列表',
    route_path = '/tool/list',
    component = 'tool/index',
    icon = 'ri:function-line',
    sort_order = 10,
    visible = TRUE,
    status = 'ACTIVE'
FROM lingxi_core.sys_menu tool_directory
WHERE tool_menu.code = 'TOOL_LIST'
  AND tool_directory.code = 'TOOL';

COMMIT;
