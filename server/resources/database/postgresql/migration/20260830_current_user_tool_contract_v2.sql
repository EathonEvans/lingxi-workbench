-- 收紧内置用户信息 Tool 的模型可见契约，避免通用求职问题误触发 HITL。
-- 审批和风险策略属于管理端配置，本迁移不覆盖已有设置。
BEGIN;

INSERT INTO lingxi_core.ai_tool
    (name, code, description, source_type, parameter_schema,
     risk_level, approval_required, approval_ttl_seconds, resource_scope,
     status, created_by, updated_by)
SELECT '获取当前用户信息',
       'get_current_user_info',
       '仅当用户明确要求查询当前登录账号的显示名称或头像时调用。仅返回显示名称和头像；不得用于获取简历、JD、候选人事实或回答通用求职问题。',
       'LOCAL_FUNCTION',
       '{"type":"object","properties":{},"additionalProperties":false}'::JSONB,
       'MEDIUM',
       TRUE,
       1800,
       '当前登录用户的显示名称与头像',
       'ACTIVE',
       root_user.id,
       root_user.id
FROM lingxi_core.sys_user root_user
WHERE root_user.username = 'root'
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    parameter_schema = EXCLUDED.parameter_schema,
    resource_scope = EXCLUDED.resource_scope,
    updated_by = EXCLUDED.updated_by,
    updated_at = CURRENT_TIMESTAMP;

COMMIT;
