BEGIN;

-- Application 能力类型已经统一为 AGENT；只修正能力目录中的旧枚举值，不放宽运行时解析。
UPDATE lingxi_core.ai_application_draft draft
SET definition = jsonb_set(
        draft.definition,
        '{capabilityCatalog,capabilities}',
        (
            SELECT jsonb_agg(
                CASE
                    WHEN capability ->> 'type' = 'REACT_AGENT' THEN
                        jsonb_set(capability, '{type}', to_jsonb('AGENT'::TEXT), FALSE)
                    ELSE capability
                END
                ORDER BY ordinal
            )
            FROM jsonb_array_elements(
                draft.definition #> '{capabilityCatalog,capabilities}'
            ) WITH ORDINALITY AS entries(capability, ordinal)
        ),
        FALSE
    ),
    revision = draft.revision + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE jsonb_typeof(draft.definition #> '{capabilityCatalog,capabilities}') = 'array'
  AND jsonb_path_exists(
      draft.definition,
      '$.capabilityCatalog.capabilities[*] ? (@.type == "REACT_AGENT")'
  );

-- 已发布版本仍需支持列表、详情和当前版本加载；迁移持久化事实，不在 Java 中保留旧枚举别名。
UPDATE lingxi_core.ai_application_version application_version
SET definition_snapshot = jsonb_set(
        application_version.definition_snapshot,
        '{capabilityCatalog,capabilities}',
        (
            SELECT jsonb_agg(
                CASE
                    WHEN capability ->> 'type' = 'REACT_AGENT' THEN
                        jsonb_set(capability, '{type}', to_jsonb('AGENT'::TEXT), FALSE)
                    ELSE capability
                END
                ORDER BY ordinal
            )
            FROM jsonb_array_elements(
                application_version.definition_snapshot #> '{capabilityCatalog,capabilities}'
            ) WITH ORDINALITY AS entries(capability, ordinal)
        ),
        FALSE
    )
WHERE jsonb_typeof(
          application_version.definition_snapshot #> '{capabilityCatalog,capabilities}'
      ) = 'array'
  AND jsonb_path_exists(
      application_version.definition_snapshot,
      '$.capabilityCatalog.capabilities[*] ? (@.type == "REACT_AGENT")'
  );

COMMIT;
