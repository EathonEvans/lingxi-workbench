BEGIN;

-- Tool 是 Agent 可装配的能力，因此在导航结构中与 Agent 列表同属 Agent 管理。
-- 直接挂载页面菜单，避免二级 DIRECTORY 再使用 Layout 产生嵌套路由冲突。
UPDATE lingxi_core.sys_menu tool_menu
SET parent_id = agent_directory.id,
    name = 'Tool管理',
    route_path = '/agent/tool',
    component = 'tool/index',
    icon = 'ri:tools-line',
    sort_order = 20,
    visible = TRUE,
    status = 'ACTIVE'
FROM lingxi_core.sys_menu agent_directory
WHERE tool_menu.code = 'TOOL_LIST'
  AND agent_directory.code = 'AGENT';

-- 原工具目录的页面已经迁入 Agent 管理，保留记录便于回滚，但不再参与导航。
UPDATE lingxi_core.sys_menu
SET visible = FALSE,
    status = 'DISABLED'
WHERE code = 'TOOL';

COMMIT;
