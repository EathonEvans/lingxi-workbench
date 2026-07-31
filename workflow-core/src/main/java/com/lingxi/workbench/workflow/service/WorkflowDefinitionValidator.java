package com.lingxi.workbench.workflow.service;

import com.lingxi.workbench.workflow.exception.WorkflowDefinitionException;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowEdgeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.WorkflowRoute;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.model.config.ConditionNodeConfig;
import com.lingxi.workbench.workflow.model.config.EndNodeConfig;
import com.lingxi.workbench.workflow.model.config.StartNodeConfig;
import com.lingxi.workbench.workflow.model.config.WorkflowNodeConfig;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Workflow 发布和编译前的结构校验器。
 */
public class WorkflowDefinitionValidator {

    /** 当前支持的协议版本号 */
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    /** 系统保留变量的前缀 */
    private static final String RESERVED_VARIABLE_PREFIX = "_";

    /**
     * 校验发布版本的工作流定义，要求 Agent 节点必须锁定版本。
     *
     * @param definition 工作流定义
     */
    public void validate(WorkflowDefinition definition) {
        validate(definition, true);
    }

    /**
     * 校验可编辑草稿。草稿中的 Agent 版本可以留空，发布时再解析为当前正式版本。
     *
     * @param definition 工作流定义
     */
    public void validateDraft(WorkflowDefinition definition) {
        validate(definition, false);
    }

    private void validate(WorkflowDefinition definition, boolean requireLockedAgentVersion) {
        require(definition != null, "Workflow定义不能为空");
        require(
                definition.schemaVersion() == SUPPORTED_SCHEMA_VERSION,
                "不支持的Workflow schemaVersion: " + definition.schemaVersion());
        require(isNotBlank(definition.code()), "Workflow编码不能为空");
        require(!definition.nodes().isEmpty(), "Workflow至少需要一个节点");

        Map<String, WorkflowNodeDefinition> nodesById = indexNodes(definition.nodes());
        validateNodeCounts(definition.nodes());
        validateNodes(definition.nodes(), requireLockedAgentVersion);
        validateEdges(definition.edges(), nodesById);
        validateNodeDegrees(definition.nodes(), definition.edges());
        validateReachabilityAndCycles(definition.nodes(), definition.edges());
    }

    private Map<String, WorkflowNodeDefinition> indexNodes(List<WorkflowNodeDefinition> nodes) {
        Map<String, WorkflowNodeDefinition> result = new HashMap<>();
        for (WorkflowNodeDefinition node : nodes) {
            require(node != null, "Workflow节点不能为空");
            require(isNotBlank(node.id()), "Workflow节点ID不能为空");
            require(!node.id().startsWith(RESERVED_VARIABLE_PREFIX), "Workflow节点ID不能以下划线开头: " + node.id());
            require(result.putIfAbsent(node.id(), node) == null, "Workflow节点ID重复: " + node.id());
        }
        return result;
    }

    private void validateNodeCounts(List<WorkflowNodeDefinition> nodes) {
        long startCount = count(nodes, WorkflowNodeType.START);
        long endCount = count(nodes, WorkflowNodeType.END);
        require(startCount == 1, "Workflow必须且只能包含一个START节点");
        require(endCount == 1, "POC阶段Workflow必须且只能包含一个END节点");
    }

    private long count(List<WorkflowNodeDefinition> nodes, WorkflowNodeType type) {
        return nodes.stream().filter(node -> node.type() == type).count();
    }

    private void validateNodes(
            List<WorkflowNodeDefinition> nodes,
            boolean requireLockedAgentVersion) {
        for (WorkflowNodeDefinition node : nodes) {
            require(node.type() != null, "节点类型不能为空: " + node.id());
            require(isNotBlank(node.name()), "节点名称不能为空: " + node.id());
            require(node.config() != null, "节点配置不能为空: " + node.id());
            validateConfigType(node);

            if (node.config() instanceof AgentNodeConfig config) {
                require(config.agentId() != null, "Agent节点缺少agentId: " + node.id());
                require(
                        !requireLockedAgentVersion || config.agentVersionId() != null,
                        "Agent节点必须锁定agentVersionId: " + node.id());
                validateMapping(config.inputMapping(), "Agent输入映射", node.id());
                validateMapping(config.outputMapping(), "Agent输出映射", node.id());
                require(!config.outputMapping().isEmpty(), "Agent节点至少需要一个输出映射: " + node.id());
            } else if (node.config() instanceof ConditionNodeConfig config) {
                require(isNotBlank(config.variable()), "条件节点变量不能为空: " + node.id());
                require(
                        !config.variable().startsWith(RESERVED_VARIABLE_PREFIX),
                        "条件节点不能读取保留变量: " + node.id());
                require(config.operator() != null, "条件节点操作符不能为空: " + node.id());
            } else if (node.config() instanceof EndNodeConfig config) {
                validateMapping(config.outputMapping(), "结束节点输出映射", node.id());
                require(!config.outputMapping().isEmpty(), "END节点至少需要一个输出映射: " + node.id());
            }
        }
    }

    private void validateConfigType(WorkflowNodeDefinition node) {
        WorkflowNodeConfig config = node.config();
        boolean matched = switch (node.type()) {
            case START -> config instanceof StartNodeConfig;
            case AGENT -> config instanceof AgentNodeConfig;
            case CONDITION -> config instanceof ConditionNodeConfig;
            case END -> config instanceof EndNodeConfig;
        };
        require(matched, "节点类型与配置不匹配: " + node.id());
    }

    private void validateMapping(Map<String, String> mapping, String mappingName, String nodeId) {
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            require(isNotBlank(entry.getKey()), mappingName + "名称不能为空: " + nodeId);
            require(isNotBlank(entry.getValue()), mappingName + "变量不能为空: " + nodeId);
            require(
                    !entry.getValue().startsWith(RESERVED_VARIABLE_PREFIX),
                    mappingName + "不能使用保留变量: " + nodeId);
        }
    }

    private void validateEdges(
            List<WorkflowEdgeDefinition> edges,
            Map<String, WorkflowNodeDefinition> nodesById) {
        require(!edges.isEmpty(), "Workflow至少需要一条连线");
        Set<String> edgeKeys = new HashSet<>();
        for (WorkflowEdgeDefinition edge : edges) {
            require(edge != null, "Workflow连线不能为空");
            require(nodesById.containsKey(edge.sourceNodeId()), "连线源节点不存在: " + edge.sourceNodeId());
            require(nodesById.containsKey(edge.targetNodeId()), "连线目标节点不存在: " + edge.targetNodeId());
            require(
                    !Objects.equals(edge.sourceNodeId(), edge.targetNodeId()),
                    "节点不能连接自身: " + edge.sourceNodeId());

            WorkflowNodeDefinition source = nodesById.get(edge.sourceNodeId());
            if (source.type() == WorkflowNodeType.CONDITION) {
                require(edge.route() != null, "条件节点出边必须设置route: " + source.id());
            } else {
                require(edge.route() == null, "非条件节点出边不能设置route: " + source.id());
            }
            String edgeKey = edge.sourceNodeId() + "->" + edge.targetNodeId() + ":" + edge.route();
            require(edgeKeys.add(edgeKey), "Workflow存在重复连线: " + edgeKey);
        }
    }

    private void validateNodeDegrees(
            List<WorkflowNodeDefinition> nodes,
            List<WorkflowEdgeDefinition> edges) {
        Map<String, List<WorkflowEdgeDefinition>> outgoing = groupEdges(edges, WorkflowEdgeDefinition::sourceNodeId);
        Map<String, List<WorkflowEdgeDefinition>> incoming = groupEdges(edges, WorkflowEdgeDefinition::targetNodeId);

        for (WorkflowNodeDefinition node : nodes) {
            List<WorkflowEdgeDefinition> out = outgoing.getOrDefault(node.id(), List.of());
            List<WorkflowEdgeDefinition> in = incoming.getOrDefault(node.id(), List.of());
            switch (node.type()) {
                case START -> {
                    require(in.isEmpty(), "START节点不能有入边");
                    require(out.size() == 1, "START节点必须且只能有一条出边");
                }
                case END -> {
                    require(!in.isEmpty(), "END节点至少需要一条入边");
                    require(out.isEmpty(), "END节点不能有出边");
                }
                case AGENT -> {
                    require(!in.isEmpty(), "Agent节点至少需要一条入边: " + node.id());
                    require(out.size() == 1, "Agent节点必须且只能有一条出边: " + node.id());
                }
                case CONDITION -> {
                    require(!in.isEmpty(), "条件节点至少需要一条入边: " + node.id());
                    require(out.size() == 2, "条件节点必须包含MATCHED和NOT_MATCHED两条出边: " + node.id());
                    EnumSet<WorkflowRoute> routes = out.stream()
                            .map(WorkflowEdgeDefinition::route)
                            .collect(Collectors.toCollection(() -> EnumSet.noneOf(WorkflowRoute.class)));
                    require(
                            routes.equals(EnumSet.allOf(WorkflowRoute.class)),
                            "条件节点分支必须同时包含MATCHED和NOT_MATCHED: " + node.id());
                }
            }
        }
    }

    private Map<String, List<WorkflowEdgeDefinition>> groupEdges(
            List<WorkflowEdgeDefinition> edges,
            java.util.function.Function<WorkflowEdgeDefinition, String> classifier) {
        return edges.stream().collect(Collectors.groupingBy(classifier));
    }

    private void validateReachabilityAndCycles(
            List<WorkflowNodeDefinition> nodes,
            List<WorkflowEdgeDefinition> edges) {
        String startNodeId = nodes.stream()
                .filter(node -> node.type() == WorkflowNodeType.START)
                .findFirst()
                .orElseThrow()
                .id();
        Map<String, List<String>> adjacency = edges.stream().collect(Collectors.groupingBy(
                WorkflowEdgeDefinition::sourceNodeId,
                Collectors.mapping(WorkflowEdgeDefinition::targetNodeId, Collectors.toList())));

        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!reachable.add(current)) {
                continue;
            }
            queue.addAll(adjacency.getOrDefault(current, List.of()));
        }
        List<String> unreachable = nodes.stream()
                .map(WorkflowNodeDefinition::id)
                .filter(Predicate.not(reachable::contains))
                .toList();
        require(unreachable.isEmpty(), "Workflow存在START不可达节点: " + unreachable);

        Map<String, Integer> indegree = new HashMap<>();
        nodes.forEach(node -> indegree.put(node.id(), 0));
        edges.forEach(edge -> indegree.compute(edge.targetNodeId(), (key, value) -> value + 1));
        ArrayDeque<String> zeroIndegree = new ArrayDeque<>();
        indegree.forEach((nodeId, degree) -> {
            if (degree == 0) {
                zeroIndegree.add(nodeId);
            }
        });
        int visited = 0;
        while (!zeroIndegree.isEmpty()) {
            String current = zeroIndegree.removeFirst();
            visited++;
            for (String target : adjacency.getOrDefault(current, List.of())) {
                int updated = indegree.computeIfPresent(target, (key, value) -> value - 1);
                if (updated == 0) {
                    zeroIndegree.add(target);
                }
            }
        }
        require(visited == nodes.size(), "POC阶段Workflow不允许存在环路");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new WorkflowDefinitionException(message);
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
