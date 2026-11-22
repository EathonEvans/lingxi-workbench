package com.lingxi.workbench.admin.evaluation.model.bo;

import com.lingxi.workbench.framework.json.JSONObject;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** Langfuse 数据集中的一条不可变评测输入。 */
@Getter
@Builder
public class LangfuseDatasetItemBO {

    private final String id;
    private final JSONObject input;
    private final JSONObject expectedOutput;
    private final JSONObject metadata;
    private final String sourceTraceId;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
}
