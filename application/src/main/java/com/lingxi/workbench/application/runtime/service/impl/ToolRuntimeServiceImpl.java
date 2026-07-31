package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.entity.RuntimeToolEntity;
import com.lingxi.workbench.application.runtime.repo.ToolRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.ToolRuntimeService;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.AgentToolRegistry;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tool 运行时装配服务。
 *
 * <p>数据库定义是模型可见契约的事实来源，Java AgentTool 是执行逻辑的事实来源。
 * 两者仅通过稳定 toolCode 对接，任何一侧缺失都会在调用模型前失败。</p>
 */
@Service
@RequiredArgsConstructor
public class ToolRuntimeServiceImpl implements ToolRuntimeService {

    private final ToolRuntimeRepo toolRuntimeRepo;
    private final AgentToolRegistry agentToolRegistry;

    /** 读取启用定义并按 Agent 配置顺序装配执行器。 */
    @Override
    @Transactional(readOnly = true)
    public List<AgentTool> resolveActiveTools(List<String> toolCodes) {
        Map<String, RuntimeToolEntity> definitions = toolRuntimeRepo.findActiveByCodes(toolCodes).stream()
                .collect(Collectors.toMap(RuntimeToolEntity::getCode, Function.identity()));
        return toolCodes.stream()
                .map(code -> managedTool(requireDefinition(definitions, code), requireExecutor(code)))
                .toList();
    }

    private AgentTool requireExecutor(String code) {
        return agentToolRegistry.find(code).orElseThrow(() -> new AgentToolException("应用运行端未部署Tool执行器：" + code));
    }

    private static RuntimeToolEntity requireDefinition(Map<String, RuntimeToolEntity> definitions, String code) {
        RuntimeToolEntity definition = definitions.get(code);
        if (Objects.isNull(definition)) {
            throw new AgentToolException("Tool不存在、已停用或来源类型不受支持：" + code);
        }
        return definition;
    }

    private static AgentTool managedTool(RuntimeToolEntity definition, AgentTool executor) {
        AgentToolDefinition runtimeDefinition = AgentToolDefinition.builder()
                .code(definition.getCode())
                .displayName(definition.getName())
                .description(definition.getDescription())
                .build();
        ToolCallback callback = new ManagedToolCallback(definition, executor.callback());
        return new ManagedAgentTool(runtimeDefinition, callback);
    }

    /** 将控制面模型契约和运行端执行回调组合为不可变工具。 */
    private record ManagedAgentTool(AgentToolDefinition definition, ToolCallback callback) implements AgentTool {
    }

    /**
     * 仅覆盖模型可见的 ToolDefinition，调用行为、元数据和 ToolContext 全部委托给部署的执行器。
     */
    private static final class ManagedToolCallback implements ToolCallback {

        private final ToolDefinition definition;
        private final ToolCallback delegate;

        private ManagedToolCallback(RuntimeToolEntity managedDefinition, ToolCallback delegate) {
            this.definition = ToolDefinition.builder()
                    .name(managedDefinition.getCode())
                    .description(managedDefinition.getDescription())
                    .inputSchema(managedDefinition.getParameterSchema().toJSONString())
                    .build();
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return delegate.call(toolInput);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return delegate.call(toolInput, toolContext);
        }
    }
}
