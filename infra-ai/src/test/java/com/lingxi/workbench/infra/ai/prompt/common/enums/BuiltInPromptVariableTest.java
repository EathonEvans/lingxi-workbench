package com.lingxi.workbench.infra.ai.prompt.common.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Prompt内置变量目录测试。 */
class BuiltInPromptVariableTest {

    @Test
    void shouldExposeUniqueKeysAndChineseNames() {
        BuiltInPromptVariable[] variables = BuiltInPromptVariable.values();

        assertThat(Arrays.stream(variables).map(BuiltInPromptVariable::getCode)).doesNotHaveDuplicates();
        assertThat(Arrays.stream(variables).map(BuiltInPromptVariable::getDisplayName))
                .allMatch(name -> name != null && !name.isBlank());
    }
}
