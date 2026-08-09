package com.lingxi.workbench.workflow.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/** Application 级记忆配置，画像、交互记录与摘要分别冻结和执行。 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemoryConfig {

    /** 用户画像提取配置。 */
    private UserProfileMemoryConfig profile;
    /** 交互记录提取与检索配置。 */
    private InteractionRecordMemoryConfig interactionRecord;
    /** 会话滚动摘要配置。 */
    private ConversationSummaryMemoryConfig summary;

    public UserProfileMemoryConfig profile() {
        return Objects.requireNonNullElseGet(profile, UserProfileMemoryConfig::disabled);
    }

    public InteractionRecordMemoryConfig interactionRecord() {
        return Objects.requireNonNullElseGet(interactionRecord, InteractionRecordMemoryConfig::disabled);
    }

    public ConversationSummaryMemoryConfig summary() {
        return Objects.requireNonNullElseGet(summary, ConversationSummaryMemoryConfig::disabled);
    }

    public static MemoryConfig disabled() {
        return MemoryConfig.builder()
                .profile(UserProfileMemoryConfig.disabled())
                .interactionRecord(InteractionRecordMemoryConfig.disabled())
                .summary(ConversationSummaryMemoryConfig.disabled())
                .build();
    }
}
