BEGIN;

INSERT INTO lingxi_core.sys_menu
    (parent_id, name, code, menu_type, route_path, component, icon, permission_code, sort_order, visible, status)
VALUES (NULL, '应用管理', 'APPLICATION', 'DIRECTORY', '/application', 'Layout',
        'ri:apps-2-line', NULL, 90, TRUE, 'ACTIVE')
ON CONFLICT (code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name = EXCLUDED.name,
    menu_type = EXCLUDED.menu_type,
    route_path = EXCLUDED.route_path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    permission_code = EXCLUDED.permission_code,
    sort_order = EXCLUDED.sort_order,
    visible = TRUE,
    status = 'ACTIVE';

INSERT INTO lingxi_core.sys_menu
    (parent_id, name, code, menu_type, route_path, component, icon, permission_code, sort_order, visible, status)
SELECT parent.id, 'Application列表', 'APPLICATION_LIST', 'MENU', '/application/list',
       'application/index', 'ri:flow-chart', 'application:list', 10, TRUE, 'ACTIVE'
FROM lingxi_core.sys_menu parent
WHERE parent.code = 'APPLICATION'
ON CONFLICT (code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name = EXCLUDED.name,
    route_path = EXCLUDED.route_path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    permission_code = EXCLUDED.permission_code,
    visible = TRUE,
    status = 'ACTIVE';

INSERT INTO lingxi_core.sys_menu
    (parent_id, name, code, menu_type, permission_code, sort_order, visible, status)
SELECT parent.id, action.name, action.code, 'BUTTON', action.permission_code,
       action.sort_order, FALSE, 'ACTIVE'
FROM (VALUES
    ('Application维护', 'APPLICATION_MANAGE', 'application:manage', 10),
    ('Application发布', 'APPLICATION_PUBLISH', 'application:publish', 20)
) AS action(name, code, permission_code, sort_order)
JOIN lingxi_core.sys_menu parent ON parent.code = 'APPLICATION_LIST'
ON CONFLICT (code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name = EXCLUDED.name,
    permission_code = EXCLUDED.permission_code,
    sort_order = EXCLUDED.sort_order,
    visible = FALSE,
    status = 'ACTIVE';

COMMIT;
