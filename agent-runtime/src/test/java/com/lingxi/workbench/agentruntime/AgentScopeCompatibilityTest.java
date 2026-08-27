package com.lingxi.workbench.agentruntime;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AgentScope 进入生产依赖前的版本与关键扩展点兼容门禁。 */
class AgentScopeCompatibilityTest {

    @Test
    void shouldNotDeclareDirectSpringAiDependencies() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        Pattern directDependency = Pattern.compile(
                "<dependency>\\s*<groupId>org\\.springframework\\.ai</groupId>");
        assertFalse(directDependency.matcher(pom).find(),
                "Agent Runtime 不得直接声明 Spring AI 依赖");
    }

    @Test
    void shouldKeepSpringAiImportsOutsideAgentRuntimeProductionSources() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations;
        try (java.util.stream.Stream<Path> sources = Files.walk(sourceRoot)) {
            violations = sources.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> imports(path).stream())
                    .filter(line -> line.startsWith("import org.springframework.ai"))
                    .toList();
        }

        assertEquals(List.of(), violations,
                "Agent Runtime 必须通过 AgentScope 或 infra-ai 稳定契约调用 AI 能力");
    }

    @Test
    void shouldStreamTypedEventsAndExposeTypedRuntimeContextToMiddleware() {
        RuntimeMarker marker = new RuntimeMarker("run-1");
        AtomicBoolean middlewareCalled = new AtomicBoolean();
        MiddlewareBase middleware = new MiddlewareBase() {
            @Override
            public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                                Function<ModelCallInput, Flux<AgentEvent>> next) {
                assertSame(marker, context.get(RuntimeMarker.class));
                middlewareCalled.set(true);
                return next.apply(input);
            }
        };

        try (ReActAgent agent = ReActAgent.builder()
                .name("lingxi-compatibility")
                .sysPrompt("Return a concise answer.")
                .model(new StubChatModel())
                .maxIters(2)
                .middleware(middleware)
                .build()) {
            RuntimeContext context = RuntimeContext.builder()
                    .userId("user-1")
                    .sessionId("session-1")
                    .build();
            context.put(RuntimeMarker.class, marker);

            List<AgentEventType> eventTypes = agent.streamEvents("hello", context)
                    .map(AgentEvent::getType)
                    .collectList()
                    .block();

            assertTrue(middlewareCalled.get());
            assertTrue(eventTypes.contains(AgentEventType.AGENT_START));
            assertTrue(eventTypes.contains(AgentEventType.MODEL_CALL_START));
            assertTrue(eventTypes.contains(AgentEventType.AGENT_RESULT));
            assertTrue(eventTypes.contains(AgentEventType.AGENT_END));
        }
    }

    @Test
    void shouldBuildOpenAiCompatibleModelFromLingxiPublishedConfigShape() {
        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey("compatibility-test-key")
                .baseUrl("http://127.0.0.1:1/v1")
                .modelName("compatibility-model")
                .stream(true)
                .build();

        assertEquals("compatibility-model", model.getModelName());
    }

    @Test
    void shouldBuildAndRunOfficialHarnessOnSharedClasspath() {
        try (HarnessAgent agent = HarnessAgent.builder()
                .name("lingxi-harness-compatibility")
                .sysPrompt("Return a concise answer.")
                .model(new StubChatModel())
                .maxIters(2)
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableSessionPersistence()
                .disableWorkspaceContext()
                .disableAtPathExpansion()
                .disableSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableDynamicSubagents()
                .disableToolsConfig()
                .disableCompaction()
                .disableToolResultEviction()
                .stateStore(new InMemoryAgentStateStore())
                .build()) {
            RuntimeContext context = RuntimeContext.builder()
                    .userId("user-1")
                    .sessionId("harness-session-1")
                    .build();

            List<AgentEventType> eventTypes = agent.streamEvents("hello", context)
                    .map(AgentEvent::getType)
                    .collectList()
                    .block();

            assertTrue(eventTypes.contains(AgentEventType.AGENT_RESULT));
            assertEquals("lingxi-harness-compatibility", agent.getName());
        }
    }

    private static final class StubChatModel extends ChatModelBase {

        @Override
        public String getModelName() {
            return "compatibility-model";
        }

        @Override
        protected Flux<ChatResponse> doStream(List<io.agentscope.core.message.Msg> messages,
                                              List<ToolSchema> tools, GenerateOptions options) {
            ContentBlock content = TextBlock.builder().text("ok").build();
            ChatUsage usage = ChatUsage.builder().inputTokens(1).outputTokens(1).build();
            return Flux.just(ChatResponse.builder()
                    .content(List.of(content))
                    .usage(usage)
                    .finishReason("stop")
                    .build());
        }
    }

    private static List<String> imports(Path path) {
        try {
            return Files.readAllLines(path).stream().map(String::trim).toList();
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static final class RuntimeMarker {

        private final String runId;

        private RuntimeMarker(String runId) {
            this.runId = runId;
        }

        @SuppressWarnings("unused")
        private String runId() {
            return runId;
        }
    }
}
