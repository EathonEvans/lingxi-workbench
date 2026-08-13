package com.lingxi.workbench.application.runtime.service.tool;

import com.lingxi.workbench.application.runtime.common.enums.RuntimeToolSourceType;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeMcpServerEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeToolEntity;
import com.lingxi.workbench.application.runtime.repo.McpServerRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.ToolRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.tool.ToolRuntimeService;
import com.lingxi.workbench.application.runtime.tool.McpRuntimeToolCallback;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.mcp.McpSyncClientFactory;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.AgentToolRegistry;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionMode;
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
    private final McpServerRuntimeRepo mcpServerRuntimeRepo;
    private final AgentToolRegistry agentToolRegistry;
    private final McpSyncClientFactory mcpSyncClientFactory;
    private final SecretCipher secretCipher;

    /** 读取启用定义并按 Agent 配置顺序装配执行器。 */
    @Override
    @Transactional(readOnly = true)
    public List<AgentTool> resolveActiveTools(List<String> toolCodes) {
        Map<String, RuntimeToolEntity> definitions = toolRuntimeRepo.findActiveByCodes(toolCodes).stream()
                .collect(Collectors.toMap(RuntimeToolEntity::getCode, Function.identity()));
        Map<Long, RuntimeMcpServerEntity> mcpServers = loadMcpServers(definitions.values().stream().toList());
        return toolCodes.stream()
                .map(code -> requireDefinition(definitions, code))
                .map(definition -> managedTool(definition, resolveExecutor(definition, mcpServers)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AgentTool resolveActiveTool(Long toolId) {
        RuntimeToolEntity definition = toolRuntimeRepo.findActiveById(toolId)
                .orElseThrow(() -> new AgentToolException("Application锁定的Tool不存在或已停用"));
        Map<Long, RuntimeMcpServerEntity> mcpServers = loadMcpServers(List.of(definition));
        return managedTool(definition, resolveExecutor(definition, mcpServers));
    }

    private Map<Long, RuntimeMcpServerEntity> loadMcpServers(List<RuntimeToolEntity> definitions) {
        List<Long> serverIds = definitions.stream()
                .filter(definition -> definition.getSourceType() == RuntimeToolSourceType.MCP)
                .map(RuntimeToolEntity::getMcpServerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return mcpServerRuntimeRepo.findRuntimeAvailableByIds(serverIds).stream()
                .collect(Collectors.toMap(RuntimeMcpServerEntity::getId, Function.identity()));
    }

    private AgentTool resolveExecutor(RuntimeToolEntity definition, Map<Long, RuntimeMcpServerEntity> mcpServers) {
        if (Objects.isNull(definition.getSourceType())) {
            throw new AgentToolException("Tool来源类型为空：" + definition.getCode());
        }
        return switch (definition.getSourceType()) {
            case LOCAL_FUNCTION -> requireLocalExecutor(definition.getCode());
            case MCP -> mcpExecutor(definition, requireMcpServer(mcpServers, definition));
        };
    }

    private AgentTool requireLocalExecutor(String code) {
        return agentToolRegistry.find(code).orElseThrow(() -> new AgentToolException("应用运行端未部署Tool执行器：" + code));
    }

    private AgentTool mcpExecutor(RuntimeToolEntity definition, RuntimeMcpServerEntity server) {
        if (Objects.isNull(definition.getRemoteToolName()) || definition.getRemoteToolName().isBlank()) {
            throw new AgentToolException("MCP Tool缺少远端工具名称：" + definition.getCode());
        }
        AgentToolDefinition runtimeDefinition = runtimeDefinition(definition);
        ToolCallback callback = new McpRuntimeToolCallback(definition, server, mcpSyncClientFactory, secretCipher);
        return new ManagedAgentTool(runtimeDefinition, callback);
    }

    private static RuntimeMcpServerEntity requireMcpServer(Map<Long, RuntimeMcpServerEntity> mcpServers, RuntimeToolEntity definition) {
        RuntimeMcpServerEntity server = mcpServers.get(definition.getMcpServerId());
        if (Objects.isNull(server)) {
            throw new AgentToolException("MCP Server不存在、未启用或最近连接测试未成功：tool=" + definition.getCode());
        }
        return server;
    }

    private static RuntimeToolEntity requireDefinition(Map<String, RuntimeToolEntity> definitions, String code) {
        RuntimeToolEntity definition = definitions.get(code);
        if (Objects.isNull(definition)) {
            throw new AgentToolException("Tool不存在、已停用或来源类型不受支持：" + code);
        }
        return definition;
    }

    private static AgentTool managedTool(RuntimeToolEntity definition, AgentTool executor) {
        AgentToolDefinition runtimeDefinition = runtimeDefinition(definition, executor.definition());
        ToolCallback callback = new ManagedToolCallback(definition, executor.callback());
        return new ManagedAgentTool(runtimeDefinition, callback);
    }

    private static AgentToolDefinition runtimeDefinition(RuntimeToolEntity definition) {
        return runtimeDefinition(definition, null);
    }

    /** 本地执行器保留其只读与副作用语义，MCP Tool 未声明时按副作用工具处理。 */
    private static AgentToolDefinition runtimeDefinition(RuntimeToolEntity definition, AgentToolDefinition executorDefinition) {
        AgentToolExecutionMode executionMode = AgentToolExecutionMode.SIDE_EFFECT;
        if (Objects.nonNull(executorDefinition)) {
            executionMode = executorDefinition.getExecutionMode();
        }
        return AgentToolDefinition.builder()
                .code(definition.getCode())
                .displayName(definition.getName())
                .description(definition.getDescription())
                .executionMode(executionMode)
                .build();
    }

    /**
     * 将控制面模型契约和运行端执行回调组合为不可变工具。
     *
     * @param definition 模型可见的工具契约定义
     * @param callback   实际执行回调
     */
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
