BEGIN;

-- ===== 记忆系统提示词模板 =====
-- 这些模板属于系统级 Prompt，不归属任何 Application，在管理端「Prompt 模板」中可见可编辑

-- 1. 会话摘要
INSERT INTO lingxi_core.ai_prompt (name, code, role, description, status, created_by, updated_by)
VALUES ('记忆-会话摘要', 'memory_summary', 'SYSTEM', '将对话历史压缩为简洁摘要', 'ACTIVE', 1, 1)
ON CONFLICT (code) DO NOTHING;

INSERT INTO lingxi_core.ai_prompt_version (prompt_id, version_no, prompt_content, variables, status, published_at, created_by, updated_by)
SELECT p.id, 1,
       '将以下对话压缩为简洁摘要，保留核心诉求和结论：

{{conversation_history}}',
       '[{"name":"conversation_history","type":"STRING","required":true,"description":"待摘要的对话历史"}]'::JSONB,
       'PUBLISHED', CURRENT_TIMESTAMP, 1, 1
FROM lingxi_core.ai_prompt p
WHERE p.code = 'memory_summary'
  AND NOT EXISTS (SELECT 1 FROM lingxi_core.ai_prompt_version v WHERE v.prompt_id = p.id AND v.status = 'PUBLISHED');

-- 2. 用户画像提取
INSERT INTO lingxi_core.ai_prompt (name, code, role, description, status, created_by, updated_by)
VALUES ('记忆-用户画像提取', 'memory_profile_extraction', 'SYSTEM', '从对话中提取用户画像', 'ACTIVE', 1, 1)
ON CONFLICT (code) DO NOTHING;

INSERT INTO lingxi_core.ai_prompt_version (prompt_id, version_no, prompt_content, variables, status, published_at, created_by, updated_by)
SELECT p.id, 1,
       '根据对话更新用户画像。已有画像：{{existing_profile}}

只输出JSON，不要其他文字。

对话：
{{conversation_history}}',
       '[{"name":"existing_profile","type":"STRING","required":true,"description":"已有用户画像"},{"name":"conversation_history","type":"STRING","required":true,"description":"当前对话历史"}]'::JSONB,
       'PUBLISHED', CURRENT_TIMESTAMP, 1, 1
FROM lingxi_core.ai_prompt p
WHERE p.code = 'memory_profile_extraction'
  AND NOT EXISTS (SELECT 1 FROM lingxi_core.ai_prompt_version v WHERE v.prompt_id = p.id AND v.status = 'PUBLISHED');

-- 3. 交互记录提取
INSERT INTO lingxi_core.ai_prompt (name, code, role, description, status, created_by, updated_by)
VALUES ('记忆-交互记录提取', 'memory_interaction_record', 'SYSTEM', '从对话中提取关键交互事件', 'ACTIVE', 1, 1)
ON CONFLICT (code) DO NOTHING;

INSERT INTO lingxi_core.ai_prompt_version (prompt_id, version_no, prompt_content, variables, status, published_at, created_by, updated_by)
SELECT p.id, 1,
       '从对话中提取关键交互事件。输出JSON格式: {"summary":"事件的完整叙述，嵌入订单号、金额、产品名称等具体数据","entities":{"order_id":"...","product":"...","event_type":"refund/order/inquiry"}}

只输出JSON，不要其他文字。

对话：
{{conversation_history}}',
       '[{"name":"conversation_history","type":"STRING","required":true,"description":"待提取的对话历史"}]'::JSONB,
       'PUBLISHED', CURRENT_TIMESTAMP, 1, 1
FROM lingxi_core.ai_prompt p
WHERE p.code = 'memory_interaction_record'
  AND NOT EXISTS (SELECT 1 FROM lingxi_core.ai_prompt_version v WHERE v.prompt_id = p.id AND v.status = 'PUBLISHED');

-- 4. 知识库指令（SYSTEM角色）
INSERT INTO lingxi_core.ai_prompt (name, code, role, description, status, created_by, updated_by)
VALUES ('知识库优先指令', 'knowledge_base_instruction', 'SYSTEM', '引导模型优先使用知识库内容回答', 'ACTIVE', 1, 1)
ON CONFLICT (code) DO NOTHING;

INSERT INTO lingxi_core.ai_prompt_version (prompt_id, version_no, prompt_content, status, published_at, created_by, updated_by)
SELECT p.id, 1,
       '请优先依据给定的知识库内容回答问题。
如果知识库内容不足以支持结论，请明确说明，不要编造。',
       'PUBLISHED', CURRENT_TIMESTAMP, 1, 1
FROM lingxi_core.ai_prompt p
WHERE p.code = 'knowledge_base_instruction'
  AND NOT EXISTS (SELECT 1 FROM lingxi_core.ai_prompt_version v WHERE v.prompt_id = p.id AND v.status = 'PUBLISHED');

-- 5. 知识库用户 Prompt 模板
INSERT INTO lingxi_core.ai_prompt (name, code, role, description, status, created_by, updated_by)
VALUES ('知识库用户Prompt模板', 'knowledge_user_prompt', 'USER', '组装知识库内容的用户消息模板', 'ACTIVE', 1, 1)
ON CONFLICT (code) DO NOTHING;

UPDATE lingxi_core.ai_prompt SET role = 'USER' WHERE code = 'knowledge_user_prompt';

INSERT INTO lingxi_core.ai_prompt_version (prompt_id, version_no, prompt_content, variables, status, published_at, created_by, updated_by)
SELECT p.id, 1,
       '知识库内容：

{{knowledge_context}}

用户问题：{{user_query}}',
       '[{"name":"knowledge_context","type":"STRING","required":true,"description":"召回的知识内容"},{"name":"user_query","type":"STRING","required":true,"description":"用户当前问题"}]'::JSONB,
       'PUBLISHED', CURRENT_TIMESTAMP, 1, 1
FROM lingxi_core.ai_prompt p
WHERE p.code = 'knowledge_user_prompt'
  AND NOT EXISTS (SELECT 1 FROM lingxi_core.ai_prompt_version v WHERE v.prompt_id = p.id AND v.status = 'PUBLISHED');

-- 更新主表的 published_version_id
UPDATE lingxi_core.ai_prompt p
SET published_version_id = v.id
FROM lingxi_core.ai_prompt_version v
WHERE v.prompt_id = p.id AND v.status = 'PUBLISHED' AND v.version_no = 1
  AND p.published_version_id IS NULL;

COMMIT;
