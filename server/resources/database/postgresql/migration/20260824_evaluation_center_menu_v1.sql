BEGIN;

-- 评测中心与应用监控共享工作台信息架构；页面权限仍由评测接口上的Application权限控制。
INSERT INTO lingxi_core.sys_menu
    (parent_id, name, code, menu_type, route_path, component, icon, permission_code,
     sort_order, visible, status)
SELECT workbench.id, '评测中心', 'EVALUATION_CENTER', 'MENU', '/dashboard/evaluation',
       'dashboard/evaluation/index', 'ri:flask-line', NULL, 30, TRUE, 'ACTIVE'
FROM lingxi_core.sys_menu workbench
WHERE workbench.code = 'WORKBENCH'
ON CONFLICT (code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name = EXCLUDED.name,
    menu_type = EXCLUDED.menu_type,
    route_path = EXCLUDED.route_path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    permission_code = NULL,
    sort_order = EXCLUDED.sort_order,
    visible = TRUE,
    status = 'ACTIVE';

-- 已拥有应用监控的角色同步获得评测入口；ROOT由查询逻辑动态获得全部生效菜单。
INSERT INTO lingxi_core.sys_role_menu (role_id, menu_id)
SELECT monitor_role.role_id, evaluation.id
FROM lingxi_core.sys_role_menu monitor_role
JOIN lingxi_core.sys_menu monitor ON monitor.id = monitor_role.menu_id
JOIN lingxi_core.sys_menu evaluation ON evaluation.code = 'EVALUATION_CENTER'
WHERE monitor.code = 'APPLICATION_MONITOR'
ON CONFLICT DO NOTHING;

COMMIT;
