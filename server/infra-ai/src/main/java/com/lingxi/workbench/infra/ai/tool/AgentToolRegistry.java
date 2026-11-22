package com.lingxi.workbench.infra.ai.tool;

import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Agent 工具的唯一注册表。
 *
 * <p>Spring 收集全部 AgentTool 后在启动阶段校验编码唯一性与输入 Schema。
 * 该注册表只管理当前应用实际部署的执行器。</p>
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> toolsByCode;

    public AgentToolRegistry(List<AgentTool> tools) {
        Map<String, AgentTool> registeredTools = new LinkedHashMap<>();
        tools.forEach(tool -> register(registeredTools, tool));
        toolsByCode = Collections.unmodifiableMap(new LinkedHashMap<>(registeredTools));
    }

    /**
     * 返回按照注册顺序排列的全部工具定义。
     */
    public List<AgentToolDefinition> definitions() {
        return toolsByCode.values().stream().map(AgentTool::definition).toList();
    }

    /**
     * 按稳定编码查找完整工具。
     */
    public Optional<AgentTool> find(String code) {
        return Optional.ofNullable(toolsByCode.get(code));
    }

    /**
     * 按 AgentVersion 中冻结的顺序解析本次需要执行的工具。
     */
    public List<AgentTool> resolve(List<String> toolCodes) {
        return toolCodes.stream().map(this::require).toList();
    }

    private AgentTool require(String code) {
        return find(code).orElseThrow(() -> new AgentToolException("AgentVersion引用的工具不存在：" + code));
    }

    private static void register(Map<String, AgentTool> registeredTools, AgentTool tool) {
        AgentToolDefinition definition = Objects.requireNonNull(tool.definition(), "工具定义不能为空");
        Map<String, Object> inputSchema = Objects.requireNonNull(tool.inputSchema(), "工具输入Schema不能为空");
        if (!Objects.equals("object", inputSchema.get("type"))) {
            throw new IllegalStateException("工具输入Schema必须声明object类型：" + definition.getCode());
        }
        AgentTool previous = registeredTools.putIfAbsent(definition.getCode(), tool);
        if (Objects.nonNull(previous)) {
            throw new IllegalStateException("工具编码重复注册：" + definition.getCode());
        }
    }
}
