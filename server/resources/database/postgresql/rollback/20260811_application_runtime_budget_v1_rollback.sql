ALTER TABLE lingxi_core.ai_agent_draft
    ADD COLUMN IF NOT EXISTS runtime_policy JSONB NOT NULL
        DEFAULT '{"totalTimeoutSeconds":90,"maxModelCalls":3,"maxInputTokens":12000,"maxOutputTokens":2048}'::JSONB,
    ALTER COLUMN schema_version SET DEFAULT 4,
    ALTER COLUMN tool_policy SET DEFAULT '{"maxToolCalls":2,"maxResultCharacters":8000}'::JSONB;

UPDATE lingxi_core.ai_agent_draft
   SET generation_policy = generation_policy
            || '{"maxOutputTokens":2048,"timeoutSeconds":60,"maxRetries":1}'::JSONB,
       tool_policy = tool_policy || '{"maxToolCalls":2}'::JSONB,
       schema_version = 4;

UPDATE lingxi_core.ai_application_draft
   SET definition = jsonb_set(
           definition,
           '{collaboration,limits,maxDelegations}',
           COALESCE(definition #> '{runtimePolicy,maxDelegations}', '3'::JSONB),
           true)
 WHERE definition #> '{collaboration,limits}' IS NOT NULL;
