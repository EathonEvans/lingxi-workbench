package com.lingxi.workbench.workflow.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/** Application 级记忆配置，三类记忆共享模型并分别冻结业务策略。 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemoryConfig {

    /** 画像、交互提取和摘要共用的 Chat 模型实例主键。 */
    private Long chatModelInstanceId;
    /** 交互记录向量化和召回使用的 Embedding 模型实例主键。 */
    private Long embeddingModelInstanceId;
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
