package com.lingxi.workbench.workflow.engine.langgraph;

import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowExecutionPorts;
import com.lingxi.workbench.workflow.model.WorkflowExecutionRequest;
import com.lingxi.workbench.workflow.model.WorkflowNodeStatus;
import com.lingxi.workbench.workflow.service.WorkflowDefinitionValidator;
import com.lingxi.workbench.workflow.service.WorkflowExecutor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRuntimeBoundaryTest {

    @Test
    void shouldBindApplicationPortsPerExecutionInsteadOfExecutorSingleton() throws NoSuchMethodException {
        Field[] fields = LangGraphWorkflowExecutor.class.getDeclaredFields();
        assertThat(fields).singleElement().satisfies(field ->
                assertThat(field.getType()).isEqualTo(WorkflowDefinitionValidator.class));

        Constructor<?>[] publicConstructors = Arrays.stream(LangGraphWorkflowExecutor.class.getDeclaredConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                .toArray(Constructor<?>[]::new);
        assertThat(publicConstructors).singleElement().satisfies(constructor ->
                assertThat(constructor.getParameterCount()).isZero());

        Method execute = WorkflowExecutor.class.getMethod(
                "execute", WorkflowDefinition.class, WorkflowExecutionRequest.class, WorkflowExecutionPorts.class);
        assertThat(execute.getParameterTypes()).containsExactly(
                WorkflowDefinition.class, WorkflowExecutionRequest.class, WorkflowExecutionPorts.class);
    }

    @Test
    void shouldKeepCoreNodeStatusFreeOfPersistenceFrameworkAnnotations() {
        assertThat(Arrays.stream(WorkflowNodeStatus.class.getDeclaredAnnotations())
                .map(annotation -> annotation.annotationType().getName()))
                .noneMatch(name -> name.startsWith("com.baomidou.mybatisplus"));
    }
}
