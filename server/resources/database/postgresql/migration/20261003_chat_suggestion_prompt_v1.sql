BEGIN;

-- ===== 回答建议提示词模板 =====
-- 平台内置补充任务的系统级 Prompt，不归属任何 Application，在管理端「Prompt 模板」中可见可编辑。
-- 运行端按稳定编码 chat_answer_suggestion 解析最新已发布版本；待分析的回答以用户消息传入，模板不含变量。

INSERT INTO lingxi_core.ai_prompt (name, code, role, description, status, created_by, updated_by)
VALUES ('对话-回答建议', 'chat_answer_suggestion', 'SYSTEM', '回答完成后生成用户可能的后续行动建议', 'ACTIVE', 1, 1)
ON CONFLICT (code) DO NOTHING;

INSERT INTO lingxi_core.ai_prompt_version (prompt_id, version_no, prompt_content, variables, status, published_at, created_by, updated_by)
SELECT p.id, 1,
       '你是工作台助手中的后续行动建议组件。基于助手刚给出的回答，站在用户视角提出接下来最可能想做的后续请求。

要求：
1. 每条建议必须是可直接发送执行的完整问题，点击后无需补充说明即可被理解；
2. 建议与回答内容紧密衔接，例如延伸分析、落地执行、整理产出、补充遗漏，不要复述原问题；
3. 不要提出需要用户先提供新材料才能执行的建议；
4. 每条建议 title 不超过 12 个字，prompt 不超过 60 个字；
5. 最多 3 条；没有值得建议的后续行动时输出空数组。

只输出 JSON 数组，不要任何解释文字，也不要使用 Markdown 代码块，格式：
[{"title":"后续行动短标题","prompt":"可直接发送的完整问题"}]',
       NULL,
       'PUBLISHED', CURRENT_TIMESTAMP, 1, 1
FROM lingxi_core.ai_prompt p
WHERE p.code = 'chat_answer_suggestion'
  AND NOT EXISTS (SELECT 1 FROM lingxi_core.ai_prompt_version v WHERE v.prompt_id = p.id AND v.status = 'PUBLISHED');

COMMIT;
