BEGIN;

-- tool_choice=auto 时，模型会自行回答简单算术；通过控制面工具描述明确确定性调用契约。
UPDATE lingxi_core.ai_tool
SET description = '对两个十进制数执行加、减、乘、除运算。只要用户要求进行明确数值的四则运算，即使模型能够心算，也必须调用本工具。',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'system_calculator';

COMMIT;
