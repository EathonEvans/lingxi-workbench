BEGIN;

UPDATE lingxi_core.ai_tool
SET description = '对两个十进制数执行加、减、乘、除运算；用户询问明确数值计算时使用。',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'system_calculator';

COMMIT;
