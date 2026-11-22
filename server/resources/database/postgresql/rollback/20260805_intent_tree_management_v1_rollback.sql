BEGIN;

DELETE FROM lingxi_core.sys_menu
WHERE code IN ('INTENT_TREE_MANAGE', 'INTENT_TREE_PUBLISH', 'INTENT_TREE_LIST');

DROP TABLE IF EXISTS lingxi_core.ai_intent_routing_session;
DROP TABLE IF EXISTS lingxi_vector.intent_tree_vector;
DROP TABLE IF EXISTS lingxi_core.ai_intent_tree_draft;
DROP TABLE IF EXISTS lingxi_core.ai_intent_tree_version;
DROP TABLE IF EXISTS lingxi_core.ai_intent_tree;

COMMIT;
