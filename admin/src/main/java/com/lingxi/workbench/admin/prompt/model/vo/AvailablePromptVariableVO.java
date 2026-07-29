package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 管理端可见的内置运行时变量定义。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AvailablePromptVariableVO {
    private final String code;
    private final String key;
    private final String groupCode;
    private final String displayName;
    private final PromptVariableType type;
    private final String description;
    private final List<String> channels;
    private final String producer;
    private final String producerDisplayName;
}
