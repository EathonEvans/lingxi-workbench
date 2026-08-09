package com.lingxi.workbench.workflow.engine.langgraph;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import com.lingxi.workbench.workflow.exception.WorkflowDefinitionException;
import com.lingxi.workbench.workflow.exception.WorkflowExecutionException;
import com.lingxi.workbench.workflow.model.AgentNodeInvocation;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeInvocation;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowEdgeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowExecutionRequest;
import com.lingxi.workbench.workflow.model.WorkflowExecutionResult;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeStatus;
import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.WorkflowRoute;
import com.lingxi.workbench.workflow.model.WorkflowRunContext;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.model.config.ConditionNodeConfig;
import com.lingxi.workbench.workflow.model.config.EndNodeConfig;
import com.lingxi.workbench.workflow.model.config.IntentClassifierNodeConfig;
import com.lingxi.workbench.workflow.service.AgentNodeInvoker;
import com.lingxi.workbench.workflow.service.IntentClassifierNodeInvoker;
import com.lingxi.workbench.workflow.service.WorkflowDefinitionValidator;
import com.lingxi.workbench.workflow.service.WorkflowExecutor;
import com.lingxi.workbench.workflow.service.WorkflowRunListener;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.Channels;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.lingxi.workbench.workflow.common.constants.WorkflowStateConstants.EXECUTION_ID_KEY;
import static com.lingxi.workbench.workflow.common.constants.WorkflowStateConstants.TRACES_KEY;
import static com.lingxi.workbench.workflow.common.constants.WorkflowStateConstants.WORKFLOW_VERSION_ID_KEY;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 基于 LangGraph4j Core 的工作流执行适配器，实现 DAG 拓扑排序调度。
 *
 * <h3>执行模型</h3>
 * <ol>
 *   <li><b>编译阶段</b>：将 {@link WorkflowDefinition} 的节点和连线编译为 LangGraph4j StateGraph：
 *     <ul>
 *       <li>START 节点 → 按连线拓扑依次调度后续节点；</li>
 *       <li>INTENT_CLASSIFIER 节点 → 调用锁定的意图树版本并输出标准识别结果；</li>
 *       <li>AGENT 节点 → 通过 {@link AgentNodeInvoker} 调用对应 Agent 版本执行推理；</li>
 *       <li>CONDITION 节点 → 根据变量值和操作符计算匹配分支，使用 conditional edge 路由；</li>
 *       <li>END 节点 → 按 outputMapping 提取最终输出。</li>
 *     </ul>
 *   </li>
 *   <li><b>执行阶段</b>：LangGraph4j 根据编译后的有向图按拓扑顺序自动调度节点，
 *       每个节点的输出写入全局状态，作为下游节点的输入。</li>
 *   <li><b>追踪</b>：每个节点执行时记录输入、输出、开始时间、结束时间和状态，
 *       聚合为 {@link WorkflowNodeTrace} 列表，跟随状态流转。</li>
 * </ol>
 *
 * <p>领域模型、Workflow 版本及节点锁定的资源版本均不依赖 LangGraph4j，该适配器是唯一的引擎依赖点。</p>
 */
public class LangGraphWorkflowExecutor implements WorkflowExecutor {

    private final WorkflowDefinitionValidator validator;
    private final AgentNodeInvoker agentNodeInvoker;
    private final IntentClassifierNodeInvoker intentClassifierNodeInvoker;
    private final WorkflowRunListener runListener;

    public LangGraphWorkflowExecutor(AgentNodeInvoker agentNodeInvoker, IntentClassifierNodeInvoker intentClassifierNodeInvoker) {
        this(new WorkflowDefinitionValidator(), agentNodeInvoker, intentClassifierNodeInvoker, WorkflowRunListener.noop());
    }

    public LangGraphWorkflowExecutor(AgentNodeInvoker agentNodeInvoker, IntentClassifierNodeInvoker intentClassifierNodeInvoker, WorkflowRunListener runListener) {
        this(new WorkflowDefinitionValidator(), agentNodeInvoker, intentClassifierNodeInvoker, runListener);
    }

    public LangGraphWorkflowExecutor(WorkflowDefinitionValidator validator, AgentNodeInvoker agentNodeInvoker, IntentClassifierNodeInvoker intentClassifierNodeInvoker, WorkflowRunListener runListener) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.agentNodeInvoker = Objects.requireNonNull(agentNodeInvoker, "agentNodeInvoker");
        this.intentClassifierNodeInvoker = Objects.requireNonNull(intentClassifierNodeInvoker, "intentClassifierNodeInvoker");
        this.runListener = Objects.requireNonNullElseGet(runListener, WorkflowRunListener::noop);
    }

    /**
     * 执行一次完整的工作流运行。
     *
     * <h3>执行链路</h3>
     * <pre>
     * execute(request)
     *   │
     *   ├─ [1] 校验阶段
     *   │   ├─ validator.validate(definition)   -- 校验工作流定义的结构合法性
     *   │   │   ├─ 校验 schemaVersion 是否为支持的版本（当前为 1）
     *   │   │   ├─ 校验 code 非空
     *   │   │   ├─ 校验节点数量：必须有且仅有一个 START 和一个 END
     *   │   │   ├─ 校验每个节点：ID 非空不重复、类型非空、名称非空、配置非空、类型与配置匹配
     *   │   │   ├─ 校验连线：源/目标节点存在、不连接自身、条件边有 route、非条件边无 route、无重复连线
     *   │   │   ├─ 校验出入度：START 无入边有1出边、END 有入边无出边、执行节点有入边有1出边、CONDITION 有入边有2出边
     *   │   │   └─ 校验可达性与无环：所有节点从 START 可达，节点集合形成 DAG 无环路
     *   │   │
     *   │   └─ validateRequest(request)           -- 校验执行请求参数合法性
     *   │       ├─ executionId 非空非空白
     *   │       ├─ workflowId / workflowVersionId 非空
     *   │       └─ inputs 的 key 不以下划线开头（保留下划线前缀给系统内部变量）
     *   │
     *   ├─ [2] 构建初始状态
     *   │   ├─ 将 request.inputs() 复制到 initialState（保留用户传入的业务变量）
     *   │   ├─ 注入 _executionId → 当前执行的唯一标识，贯穿全链路
     *   │   └─ 注入 _workflowVersionId → 锁定本次执行绑定的 Workflow 发布版本
     *   │
     *   ├─ [3] 编译阶段 compile(definition)
     *   │   ├─ 创建 StateGraph，注册 _traces 为 appender 通道（追加模式收集所有节点追踪）
     *   │   ├─ 遍历所有节点，为每个节点注册异步执行函数 node_async(state -> executeNode(node, state))
     *   │   ├─ 定位 START 节点，添加 START → startNode.id() 的系统入口边
     *   │   ├─ 定位 END 节点，添加 endNode.id() → END 的系统出口边
     *   │   ├─ 按 sourceNodeId 对所有连线分组，构建每个源节点到其目标边的映射
     *   │   ├─ 遍历所有源节点，按类型注册边：
     *   │   │   ├─ CONDITION 节点：注册 conditional edge，由 edge_async(state -> evaluateCondition(node, state))
     *   │   │   │   动态计算路由结果（MATCHED / NOT_MATCHED），映射到对应目标节点
     *   │   │   └─ 非 CONDITION 节点：注册普通边，直接连接到下一节点
     *   │   └─ 调用 graph.compile() 完成编译，LangGraph4j 内部验证图结构并构建拓扑调度计划
     *   │
     *   ├─ [4] 执行阶段 graph.invoke(initialState)
     *   │   └─ LangGraph4j 按编译后的拓扑顺序自动调度节点，对每个节点执行：
     *   │       executeNode(node, state)
     *   │         ├─ 记录节点开始时间 startedAt = Instant.now()
     *   │         ├─ 调用 nodeInputs(node, state) 提取该节点的输入变量
     *   │         │   ├─ AGENT 节点：按 config.inputMapping 从全局状态中解析输入
     *   │         │   ├─ INTENT_CLASSIFIER 节点：按 config.queryVariable 提取待识别文本
     *   │         │   ├─ CONDITION 节点：提取观测变量 config.variable 的当前值
     *   │         │   ├─ END 节点：按 config.outputMapping 从全局状态中解析变量
     *   │         │   └─ 其他节点：透传全部非保留变量（key 不以下划线开头）
     *   │         │
     *   │         ├─ 按节点类型分发执行：
     *   │         │   ├─ START / CONDITION / END → 返回空 Map（无业务输出）
     *   │         │   ├─ INTENT_CLASSIFIER → executeIntentClassifier(node, state, inputs)
     *   │         │   └─ AGENT → executeAgent(node, state, inputs)
     *   │         │       ├─ 从 node.config 中解析 AgentNodeConfig
     *   │         │       ├─ 构建 AgentNodeInvocation 调用上下文
     *   │         │       │   ├─ executionId：当前执行 ID
     *   │         │       │   ├─ workflowVersionId：Workflow 发布版本 ID
     *   │         │       │   ├─ nodeId：当前节点 ID
     *   │         │       │   ├─ agentId / agentVersionId：从 config 解析为 Long
     *   │         │       │   └─ inputs：节点输入变量
     *   │         │       ├─ 调用 agentNodeInvoker.invoke(invocation) 执行 Agent 推理
     *   │         │       │   返回 Agent 原始输出 Map（如 {"reply": "你好", "score": 0.9}）
     *   │         │       └─ 按 config.outputMapping 将 Agent 输出映射为工作流变量
     *   │         │           （如 {"reply" → "final_answer"}，则工作流变量 final_answer = "你好"）
     *   │         │
     *   │         ├─ 构建 WorkflowNodeTrace 记录本次节点执行信息：
     *   │         │   ├─ nodeId / nodeType：节点标识
     *   │         │   ├─ status：SUCCEEDED / FAILED
     *   │         │   ├─ startedAt / finishedAt：执行时间窗口
     *   │         │   ├─ inputs：节点接收的输入
     *   │         │   └─ outputs：节点产生的输出
     *   │         │
     *   │         └─ 返回状态更新 Map：
     *   │             ├─ 合并节点输出变量到全局状态
     *   │             └─ 追加 trace 到 _traces 通道（appender 模式自动合并）
     *   │
     *   ├─ [5] 结果收集
     *   │   ├─ graph.invoke() 返回 Optional&lt;WorkflowGraphState&gt;，为 null 时抛出 WorkflowExecutionException
     *   │   └─ resolveWorkflowOutputs(definition, finalState) 从最终状态提取工作流输出
     *   │       ├─ 定位 END 节点的 EndNodeConfig
     *   │       └─ 按 outputMapping 从全局状态提取最终输出变量，返回不可变 Map
     *   │
     *   └─ [6] 返回结果
     *       └─ new WorkflowExecutionResult(executionId, outputs, traces)
     *           ├─ executionId：执行标识
     *           ├─ outputs：END 节点提取的最终输出
     *           └─ traces：全部节点的执行追踪记录
     *
     *       异常路径：
     *         ├─ WorkflowDefinitionException / WorkflowExecutionException → 直接重新抛出
     *         └─ 其他异常 → 提取根因，包装为 WorkflowExecutionException 抛出
     * </pre>
     *
     * @param definition 不可变的工作流定义快照，包含节点列表和连线列表
     * @param request    执行请求，包含 executionId、workflowId、workflowVersionId 和初始输入变量
     * @return 执行结果，包含执行 ID、最终输出和节点追踪列表
     */
    @Override
    public WorkflowExecutionResult execute(WorkflowDefinition definition, WorkflowExecutionRequest request) {
        // [1] 校验阶段：先校验工作流定义结构，再校验执行请求参数
        validator.validate(definition);
        validateRequest(request);

        // [2] 构建初始状态：将请求输入复制到全局状态，并注入系统保留变量
        // 系统保留变量以 _ 开头，用于全链路追踪和版本锁定
        Map<String, Object> initialState = new LinkedHashMap<>(request.inputs());
        initialState.put(EXECUTION_ID_KEY, request.executionId());
        initialState.put(WORKFLOW_VERSION_ID_KEY, request.workflowVersionId());

        WorkflowRunContext runContext = WorkflowRunContext.builder()
                .runId(IdUtil.getSnowflakeNextIdStr())
                .definition(definition)
                .request(request)
                .startedAt(Instant.now())
                .build();
        runListener.onRunStarted(runContext);

        try {
            // [3] 编译阶段：将灵析工作流定义编译为 LangGraph4j 可执行有向图
            CompiledGraph<WorkflowGraphState> graph = compile(definition, runContext);
            // [4] 执行阶段 + [5] 结果收集：LangGraph4j 按拓扑顺序调度所有节点
            WorkflowGraphState finalState = graph.invoke(initialState)
                    .orElseThrow(() -> new WorkflowExecutionException("Workflow没有产生最终状态"));
            // [6] 返回结果：聚合执行 ID、最终输出和全部节点追踪
            WorkflowExecutionResult result = new WorkflowExecutionResult(request.executionId(), resolveWorkflowOutputs(definition, finalState), finalState.traces());
            runListener.onRunSucceeded(runContext, result);
            return result;
        } catch (WorkflowDefinitionException | WorkflowExecutionException exception) {
            // 业务异常直接重新抛出，保留原始错误码和上下文
            notifyRunFailed(runContext, exception);
            throw exception;
        } catch (Exception exception) {
            // 非预期异常（如网络错误、序列化失败）提取根因后包装为统一业务异常
            WorkflowExecutionException wrapped = new WorkflowExecutionException("Workflow执行失败: " + definition.code(), rootCause(exception));
            notifyRunFailed(runContext, wrapped);
            throw wrapped;
        }
    }

    /**
     * 将工作流定义编译为 LangGraph4j 可执行图。
     *
     * <h3>编译步骤</h3>
     * <ol>
     *   <li>创建 StateGraph，为 _traces 注册 appender 通道：
     *       appender 通道确保各节点并行执行时，追踪记录的追加操作是线程安全的累加而非覆盖。</li>
     *   <li>遍历 definition.nodes()，为每个节点注册 node_async 异步执行函数。</li>
     *   <li>定位 START 和 END 节点，添加系统入口边和出口边。</li>
     *   <li>按 sourceNodeId 对连线分组，构建节点的出边映射。</li>
     *   <li>对 CONDITION 节点注册 conditional edge（动态路由），其余节点注册普通边（固定路由）。</li>
     *   <li>调用 graph.compile() 触发 LangGraph4j 内部图验证和拓扑排序。</li>
     * </ol>
     *
     * @param definition 工作流定义
     * @return 编译后的可执行图
     * @throws GraphStateException 图结构不合法时抛出
     */
    private CompiledGraph<WorkflowGraphState> compile(WorkflowDefinition definition, WorkflowRunContext runContext) throws GraphStateException {
        // 创建状态图，TRACES_KEY 使用 appender 通道：各节点追加 trace 而非覆盖
        StateGraph<WorkflowGraphState> graph = new StateGraph<>(Map.of(TRACES_KEY, Channels.appender(ArrayList::new)), WorkflowGraphState::new);

        // 注册所有节点：每个节点绑定 node_async(state -> executeNode(node, state))，
        // LangGraph4j 在调度到该节点时执行此函数
        for (WorkflowNodeDefinition node : definition.nodes()) {
            graph.addNode(node.id(), node_async(state -> executeNode(runContext, node, state)));
        }

        // 系统入口边：START → 工作流第一个节点（唯一 START 节点的执行入口）
        WorkflowNodeDefinition startNode = findNode(definition, WorkflowNodeType.START);
        WorkflowNodeDefinition endNode = findNode(definition, WorkflowNodeType.END);
        graph.addEdge(START, startNode.id());

        // 系统出口边：END 节点 → 图结束标记，执行到 END 节点后终止
        graph.addEdge(endNode.id(), END);

        // 按节点 ID 建立索引，用于后续边注册时快速查找目标节点
        Map<String, WorkflowNodeDefinition> nodesById = definition.nodes()
                .stream()
                .collect(Collectors.toMap(WorkflowNodeDefinition::id, node -> node));

        // 按 sourceNodeId 对所有连线分组，得到每个源节点的出边列表
        Map<String, List<WorkflowEdgeDefinition>> outgoing = definition.edges()
                .stream()
                .collect(Collectors.groupingBy(WorkflowEdgeDefinition::sourceNodeId));

        // 遍历所有源节点，根据节点类型注册不同种类的边
        for (WorkflowNodeDefinition source : definition.nodes()) {
            List<WorkflowEdgeDefinition> edges = outgoing.getOrDefault(source.id(), List.of());
            if (source.type() == WorkflowNodeType.CONDITION) {
                // 条件节点：注册 conditional edge，运行时由 evaluateCondition 动态决定走 MATCHED 还是 NOT_MATCHED 分支
                Map<String, String> routes = new LinkedHashMap<>();
                edges.forEach(edge -> routes.put(edge.route().name(), edge.targetNodeId()));
                graph.addConditionalEdges(source.id(), edge_async(state -> evaluateCondition(source, state).name()), routes);
            } else {
                // 非条件节点：注册普通边，固定路由到下一节点
                edges.forEach(edge -> {
                    try {
                        graph.addEdge(source.id(), nodesById.get(edge.targetNodeId()).id());
                    } catch (GraphStateException exception) {
                        throw new WorkflowDefinitionException("Workflow连线编译失败", exception);
                    }
                });
            }
        }

        // 编译图：LangGraph4j 内部校验节点可达性、边完整性，并构建拓扑调度计划
        return graph.compile();
    }

    /**
     * 执行单个节点，根据节点类型分发处理。
     *
     * <h3>执行流程</h3>
     * <ol>
     *   <li>记录节点开始时间（用于追踪耗时）。</li>
     *   <li>调用 {@link #nodeInputs(WorkflowNodeDefinition, WorkflowGraphState)} 从全局状态提取节点输入。</li>
     *   <li>按节点类型分发：
     *     <ul>
     *       <li>START / CONDITION / END → 无业务输出，返回空 Map；</li>
     *       <li>INTENT_CLASSIFIER → 调用锁定的意图树版本执行识别；</li>
     *       <li>AGENT → 调用 {@link #executeAgent(WorkflowNodeDefinition, WorkflowGraphState, Map)} 执行 Agent 推理。</li>
     *     </ul>
     *   </li>
     *   <li>构建 {@link WorkflowNodeTrace} 记录输入、输出和时间信息。</li>
     *   <li>返回状态更新 Map：合并节点输出到全局状态，追加追踪记录到 _traces 通道。</li>
     * </ol>
     *
     * @param node  当前执行的节点定义
     * @param state 运行时的全局状态（已包含上游节点输出和系统变量）
     * @return 状态更新 Map，包含本节点的输出变量和追踪记录
     */
    private Map<String, Object> executeNode(WorkflowRunContext runContext, WorkflowNodeDefinition node, WorkflowGraphState state) {
        // 记录节点开始时间，用于后续计算执行耗时
        Instant startedAt = Instant.now();
        runListener.onNodeStarted(runContext, node, startedAt);

        Map<String, Object> inputs = Map.of();
        try {
            // 从全局状态中提取本节点的输入变量
            inputs = nodeInputs(node, state);

            // START / CONDITION / END 节点无业务输出，其他节点调用锁定的运行时能力
            Map<String, Object> outputs = switch (node.type()) {
                case START, CONDITION, END -> Map.of();
                case INTENT_CLASSIFIER -> executeIntentClassifier(node, state, inputs);
                case AGENT -> executeAgent(node, state, inputs);
            };
            WorkflowNodeTrace trace = nodeTrace(node, WorkflowNodeStatus.SUCCEEDED, startedAt, inputs, outputs, null);
            runListener.onNodeSucceeded(runContext, node, trace);

            // 合并节点输出变量，并通过 appender 通道追加节点追踪
            Map<String, Object> stateUpdates = new LinkedHashMap<>(outputs);
            stateUpdates.put(TRACES_KEY, List.of(trace));
            return stateUpdates;
        } catch (RuntimeException exception) {
            WorkflowNodeTrace trace = nodeTrace(node, WorkflowNodeStatus.FAILED, startedAt, inputs, Map.of(), rootCause(exception).getMessage());
            notifyNodeFailed(runContext, node, trace, exception);
            throw exception;
        }
    }

    private static WorkflowNodeTrace nodeTrace(WorkflowNodeDefinition node, WorkflowNodeStatus status, Instant startedAt, Map<String, Object> inputs, Map<String, Object> outputs, String errorMessage) {
        return WorkflowNodeTrace.builder()
                .nodeId(node.id())
                .nodeType(node.type())
                .status(status)
                .startedAt(startedAt)
                .finishedAt(Instant.now())
                .inputs(inputs)
                .outputs(outputs)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 调用 Agent 版本执行推理。
     *
     * <h3>执行流程</h3>
     * <ol>
     *   <li>从节点配置中提取 AgentNodeConfig，获取 agentId、agentVersionId 和 outputMapping。</li>
     *   <li>构建 {@link AgentNodeInvocation}，封装执行 ID、节点 ID、Agent ID/版本和节点输入。</li>
     *   <li>调用 {@link AgentNodeInvoker#invoke(AgentNodeInvocation)} 执行 Agent 推理。</li>
     *   <li>将 Agent 输出按 outputMapping 映射为工作流变量：
     *       outputMapping 是 "Agent输出变量名 → 工作流变量名" 的映射，
     *       例如 {"reply" → "final_answer"} 表示将 Agent 输出的 "reply" 映射为工作流变量 "final_answer"。
     *       如果 Agent 输出中缺少 outputMapping 声明的 key，抛出 WorkflowExecutionException。</li>
     * </ol>
     *
     * @param node   当前 Agent 节点定义
     * @param state  运行时全局状态
     * @param inputs 本节点的输入变量（已按 inputMapping 解析）
     * @return 映射后的工作流变量 Map
     * @throws WorkflowExecutionException Agent 输出缺少声明的 key 时抛出
     */
    private Map<String, Object> executeAgent(WorkflowNodeDefinition node, WorkflowGraphState state, Map<String, Object> inputs) {
        AgentNodeConfig config = (AgentNodeConfig) node.config();

        // 构建 Agent 调用上下文，包括执行 ID、版本 ID、节点 ID、Agent ID/版本和输入
        AgentNodeInvocation invocation = AgentNodeInvocation.builder()
                .executionId(state.executionId())
                .workflowVersionId(state.workflowVersionId())
                .nodeId(node.id())
                .agentId(config.agentId())
                .agentVersionId(config.agentVersionId())
                .inputs(inputs)
                .build();

        // 调用 Agent 运行时执行推理，获取 Agent 原始输出
        Map<String, Object> agentOutputs = agentNodeInvoker.invoke(invocation);

        // 按 outputMapping 将 Agent 输出映射为工作流变量
        // outputMapping.key = Agent 输出变量名，outputMapping.value = 工作流变量名
        return mapOutputs("Agent", node.id(), agentOutputs, config.outputMapping());
    }

    /** 调用锁定的意图树版本并将标准识别结果写入工作流变量。 */
    private Map<String, Object> executeIntentClassifier(WorkflowNodeDefinition node, WorkflowGraphState state, Map<String, Object> inputs) {
        IntentClassifierNodeConfig config = (IntentClassifierNodeConfig) node.config();
        Object queryValue = inputs.get("query");
        if (!(queryValue instanceof String query) || query.isBlank()) {
            throw new WorkflowExecutionException("意图识别节点query必须是非空字符串: " + node.id());
        }
        IntentClassifierNodeInvocation invocation = IntentClassifierNodeInvocation.builder()
                .executionId(state.executionId())
                .nodeId(node.id())
                .intentTreeId(config.intentTreeId())
                .intentTreeVersionId(config.intentTreeVersionId())
                .query(query)
                .build();
        Map<String, Object> outputs = intentClassifierNodeInvoker.invoke(invocation);
        return mapOutputs("意图识别", node.id(), outputs, config.outputMapping());
    }

    private Map<String, Object> mapOutputs(String nodeType, String nodeId, Map<String, Object> outputs, Map<String, String> mapping) {
        Map<String, Object> workflowOutputs = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (!outputs.containsKey(entry.getKey())) {
                throw new WorkflowExecutionException(CharSequenceUtil.format(
                        "{}节点缺少声明的输出 {}: {}", nodeType, entry.getKey(), nodeId));
            }
            workflowOutputs.put(entry.getValue(), outputs.get(entry.getKey()));
        }
        return workflowOutputs;
    }

    /**
     * 根据节点类型从工作流全局状态中提取该节点的输入变量。
     *
     * <h3>分发逻辑</h3>
     * <ul>
     *   <li>AGENT 节点 → 按 AgentNodeConfig.inputMapping 从状态中解析，
     *       inputMapping 是 "输入参数名 → 工作流变量名" 的映射，
     *       例如 {"query" → "user_question"} 表示从全局状态取 user_question 的值赋给输入参数 query。</li>
     *   <li>CONDITION 节点 → 只提取观测变量 config.variable 的当前值。</li>
     *   <li>END 节点 → 按 EndNodeConfig.outputMapping 从状态中解析需要的变量。</li>
     *   <li>其他节点 → 透传全部非系统保留变量（key 不以下划线开头）。</li>
     * </ul>
     *
     * @param node  当前节点定义
     * @param state 运行时全局状态
     * @return 节点输入变量 Map
     */
    private Map<String, Object> nodeInputs(WorkflowNodeDefinition node, WorkflowGraphState state) {
        if (node.config() instanceof AgentNodeConfig config) {
            // AGENT 节点：按 inputMapping 解析，例如 {"query" → "user_question"} 从状态中取 user_question 的值
            return resolveInputMapping(config.inputMapping(), node.id(), state);
        }
        if (node.config() instanceof IntentClassifierNodeConfig config) {
            return Map.of("query", requireStateValue(state, config.queryVariable(), node.id()));
        }
        if (node.config() instanceof ConditionNodeConfig config) {
            // CONDITION 节点：只需要观测变量的当前值
            return Map.of(config.variable(), requireStateValue(state, config.variable(), node.id()));
        }
        if (node.config() instanceof EndNodeConfig(Map<String, String> outputMapping)) {
            // END 节点：按 outputMapping 从状态中提取最终输出变量
            return resolveInputMapping(outputMapping, node.id(), state);
        }
        // 没有特殊配置的节点（如 START）：透传全部非系统保留变量
        return state.data().entrySet().stream().filter(entry -> !entry.getKey().startsWith("_")).collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 将映射表（inputName → variableName）解析为（inputName → 变量值）的 Map。
     *
     * <p>遍历映射的每一对，从全局状态中安全获取变量值。
     * 如果变量不存在，{@link #requireStateValue} 会抛出 WorkflowExecutionException。</p>
     *
     * @param mapping 输入名称到状态变量名的映射
     * @param nodeId  节点 ID，用于错误定位
     * @param state   运行时全局状态
     * @return 解析后的输入变量 Map
     */
    private Map<String, Object> resolveInputMapping(Map<String, String> mapping, String nodeId, WorkflowGraphState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        mapping.forEach((inputName, variableName) -> result.put(inputName, requireStateValue(state, variableName, nodeId)));
        return result;
    }

    /**
     * 评估条件节点的分支方向。
     *
     * <h3>评估流程</h3>
     * <ol>
     *   <li>从全局状态中获取条件变量 config.variable 的实际值。</li>
     *   <li>与 config.expectedValue 按 config.operator 进行比较。</li>
     *   <li>返回 MATCHED 或 NOT_MATCHED 路由。</li>
     * </ol>
     *
     * <p>当前支持的操作符：
     * <ul>
     *   <li>EQUALS → 变量值 equals 预期值；</li>
     *   <li>NOT_EQUALS → 变量值不等于预期值。</li>
     * </ul>
     * 条件求值使用 {@link Objects#equals}，安全处理 null。</p>
     *
     * @param node  条件节点定义，包含观测变量、操作符和预期值
     * @param state 运行时全局状态
     * @return MATCHED 或 NOT_MATCHED 路由
     */
    private WorkflowRoute evaluateCondition(WorkflowNodeDefinition node, WorkflowGraphState state) {
        ConditionNodeConfig config = (ConditionNodeConfig) node.config();

        // 从全局状态中获取条件变量的实际值
        Object actualValue = requireStateValue(state, config.variable(), node.id());

        // 按操作符判断是否匹配，使用 Objects.equals 安全处理 null
        boolean matched = switch (config.operator()) {
            case EQUALS -> Objects.equals(actualValue, config.expectedValue());
            case NOT_EQUALS -> !Objects.equals(actualValue, config.expectedValue());
        };

        // 返回路由方向，LangGraph4j 的 conditional edge 据此选择下一节点
        if (matched) {
            return WorkflowRoute.MATCHED;
        }
        return WorkflowRoute.NOT_MATCHED;
    }

    /**
     * 按 END 节点的 outputMapping 从最终状态中提取工作流输出变量。
     *
     * <p>outputMapping 的 key 为最终输出变量名，value 为全局状态中的变量名。
     * 例如 {"result" → "final_answer"} 表示从状态取 final_answer 作为结果中的 result。</p>
     *
     * @param definition 工作流定义
     * @param finalState 执行完成后的最终全局状态
     * @return 不可变的工作流输出 Map
     */
    private Map<String, Object> resolveWorkflowOutputs(WorkflowDefinition definition, WorkflowGraphState finalState) {
        EndNodeConfig config = (EndNodeConfig) findNode(definition, WorkflowNodeType.END).config();
        Map<String, Object> outputs = new LinkedHashMap<>();
        config.outputMapping().forEach((outputName, variableName) -> outputs.put(outputName, requireStateValue(finalState, variableName, "END")));
        return Collections.unmodifiableMap(outputs);
    }

    /**
     * 从全局状态中安全获取变量值。
     *
     * <p>委托给 {@link WorkflowGraphState#value(String)} 方法，
     * 变量不存在时抛出 WorkflowExecutionException，包含变量名和节点 ID 便于排查。</p>
     *
     * @param state    运行时全局状态
     * @param variable 变量名
     * @param nodeId   节点 ID，用于错误定位
     * @return 变量值，不为 null
     * @throws WorkflowExecutionException 变量不存在时抛出
     */
    private Object requireStateValue(WorkflowGraphState state, String variable, String nodeId) {
        return state.value(variable).orElseThrow(() -> new WorkflowExecutionException("节点读取不到工作流变量 " + variable + ": " + nodeId));
    }

    /**
     * 按类型在工作流定义中查找唯一节点。
     *
     * <p>每种类型（START / END）应只有一个节点，由校验器在编译前保证。
     * 找不到时抛出 WorkflowDefinitionException。</p>
     *
     * @param definition 工作流定义
     * @param type       要查找的节点类型
     * @return 匹配的节点定义
     * @throws WorkflowDefinitionException 未找到匹配节点时抛出
     */
    private WorkflowNodeDefinition findNode(WorkflowDefinition definition, WorkflowNodeType type) {
        return definition.nodes().stream().filter(node -> node.type() == type).findFirst().orElseThrow(() -> new WorkflowDefinitionException("Workflow缺少" + type + "节点"));
    }

    /**
     * 校验执行请求参数的合法性。
     *
     * <p>校验规则：
     * <ul>
     *   <li>executionId 非空非空白，是本次执行的全链路唯一标识；</li>
     *   <li>workflowId 和 workflowVersionId 非空，锁定执行时使用的 Workflow 发布版本；</li>
     *   <li>inputs 的 key 不得以下划线 _ 开头，避免与系统保留变量冲突。</li>
     * </ul>
     *
     * @param request 执行请求
     * @throws WorkflowExecutionException 校验不通过时抛出
     */
    private void validateRequest(WorkflowExecutionRequest request) {
        if (request == null || request.executionId() == null || request.executionId().isBlank()) {
            throw new WorkflowExecutionException("Workflow executionId不能为空");
        }
        if (request.workflowId() == null || request.workflowVersionId() == null) {
            throw new WorkflowExecutionException("Workflow及其锁定版本不能为空");
        }
        if (request.inputs().keySet().stream().anyMatch(key -> key.startsWith("_"))) {
            throw new WorkflowExecutionException("Workflow输入变量不能以下划线开头");
        }
    }

    private void notifyNodeFailed(WorkflowRunContext context, WorkflowNodeDefinition node, WorkflowNodeTrace trace, RuntimeException original) {
        try {
            runListener.onNodeFailed(context, node, trace);
        } catch (RuntimeException listenerError) {
            original.addSuppressed(listenerError);
        }
    }

    private void notifyRunFailed(WorkflowRunContext context, RuntimeException original) {
        try {
            runListener.onRunFailed(context, original);
        } catch (RuntimeException listenerError) {
            original.addSuppressed(listenerError);
        }
    }

    /**
     * 提取异常链的根因。
     *
     * <p>沿着 getCause() 链向下追溯，找到最底层的原始异常。
     * 排除循环引用（cause == this）防止无限循环。</p>
     *
     * @param exception 当前异常
     * @return 异常链中最底层的根因
     */
    private Throwable rootCause(Throwable exception) {
        Throwable result = exception;
        while (result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }
        return result;
    }
}
