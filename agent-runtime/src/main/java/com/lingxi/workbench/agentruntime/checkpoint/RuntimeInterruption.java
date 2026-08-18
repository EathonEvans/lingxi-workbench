package com.lingxi.workbench.agentruntime.checkpoint;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** Runtime 对外返回的安全人工中断事实，不包含 Tool 参数和原始结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RuntimeInterruption {

    private Long interactionId;
    private Long checkpointId;
    private String fingerprint;
    private String title;
    private String summary;
    private String riskLevel;
    private String resourceScope;
    private Integer revision;
    private OffsetDateTime expiresAt;
}
