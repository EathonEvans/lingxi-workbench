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

    private boolean enabled;
    private Long chatModelInstanceId;
    private Long promptVersionId;
    private UserProfileUpdateStrategy updateStrategy;

    public UserProfileUpdateStrategy effectiveUpdateStrategy() {
        return Objects.requireNonNullElse(updateStrategy, UserProfileUpdateStrategy.MERGE);
    }

    public static UserProfileMemoryConfig disabled() {
        return UserProfileMemoryConfig.builder().enabled(false).updateStrategy(UserProfileUpdateStrategy.MERGE).build();
    }
}
