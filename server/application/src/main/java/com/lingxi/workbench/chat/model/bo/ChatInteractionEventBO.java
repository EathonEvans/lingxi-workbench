package com.lingxi.workbench.chat.model.bo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/** 可安全展示给用户的 HITL 等待事件。 */
@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatInteractionEventBO {

    private String interactionId;
    private String interactionType;
    private String title;
    private String summary;
    private String riskLevel;
    private String resourceScope;
    private String fingerprint;
    private Integer revision;
    private OffsetDateTime expiresAt;
    private long sequence;
}
