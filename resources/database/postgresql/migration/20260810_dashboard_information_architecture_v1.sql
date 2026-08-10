-- Dashboard 只承载业务统计；原导航页直接调整为应用监控，保留主键及角色授权关系。
UPDATE lingxi_core.sys_menu
SET name = '应用监控',
    code = 'APPLICATION_MONITOR',
    route_path = '/dashboard/monitor',
    component = 'dashboard/monitor/index',
    icon = 'ri:pulse-line',
    sort_order = 20,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'NAVIGATION';

UPDATE lingxi_core.sys_menu
SET name = '仪表盘',
    route_path = '/dashboard/console',
    component = 'dashboard/console/index',
    icon = 'ri:pie-chart-line',
    sort_order = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'DASHBOARD';
