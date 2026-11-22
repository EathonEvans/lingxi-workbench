-- 问题上下文化已统一命名为问题重写；仅更新Prompt主记录展示信息，不改动已发布版本及运行快照。
UPDATE lingxi_core.ai_prompt
SET name = '求职助手问题重写Prompt',
    description = '将依赖会话历史的用户追问重写为可独立理解的问题',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'career_contextualization';
