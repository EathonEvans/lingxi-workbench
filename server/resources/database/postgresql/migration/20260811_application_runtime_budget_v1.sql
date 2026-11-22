-- Agent Schema V5：运行预算统一归属ApplicationVersion。
-- Agent仅保留模型采样参数、工具授权和单次工具结果裁剪，不再保存超时、Token或调用次数。

UPDATE lingxi_core.ai_agent_draft
   SET generation_policy = generation_policy - 'maxOutputTokens' - 'timeoutSeconds' - 'maxRetries',
       tool_policy = tool_policy - 'maxToolCalls',
       schema_version = 5;

ALTER TABLE lingxi_core.ai_agent_draft
    ALTER COLUMN schema_version SET DEFAULT 5,
    ALTER COLUMN tool_policy SET DEFAULT '{"maxResultCharacters":8000}'::JSONB,
    DROP COLUMN IF EXISTS runtime_policy;

COMMENT ON COLUMN lingxi_core.ai_agent_draft.tool_policy
    IS 'Agent单次工具结果字符上限；调用次数由ApplicationVersion统一控制';

-- Application运行策略统一保存委派次数；协作拓扑只保留固定深度。
UPDATE lingxi_core.ai_application_draft
   SET definition = definition #- '{collaboration,limits,maxDelegations}'
 WHERE definition #> '{collaboration,limits,maxDelegations}' IS NOT NULL;
