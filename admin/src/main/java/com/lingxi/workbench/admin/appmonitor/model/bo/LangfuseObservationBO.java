package com.lingxi.workbench.admin.appmonitor.model.bo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** Langfuse v4 Observations API 返回的安全字段。 */
@Getter
@Builder
public class LangfuseObservationBO {
    private String id;
    private String parentObservationId;
    private String name;
    private String type;
    private String level;
    private String providedModelName;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
}
