package com.lingxi.workbench.admin.agent.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Agent 知识库数据版本绑定模式。 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeBindingMode {
    /** 始终读取知识库当前生效的文档分块版本。 */
    LATEST("LATEST", "跟随知识库最新生效数据");

    @EnumValue
    private final String code;
    private final String describe;
}
