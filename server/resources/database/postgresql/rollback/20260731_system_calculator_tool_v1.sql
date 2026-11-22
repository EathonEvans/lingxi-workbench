BEGIN;

DELETE FROM lingxi_core.ai_tool
WHERE code = 'system_calculator';

COMMIT;
