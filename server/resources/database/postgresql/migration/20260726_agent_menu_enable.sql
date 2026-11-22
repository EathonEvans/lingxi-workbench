BEGIN;

-- 修正已执行 Agent V1 迁移的环境：管理页面完成后开放目录和列表入口。
UPDATE lingxi_core.sys_menu
   SET visible = TRUE,
       status = 'ACTIVE'
 WHERE code IN ('AGENT', 'AGENT_LIST');

COMMIT;
