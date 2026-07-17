-- 灵析AI Workbench第一版初始化数据脚本。
-- 本脚本提供第一次启动所需的最小管理端数据集。
-- root初始密码为Root@123456，仅用于本地初始化，首次登录后必须立即修改。

BEGIN;

INSERT INTO lingxi_core.sys_department (name, code, sort_order, status)
VALUES ('灵析科技', 'ROOT', 0, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO lingxi_core.sys_role (name, code, data_scope, sort_order, built_in, status, remark)
VALUES ('超级管理员', 'ROOT', 'ALL', 0, TRUE, 'ACTIVE', '系统内置最高权限角色')
ON CONFLICT (code) DO UPDATE
SET data_scope = 'ALL',
    built_in   = TRUE,
    status     = 'ACTIVE';

INSERT INTO lingxi_core.sys_user AS existing
    (username, password_hash, display_name, email, mobile, status, department_id)
SELECT 'root',
       '$2y$12$4J1CTB3AmKq6XvleSMHSWuQ1lRNPxSw/k62HhGkVdCmcE4HRJXGUC',
       '超级管理员',
       NULL,
       NULL,
       'ACTIVE',
       department.id
FROM lingxi_core.sys_department department
WHERE department.code = 'ROOT'
ON CONFLICT (username) DO UPDATE
SET department_id = COALESCE(existing.department_id, EXCLUDED.department_id);

INSERT INTO lingxi_core.sys_user_application_access (user_id, enabled)
SELECT id, FALSE FROM lingxi_core.sys_user WHERE username = 'root'
ON CONFLICT (user_id) DO NOTHING;

-- 工作台作为一级目录，主键由数据库自增生成。
INSERT INTO lingxi_core.sys_menu
    (parent_id, name, code, menu_type, route_path, component, icon, sort_order, visible, status)
VALUES (NULL, '工作台', 'WORKBENCH', 'DIRECTORY', '/dashboard', 'Layout',
        'ri:home-smile-2-line', 10, TRUE, 'ACTIVE')
ON CONFLICT (code) DO UPDATE
SET parent_id  = NULL,
    name       = EXCLUDED.name,
    menu_type  = EXCLUDED.menu_type,
    route_path = EXCLUDED.route_path,
    component  = EXCLUDED.component,
    icon       = EXCLUDED.icon,
    permission_code = NULL,
    sort_order = EXCLUDED.sort_order,
    visible    = TRUE,
    status     = 'ACTIVE';

-- 导航页沿用原仪表盘的快捷入口样式，主键同样由数据库自增生成。
INSERT INTO lingxi_core.sys_menu
    (parent_id, name, code, menu_type, route_path, component, icon, sort_order, visible, status)
SELECT workbench.id, '导航页', 'NAVIGATION', 'MENU', '/dashboard/navigation',
       'dashboard/navigation/index', 'ri:compass-3-line', 10, TRUE, 'ACTIVE'
FROM lingxi_core.sys_menu workbench
WHERE workbench.code = 'WORKBENCH'
ON CONFLICT (code) DO UPDATE
SET parent_id  = EXCLUDED.parent_id,
    name       = EXCLUDED.name,
    menu_type  = EXCLUDED.menu_type,
    route_path = EXCLUDED.route_path,
    component  = EXCLUDED.component,
    icon       = EXCLUDED.icon,
    permission_code = NULL,
    sort_order = EXCLUDED.sort_order,
    visible    = TRUE,
    status     = 'ACTIVE';

-- 保留既有DASHBOARD菜单主键，仅将其调整为工作台目录下的仪表盘页面。
INSERT INTO lingxi_core.sys_menu
    (parent_id, name, code, menu_type, route_path, component, icon, sort_order, visible, status)
SELECT workbench.id, '仪表盘', 'DASHBOARD', 'MENU', '/dashboard/console',
       'dashboard/console/index', 'ri:pie-chart-line', 20, TRUE, 'ACTIVE'
FROM lingxi_core.sys_menu workbench
WHERE workbench.code = 'WORKBENCH'
ON CONFLICT (code) DO UPDATE
SET parent_id  = EXCLUDED.parent_id,
    name       = EXCLUDED.name,
    menu_type  = EXCLUDED.menu_type,
    route_path = EXCLUDED.route_path,
    component  = EXCLUDED.component,
    icon       = EXCLUDED.icon,
    permission_code = NULL,
    sort_order = EXCLUDED.sort_order,
    visible    = TRUE,
    status     = 'ACTIVE';

-- 兼容尚未落库的早期DASHBOARD_CONSOLE设计：权限迁回现有DASHBOARD主键后删除冗余记录。
INSERT INTO lingxi_core.sys_role_menu (role_id, menu_id)
SELECT role_menu.role_id, dashboard.id
FROM lingxi_core.sys_role_menu role_menu
JOIN lingxi_core.sys_menu legacy_page
  ON legacy_page.id = role_menu.menu_id
 AND legacy_page.code = 'DASHBOARD_CONSOLE'
JOIN lingxi_core.sys_menu dashboard
  ON dashboard.code = 'DASHBOARD'
ON CONFLICT DO NOTHING;

DELETE FROM lingxi_core.sys_role_menu
WHERE menu_id = (SELECT id FROM lingxi_core.sys_menu WHERE code = 'DASHBOARD_CONSOLE');

DELETE FROM lingxi_core.sys_menu
WHERE code = 'DASHBOARD_CONSOLE';

-- 系统管理根目录。
INSERT INTO lingxi_core.sys_menu
    (parent_id, name, code, menu_type, route_path, component, icon, sort_order, visible, status)
VALUES (NULL, '系统管理', 'SYSTEM', 'DIRECTORY', '/system', 'Layout', 'ri:settings-3-line', 100, TRUE, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- 四个管理页面菜单，路由和组件标识由后端统一下发。
INSERT INTO lingxi_core.sys_menu
    (parent_id, name, code, menu_type, route_path, component, icon, permission_code, sort_order, visible, status)
SELECT root.id, menu.name, menu.code, 'MENU', menu.route_path, menu.component, menu.icon,
       menu.permission_code, menu.sort_order, TRUE, 'ACTIVE'
FROM lingxi_core.sys_menu root
CROSS JOIN (VALUES
    ('用户管理', 'SYSTEM_USER', '/system/user', 'system/user/index', 'ri:user-settings-line', 'system:user:list', 10),
    ('角色管理', 'SYSTEM_ROLE', '/system/role', 'system/role/index', 'ri:admin-line', 'system:role:list', 20),
    ('菜单管理', 'SYSTEM_MENU', '/system/menu', 'system/menu/index', 'ri:menu-line', 'system:menu:list', 30),
    ('部门管理', 'SYSTEM_DEPARTMENT', '/system/department', 'system/department/index', 'ri:organization-chart', 'system:department:list', 40)
) AS menu(name, code, route_path, component, icon, permission_code, sort_order)
WHERE root.code = 'SYSTEM'
ON CONFLICT (code) DO NOTHING;

-- 模型管理是与系统管理同级的一级目录，禁止把Layout嵌套到系统管理Layout中。
INSERT INTO lingxi_core.sys_menu
    (parent_id, name, code, menu_type, route_path, component, icon, permission_code, sort_order, visible, status)
VALUES (NULL, '模型管理', 'SYSTEM_MODEL', 'DIRECTORY', '/model', 'Layout',
        'ri:brain-line', NULL, 50, TRUE, 'ACTIVE')
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
SELECT parent.id, page.name, page.code, 'MENU', page.route_path, page.component, page.icon,
       page.permission_code, page.sort_order, TRUE, 'ACTIVE'
FROM (VALUES
    ('供应商管理', 'SYSTEM_MODEL_PROVIDER', '/model/provider', 'system/model/provider/index', 'ri:cloud-line', 'model:provider:list', 10),
    ('API Key管理', 'SYSTEM_MODEL_CREDENTIAL', '/model/credential', 'system/model/credential/index', 'ri:key-2-line', 'model:credential:list', 20),
    ('模型实例管理', 'SYSTEM_MODEL_INSTANCE', '/model/instance', 'system/model/instance/index', 'ri:robot-2-line', 'model:instance:list', 30)
) AS page(name, code, route_path, component, icon, permission_code, sort_order)
JOIN lingxi_core.sys_menu parent ON parent.code = 'SYSTEM_MODEL'
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
    (parent_id, name, code, menu_type, permission_code, sort_order, visible, status)
SELECT parent.id, action.name, action.code, 'BUTTON', action.permission_code,
       action.sort_order, FALSE, 'ACTIVE'
FROM (VALUES
    ('SYSTEM_MODEL_PROVIDER', '供应商维护', 'SYSTEM_MODEL_PROVIDER_MANAGE', 'model:provider:manage', 10),
    ('SYSTEM_MODEL_CREDENTIAL', '密钥维护', 'SYSTEM_MODEL_CREDENTIAL_MANAGE', 'model:credential:manage', 10),
    ('SYSTEM_MODEL_INSTANCE', '模型实例维护', 'SYSTEM_MODEL_INSTANCE_MANAGE', 'model:instance:manage', 10),
    ('SYSTEM_MODEL_INSTANCE', '设置默认模型', 'SYSTEM_MODEL_INSTANCE_DEFAULT', 'model:instance:default', 20),
    ('SYSTEM_MODEL_INSTANCE', '模型连通测试', 'SYSTEM_MODEL_INSTANCE_TEST', 'model:instance:test', 30)
) AS action(parent_code, name, code, permission_code, sort_order)
JOIN lingxi_core.sys_menu parent ON parent.code = action.parent_code
ON CONFLICT (code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name = EXCLUDED.name,
    permission_code = EXCLUDED.permission_code,
    sort_order = EXCLUDED.sort_order,
    visible = FALSE,
    status = 'ACTIVE';

-- Prompt模板管理是独立的一级目录，不归属于模型管理或系统管理。
INSERT INTO lingxi_core.sys_menu
    (parent_id, name, code, menu_type, route_path, component, icon, permission_code, sort_order, visible, status)
VALUES (NULL, 'Prompt模板管理', 'PROMPT', 'DIRECTORY', '/prompt', 'Layout',
        'ri:terminal-box-line', NULL, 60, TRUE, 'ACTIVE')
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
SELECT parent.id, '模板列表', 'PROMPT_LIST', 'MENU', '/prompt/list',
       'prompt/index', 'ri:file-text-line', 'prompt:list', 10, TRUE, 'ACTIVE'
FROM lingxi_core.sys_menu parent
WHERE parent.code = 'PROMPT'
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
    (parent_id, name, code, menu_type, permission_code, sort_order, visible, status)
SELECT parent.id, action.name, action.code, 'BUTTON', action.permission_code,
       action.sort_order, FALSE, 'ACTIVE'
FROM (VALUES
    ('Prompt维护', 'PROMPT_MANAGE', 'prompt:manage', 10),
    ('Prompt发布', 'PROMPT_PUBLISH', 'prompt:publish', 20)
) AS action(name, code, permission_code, sort_order)
JOIN lingxi_core.sys_menu parent ON parent.code = 'PROMPT_LIST'
ON CONFLICT (code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    name = EXCLUDED.name,
    permission_code = EXCLUDED.permission_code,
    sort_order = EXCLUDED.sort_order,
    visible = FALSE,
    status = 'ACTIVE';

-- 页面操作权限，后端接口使用同一permission_code进行校验。
INSERT INTO lingxi_core.sys_menu
    (parent_id, name, code, menu_type, permission_code, sort_order, visible, status)
SELECT parent.id, action.name, action.code, 'BUTTON', action.permission_code,
       action.sort_order, FALSE, 'ACTIVE'
FROM (VALUES
    ('SYSTEM_USER', '新增用户', 'SYSTEM_USER_CREATE', 'system:user:create', 10),
    ('SYSTEM_USER', '修改用户', 'SYSTEM_USER_UPDATE', 'system:user:update', 20),
    ('SYSTEM_USER', '分配角色', 'SYSTEM_USER_ASSIGN_ROLE', 'system:user:assign-role', 30),
    ('SYSTEM_USER', '应用访问', 'SYSTEM_USER_APPLICATION_ACCESS', 'system:user:application-access', 40),
    ('SYSTEM_ROLE', '新增角色', 'SYSTEM_ROLE_CREATE', 'system:role:create', 10),
    ('SYSTEM_ROLE', '修改角色', 'SYSTEM_ROLE_UPDATE', 'system:role:update', 20),
    ('SYSTEM_ROLE', '删除角色', 'SYSTEM_ROLE_DELETE', 'system:role:delete', 30),
    ('SYSTEM_ROLE', '分配菜单', 'SYSTEM_ROLE_ASSIGN_MENU', 'system:role:assign-menu', 40),
    ('SYSTEM_ROLE', '分配部门', 'SYSTEM_ROLE_ASSIGN_DEPARTMENT', 'system:role:assign-department', 50),
    ('SYSTEM_MENU', '新增菜单', 'SYSTEM_MENU_CREATE', 'system:menu:create', 10),
    ('SYSTEM_MENU', '修改菜单', 'SYSTEM_MENU_UPDATE', 'system:menu:update', 20),
    ('SYSTEM_MENU', '删除菜单', 'SYSTEM_MENU_DELETE', 'system:menu:delete', 30),
    ('SYSTEM_DEPARTMENT', '新增部门', 'SYSTEM_DEPARTMENT_CREATE', 'system:department:create', 10),
    ('SYSTEM_DEPARTMENT', '修改部门', 'SYSTEM_DEPARTMENT_UPDATE', 'system:department:update', 20),
    ('SYSTEM_DEPARTMENT', '删除部门', 'SYSTEM_DEPARTMENT_DELETE', 'system:department:delete', 30)
) AS action(parent_code, name, code, permission_code, sort_order)
JOIN lingxi_core.sys_menu parent ON parent.code = action.parent_code
ON CONFLICT (code) DO NOTHING;

-- root账号绑定ROOT角色。
INSERT INTO lingxi_core.sys_user_role (user_id, role_id)
SELECT sys_user.id, role.id
FROM lingxi_core.sys_user sys_user
CROSS JOIN lingxi_core.sys_role role
WHERE sys_user.username = 'root'
  AND role.code = 'ROOT'
ON CONFLICT DO NOTHING;

-- ROOT权限由查询逻辑动态赋予全部生效菜单，不在关联表中保存重复数据。
-- 清理早期脚本可能写入的ROOT菜单关系，否则这些引用会阻止菜单正常删除。
DELETE FROM lingxi_core.sys_role_menu role_menu
USING lingxi_core.sys_role role
WHERE role_menu.role_id = role.id
  AND role.code = 'ROOT';

COMMIT;
