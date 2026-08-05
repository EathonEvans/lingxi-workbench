package com.lingxi.workbench.infra.ai.variable;

import com.lingxi.workbench.infra.ai.variable.common.enums.BuiltInRuntimeVariable;
import com.lingxi.workbench.infra.ai.variable.common.enums.RuntimeVariableChannel;
import com.lingxi.workbench.infra.ai.variable.common.enums.RuntimeVariableGroup;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** 内置运行时变量目录测试。 */
class BuiltInRuntimeVariableTest {

    @Test
    void shouldExposeUniqueKeysAndChineseNames() {
        BuiltInRuntimeVariable[] variables = BuiltInRuntimeVariable.values();

        assertThat(Arrays.stream(variables).map(BuiltInRuntimeVariable::getCode)).doesNotHaveDuplicates();
        assertThat(Arrays.stream(variables).map(BuiltInRuntimeVariable::getDisplayName))
                .allMatch(name -> name != null && !name.isBlank());
        assertThat(Arrays.stream(variables))
                .allMatch(variable -> variable.getCode().equals(
                        variable.getGroup().getCode()
                                + "."
                                + variable.getKey()))
                .allMatch(variable -> variable.getProducer() != null)
                .allMatch(variable -> !variable.getChannels().isEmpty());
        assertThat(Arrays.stream(variables)
                .allMatch(variable -> RuntimeVariableCatalog.find(
                        variable.getCode()).orElseThrow() == variable))
                .isTrue();
    }

    @Test
    void shouldExposeGroupsInBackendDefinedOrder() {
        assertThat(RuntimeVariableCatalog.groups())
                .extracting(RuntimeVariableGroup::getSort)
                .isSorted();
        assertThat(RuntimeVariableCatalog.findGroup("query"))
                .contains(RuntimeVariableGroup.QUERY);
    }

    @Test
    void shouldScopeQueryRewriteVariablesToDedicatedChannel() {
        assertThat(BuiltInRuntimeVariable.USER_QUERY.supportsChannel(
                RuntimeVariableChannel.QUERY_REWRITE_PROMPT))
                .isTrue();
        assertThat(BuiltInRuntimeVariable.QUERY_REWRITE_HISTORY.supportsChannel(
                RuntimeVariableChannel.QUERY_REWRITE_PROMPT))
                .isTrue();
        assertThat(BuiltInRuntimeVariable.USER_QUERY.supportsChannel(
                RuntimeVariableChannel.SYSTEM_PROMPT))
                .isFalse();
    }

    @Test
    void shouldExposeCollaborationDelegationLimitToPromptChannels() {
        BuiltInRuntimeVariable variable = RuntimeVariableCatalog.find(
                        "collaboration.max_delegations")
                .orElseThrow();

        assertThat(variable.getGroup()).isEqualTo(RuntimeVariableGroup.COLLABORATION);
        assertThat(variable.getType().getCode()).isEqualTo("INTEGER");
        assertThat(variable.supportsChannel(RuntimeVariableChannel.SYSTEM_PROMPT)).isTrue();
        assertThat(variable.supportsChannel(RuntimeVariableChannel.USER_PROMPT)).isTrue();
        assertThat(variable.supportsChannel(RuntimeVariableChannel.QUERY_REWRITE_PROMPT)).isFalse();
    }
}
