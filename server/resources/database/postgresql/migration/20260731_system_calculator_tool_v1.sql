BEGIN;

-- 计算器只保存控制面契约；执行器由运行端加载 AgentTool 并按 code 注册。
INSERT INTO lingxi_core.ai_tool
    (name, code, description, source_type, parameter_schema, status, created_by, updated_by)
SELECT '基础计算器',
       'system_calculator',
       '对两个十进制数执行加、减、乘、除运算；用户询问明确数值计算时使用。',
       'LOCAL_FUNCTION',
       '{
          "type": "object",
          "properties": {
            "leftOperand": {
              "type": "number",
              "description": "左操作数"
            },
            "operator": {
              "type": "string",
              "description": "运算符，仅支持ADD、SUBTRACT、MULTIPLY、DIVIDE或+、-、*、/"
            },
            "rightOperand": {
              "type": "number",
              "description": "右操作数"
            },
            "scale": {
              "type": "integer",
              "description": "除法结果保留的小数位数，范围0到10，默认6"
            }
          },
          "required": ["leftOperand", "operator", "rightOperand"],
          "additionalProperties": false
        }'::JSONB,
       'ACTIVE',
       root_user.id,
       root_user.id
FROM lingxi_core.sys_user root_user
WHERE root_user.username = 'root'
ON CONFLICT (code) DO NOTHING;

COMMIT;
