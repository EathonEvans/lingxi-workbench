BEGIN;

DELETE FROM lingxi_core.sys_menu
WHERE code IN ('MCP_SERVER_MANAGE', 'MCP_SERVER_LIST');

DROP TABLE IF EXISTS lingxi_core.ai_mcp_server;

COMMIT;
