BEGIN;

-- 仅在应用代码也回退到 REACT_AGENT 契约时执行。
UPDATE lingxi_core.ai_application_draft draft
SET definition = jsonb_set(
        draft.definition,
        '{capabilityCatalog,capabilities}',
        (
            SELECT jsonb_agg(
                CASE
                    WHEN capability ->> 'type' = 'AGENT' THEN
                        jsonb_set(capability, '{type}', to_jsonb('REACT_AGENT'::TEXT), FALSE)
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
      '$.capabilityCatalog.capabilities[*] ? (@.type == "AGENT")'
  );

UPDATE lingxi_core.ai_application_version application_version
SET definition_snapshot = jsonb_set(
        application_version.definition_snapshot,
        '{capabilityCatalog,capabilities}',
        (
            SELECT jsonb_agg(
                CASE
                    WHEN capability ->> 'type' = 'AGENT' THEN
                        jsonb_set(capability, '{type}', to_jsonb('REACT_AGENT'::TEXT), FALSE)
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
      '$.capabilityCatalog.capabilities[*] ? (@.type == "AGENT")'
  );

COMMIT;
