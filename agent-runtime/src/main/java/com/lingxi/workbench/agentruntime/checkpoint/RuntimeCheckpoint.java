package com.lingxi.workbench.agentruntime.checkpoint;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/** Runtime 在人工中断点持久化的完整、稳定、可移植状态。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RuntimeCheckpoint {

    /** V1是已废弃的Application简化Payload；V2起由Runtime独占完整恢复协议。 */
    public static final int CURRENT_VERSION = 2;

    private Integer version;
    private RuntimeCheckpointContext context;
    private ExecutionStateSnapshot state;
    private List<MessageSnapshot> messages;
    private ToolCallSnapshot pendingToolCall;
    private String toolConfigurationHash;
    private String resourceScope;
    private String fingerprint;
    private OffsetDateTime expiresAt;

    /** Spring AI Message 的稳定快照。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MessageSnapshot {
        private MessageKind kind;
        private String text;
        private List<ToolCallSnapshot> toolCalls;
        private List<ToolResponseSnapshot> toolResponses;
    }

    /** 原始 Spring AI ToolCall。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ToolCallSnapshot {
        private String id;
        private String type;
        private String name;
        private String arguments;
    }

    /** Spring AI ToolResponse。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ToolResponseSnapshot {
        private String id;
        private String name;
        private String responseData;
    }

    public enum MessageKind {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }
}
