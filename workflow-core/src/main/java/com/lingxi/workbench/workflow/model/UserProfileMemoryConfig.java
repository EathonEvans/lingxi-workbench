package com.lingxi.workbench.workflow.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/** 用户画像提取配置。 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserProfileMemoryConfig {

    /** 是否启用用户画像提取。 */
    private boolean enabled;
    /** Chat 模型实例主键。 */
    private Long chatModelInstanceId;
    /** 已发布 PromptVersion 主键。 */
    private Long promptVersionId;
    /** 画像更新策略，默认 MERGE。 */
    private UserProfileUpdateStrategy updateStrategy;

    public UserProfileUpdateStrategy effectiveUpdateStrategy() {
        return Objects.requireNonNullElse(updateStrategy, UserProfileUpdateStrategy.MERGE);
    }

    public static UserProfileMemoryConfig disabled() {
        return UserProfileMemoryConfig.builder().enabled(false).updateStrategy(UserProfileUpdateStrategy.MERGE).build();
    }
}
