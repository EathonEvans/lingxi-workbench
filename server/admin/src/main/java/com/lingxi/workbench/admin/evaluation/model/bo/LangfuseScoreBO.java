package com.lingxi.workbench.admin.evaluation.model.bo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** Langfuse 数值 Score 安全投影，不读取 Trace 输入输出。 */
@Getter
@Builder
public class LangfuseScoreBO {
    private final String id;
    private final String name;
    private final double value;
    private final String source;
    private final String traceId;
    private final String comment;
    private final OffsetDateTime timestamp;
}
