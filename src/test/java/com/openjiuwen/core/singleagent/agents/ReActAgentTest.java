// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.common.security.UserConfig;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.EventInputs;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.task_loop.LoopQueues;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link ReActAgent}.
 * Translated from Python test_rail.py with additional coverage.
 */
class ReActAgentTest {
    private ReActAgent agent;

    private static final class TestSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new HashMap<>();

        private TestSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }

    private static final class TestableReActAgent extends ReActAgent {
        private final AbilityManager abilityManager = mock(AbilityManager.class);

        private TestableReActAgent(String id) {
            super(AgentCard.builder().id(id).name(id).description(id).build());
            when(abilityManager.listToolInfo()).thenReturn(List.of());
        }

        @Override
        public AbilityManager getAbilityManager() {
            return abilityManager;
        }
    }

    @BeforeEach
    void setUp() {
        AgentCard card = AgentCard.builder().name("test-react-agent").description("Test ReAct Agent").build();
        agent = new ReActAgent(card);
    }

    @AfterEach
    void tearDown() {
        agent.getAgentCallbackManager().clear(null);
    }

    // ========== Construction ==========

    @Test
    void testDefaultConfig() {
        ReActAgentConfig config = (ReActAgentConfig) agent.getConfig();
        assertThat(config).isNotNull();
        assertThat(config.getMaxIterations()).isEqualTo(5);
        assertThat(config.getModelProvider()).isEqualTo("openai");
    }

    @Test
    void testContextEngineCreated() {
        assertThat(agent.getContextEngine()).isNotNull();
    }

    // ========== Configure ==========

    @Test
    void testConfigureWithReActAgentConfig() {
        ReActAgentConfig newConfig = ReActAgentConfig.builder().modelName("gpt-4").maxIterations(10).build();

        agent.configure(newConfig);
        assertThat(((ReActAgentConfig) agent.getConfig()).getModelName()).isEqualTo("gpt-4");
    }

    @Test
    void testConfigureWithWrongTypeThrows() {
        assertThatThrownBy(() -> agent.configure("wrong type")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected ReActAgentConfig");
    }

    @Test
    void testConfigureReturnsAgent() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        BaseAgent result = agent.configure(config);
        assertThat(result).isSameAs(agent);
    }

    @Test
    void testSetLlmOverridesActiveRuntimeModel() {
        Model model = mock(Model.class);

        agent.setLlm(model);

        assertThat(agent.peekLlm()).isSameAs(model);

        agent.setLlm(null);
        assertThat(agent.peekLlm()).isNull();
    }

    // ========== Invoke with null/invalid inputs ==========

    @Test
    void testInvokeNullInputThrows() {
        assertThatThrownBy(() -> agent.invoke(null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvokeInvalidTypeThrows() {
        assertThatThrownBy(() -> agent.invoke(42, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvokeEmptyQueryThrows() {
        assertThatThrownBy(() -> agent.invoke(Map.of("query", ""), null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ========== Rail Registration on ReActAgent (mirrors Python tests) ==========

    @Test
    void testRailRegistration() {
        List<String> events = new ArrayList<>();
        AgentRail logRail = new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {
                events.add("before_invoke");
            }

            @Override
            public void afterInvoke(AgentCallbackContext ctx) {
                events.add("after_invoke");
            }

            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {
                events.add("before_model_call");
            }
        };

        agent.registerRail(logRail);

        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.BEFORE_INVOKE)).isTrue();
        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.AFTER_INVOKE)).isTrue();
        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.BEFORE_MODEL_CALL)).isTrue();
    }

    @Test
    void testRailAllEightEvents() {
        AgentRail allHooksRail = new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {
            }

            @Override
            public void afterInvoke(AgentCallbackContext ctx) {
            }

            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {
            }

            @Override
            public void afterModelCall(AgentCallbackContext ctx) {
            }

            @Override
            public void onModelException(AgentCallbackContext ctx) {
            }

            @Override
            public void beforeToolCall(AgentCallbackContext ctx) {
            }

            @Override
            public void afterToolCall(AgentCallbackContext ctx) {
            }

            @Override
            public void onToolException(AgentCallbackContext ctx) {
            }
        };

        agent.registerRail(allHooksRail);

        for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
            assertThat(agent.getAgentCallbackManager().hasHooks(event)).as("Event %s should have hooks", event)
                    .isTrue();
        }
    }

    // ========== Rail Tools Auto Registration (mirrors Python test_rail_tools_auto_registration) ==========

    @Test
    void testRailToolsAutoRegistration() {
        ToolCard toolCard = ToolCard.builder().name("rail_tool").description("A rail tool")
                .inputParams(Map.of("type", "object", "properties", Map.of())).build();

        AgentRail toolRail = new AgentRail(List.of(toolCard)) {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {
            }
        };

        agent.registerRail(toolRail);

        List<String> names = new ArrayList<>();
        for (Object ability : agent.getAbilityManager().list()) {
            if (ability instanceof ToolCard tc) {
                names.add(tc.getName());
            }
        }
        assertThat(names).contains("rail_tool");
    }

    @Test
    void testRailUnregisterRemovesTools() {
        ToolCard toolCard = ToolCard.builder().name("rail_tool_remove").description("Tool to remove").build();

        AgentRail toolRail = new AgentRail(List.of(toolCard)) {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {
            }
        };

        agent.registerRail(toolRail);
        assertThat(agent.getAbilityManager().get("rail_tool_remove")).isNotNull();

        agent.unregisterRail(toolRail);
        assertThat(agent.getAbilityManager().get("rail_tool_remove")).isNull();
    }

    // ========== Rail Priority (mirrors Python TestRailPriority) ==========

    @Test
    void testRailPriorityOrdering() {
        List<String> order = new ArrayList<>();

        // Use registerCallback to avoid anonymous class reflection issues
        // Higher priority value runs first in CallbackFramework
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> order.add("high"), 90);
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> order.add("low"), 10);

        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).build();
        agent.fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        assertThat(order).containsExactly("high", "low");
    }

    // ========== Rail Extra Communication (mirrors Python TestRailExtra) ==========

    @Test
    void testRailExtraCommunication() {
        final boolean[] sawWriter = {false};

        // Use registerCallback; higher priority runs first
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> ctx.getExtra().put("writer_was_here", true),
                90);
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE,
                ctx -> sawWriter[0] = Boolean.TRUE.equals(ctx.getExtra().get("writer_was_here")), 10);

        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).build();
        agent.fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        assertThat(sawWriter[0]).isTrue();
    }

    // ========== Method data visibility (mirrors Python TestMethodSplitDataVisibility) ==========

    @Test
    void testBeforeCallbackSeesInputsData() {
        List<Object> seenMessages = new ArrayList<>();

        // Use registerCallback to avoid reflection access issues
        agent.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL, ctx -> {
            if (ctx.getInputs() instanceof ModelCallInputs mci) {
                seenMessages.addAll(mci.getMessages());
            }
        }, 50);

        // Manually fire with some inputs to simulate
        ModelCallInputs inputs = ModelCallInputs.builder().messages(List.of("msg1", "msg2")).build();

        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).inputs(inputs).build();
        agent.fireCallbackEvent(AgentCallbackEvent.BEFORE_MODEL_CALL, ctx);

        assertThat(seenMessages).hasSize(2);
    }

    // ========== getLlm throws when no config ==========

    @Test
    void testGetLlmThrowsWithoutClientConfig() {
        // Default config has no model_client_config
        assertThatThrownBy(() -> agent.invoke(Map.of("query", "test"), null)).isInstanceOf(Exception.class);
    }

    @Test
    void testAfterInvokeCallbackSeesInvokeInputsOnFailure() {
        List<EventInputs> seenInputs = new ArrayList<>();
        agent.registerCallback(AgentCallbackEvent.AFTER_INVOKE, ctx -> seenInputs.add(ctx.getInputs()), 50);

        assertThatThrownBy(() -> agent.invoke(Map.of("query", "needs-model"), null)).isInstanceOf(Exception.class);

        assertThat(seenInputs).hasSize(1);
        assertThat(seenInputs.get(0)).isInstanceOf(InvokeInputs.class);
        assertThat(((InvokeInputs) seenInputs.get(0)).getQuery()).isEqualTo("needs-model");
    }

    @Test
    void testInvokeCopiesTaskLoopMetadataIntoCallbackExtra() {
        List<Map<String, Object>> seenExtra = new ArrayList<>();
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> seenExtra.add(new HashMap<>(ctx.getExtra())),
                50);

        assertThatThrownBy(
                () -> agent.invoke(
                        Map.of("query", "needs-model", "run_kind", "heartbeat", "run_context",
                                Map.of("source", "task_loop"), "is_follow_up", true),
                        new TestSession("react-metadata-session")))
                .isInstanceOf(Exception.class);

        assertThat(seenExtra).hasSize(1);
        assertThat(seenExtra.get(0)).containsEntry("run_kind", "heartbeat").containsEntry("is_follow_up", true);
        assertThat(seenExtra.get(0).get("run_context")).isEqualTo(Map.of("source", "task_loop"));
    }

    @Test
    void testLoopQueuesSteeringInjectedBeforeModelCall() throws Exception {
        ReActAgentConfig config = ReActAgentConfig.builder().maxIterations(1).build();
        agent.configure(config);
        LoopQueues queues = new LoopQueues();
        queues.pushSteer("inspect scope before editing");
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<BaseMessage> messages = (List<BaseMessage>) invocation.getArgument(0);
                    assertThat(messages).extracting(message -> String.valueOf(message.getContent()))
                            .anyMatch(content -> content.contains("[STEERING] inspect scope before editing"));
                    return AssistantMessage.builder().content("done").build();
                });
        agent.setLlm(model);

        Object result =
            agent.invoke(Map.of("query", "run", "loop_queues", queues), new TestSession("react-steering-session"));

        assertThat(((Map<?, ?>) result).get("output")).isEqualTo("done");
        assertThat(queues.drainSteering()).isEmpty();
    }

    @Test
    void testInvokeRedactsMessagesAfterModelCallRailsWhenSensitive() throws Exception {
        boolean previousSensitive = UserConfig.isSensitive();
        UserConfig.setSensitive(true);
        Logger agentLogger = (Logger) LoggerFactory.getLogger("agent");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        agentLogger.addAppender(appender);
        TestableReActAgent testAgent = new TestableReActAgent("react-sensitive-log");
        try {
            testAgent.configure(ReActAgentConfig.builder().maxIterations(1).build());
            testAgent.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL, callbackContext -> {
                ModelCallInputs modelInputs = (ModelCallInputs) callbackContext.getInputs();
                modelInputs.setMessages(List.of(new SystemMessage("rail-system-secret"),
                        new UserMessage("rail-user-secret"), new ToolMessage("30.0", "call-sensitive-log")));
            }, 50);
            Model model = mock(Model.class);
            when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(AssistantMessage.builder().content("done").build());
            testAgent.setLlm(model);

            testAgent.invoke(Map.of("query", "original-user-secret"), new TestSession("react-sensitive-log-session"));

            List<String> messages =
                    appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).contains(
                    "LLM request messages redacted: message_count=3, role_counts={system=1, user=1, tool=1}",
                    "{\"role\": \"tool\", \"content\": \"30.0\"}");
            assertThat(messages).noneMatch(message -> message.contains("original-user-secret")
                    || message.contains("rail-system-secret") || message.contains("rail-user-secret"));
        } finally {
            testAgent.getAgentCallbackManager().clear(null);
            agentLogger.detachAppender(appender);
            appender.stop();
            UserConfig.setSensitive(previousSensitive);
        }
    }

    @Test
    void testStreamLogsPostRailToolMessageWhenNotSensitive() throws Exception {
        boolean previousSensitive = UserConfig.isSensitive();
        UserConfig.setSensitive(false);
        Logger agentLogger = (Logger) LoggerFactory.getLogger("agent");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        agentLogger.addAppender(appender);
        TestableReActAgent testAgent = new TestableReActAgent("react-stream-log");
        try {
            testAgent.configure(ReActAgentConfig.builder().maxIterations(2).build());
            testAgent.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL, callbackContext -> {
                ModelCallInputs modelInputs = (ModelCallInputs) callbackContext.getInputs();
                List<Object> rewritten = new ArrayList<>();
                for (Object message : modelInputs.getMessages()) {
                    rewritten.add(message instanceof UserMessage
                            ? new UserMessage("rail-rewritten-query")
                            : message);
                }
                modelInputs.setMessages(rewritten);
            }, 50);
            Model model = mock(Model.class);
            when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(AssistantMessageChunk.builder().content("").toolCalls(List.of(
                                    ToolCall.builder().id("call-log").name("logged-tool").arguments("{}").index(0)
                                            .build()))
                                    .build())
                                    .iterator(),
                            List.of(AssistantMessageChunk.builder().content("done").build()).iterator());
            when(testAgent.getAbilityManager()
                    .execute(any(AgentCallbackContext.class), any(), any(Session.class), isNull()))
                    .thenReturn(List.of(
                            new AbilityManager.ToolExecutionEntry("30.0", new ToolMessage("30.0", "call-log"))));
            testAgent.setLlm(model);
            AgentSessionApi session =
                    new AgentSessionApi("react-stream-log-session", null, testAgent.getCard(),
                            List.of(StreamMode.OUTPUT));

            Iterator<Object> stream =
                    testAgent.stream(Map.of("query", "original-stream-query"), session, List.of(StreamMode.OUTPUT));
            stream.forEachRemaining(ignored -> {
            });

            List<String> messages =
                    appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).contains("{\"role\": \"user\", \"content\": \"rail-rewritten-query\"}");
            assertThat(messages).contains("{\"role\": \"tool\", \"content\": \"30.0\"}");
            assertThat(messages).noneMatch(message -> message.contains("original-stream-query"));
        } finally {
            testAgent.getAgentCallbackManager().clear(null);
            agentLogger.detachAppender(appender);
            appender.stop();
            UserConfig.setSensitive(previousSensitive);
        }
    }

    @Test
    void testInvokeDoesNotExecuteToolCallAtIterationLimit() throws Exception {
        TestableReActAgent testAgent = new TestableReActAgent("react-iteration-limit");
        testAgent.configure(ReActAgentConfig.builder().maxIterations(1).build());
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssistantMessage.builder().content("").toolCalls(List.of(
                        ToolCall.builder().id("call-limit").name("should-not-run").arguments("{}").build())).build());
        testAgent.setLlm(model);
        TestSession session = new TestSession("react-iteration-limit-session");

        Object result = testAgent.invoke(Map.of("query", "run"), session);

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> resultMap = (Map<?, ?>) result;
        assertThat(resultMap.get("result_type")).isEqualTo("error");
        assertThat(resultMap.get("output")).isEqualTo("Max iterations reached without completion");
        verify(testAgent.getAbilityManager(), never()).execute(any(AgentCallbackContext.class), any(),
                any(Session.class), isNull());
        ModelContext context = testAgent.getContextEngine().getContext(null, session.getSessionId());
        assertThat(context.getMessages()).singleElement().isInstanceOf(UserMessage.class);
    }

    @Test
    void testStreamDoesNotExecuteToolCallAtIterationLimit() throws Exception {
        TestableReActAgent testAgent = new TestableReActAgent("react-stream-iteration-limit");
        testAgent.configure(ReActAgentConfig.builder().maxIterations(1).build());
        Model model = mock(Model.class);
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(AssistantMessageChunk.builder().content("").toolCalls(List.of(
                        ToolCall.builder().id("call-stream-limit").name("should-not-run").arguments("{}").index(0)
                                .build())).build()).iterator());
        testAgent.setLlm(model);
        AgentSessionApi session =
                new AgentSessionApi("react-stream-iteration-limit-session", null, testAgent.getCard(),
                        List.of(StreamMode.OUTPUT));

        Iterator<Object> stream = testAgent.stream(Map.of("query", "run"), session, List.of(StreamMode.OUTPUT));
        List<Object> outputs = new ArrayList<>();
        stream.forEachRemaining(outputs::add);

        assertThat(outputs).anyMatch(output -> output instanceof OutputSchema outputSchema
                && outputSchema.getPayload() instanceof Map<?, ?> payload
                && "Max iterations reached without completion".equals(payload.get("output")));
        verify(testAgent.getAbilityManager(), never()).execute(any(AgentCallbackContext.class), any(),
                any(Session.class), isNull());
        ModelContext context = testAgent.getContextEngine().getContext(null, session.getSessionId());
        assertThat(context.getMessages()).singleElement().isInstanceOf(UserMessage.class);
    }

    @Test
    void testRunStreamRoundWritesFinalAnswerSchemaLikeStream() throws Exception {
        TestableReActAgent testAgent = new TestableReActAgent("react-run-stream-round");
        testAgent.configure(ReActAgentConfig.builder().maxIterations(1).build());
        Model model = mock(Model.class);
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(AssistantMessageChunk.builder().content("delta").build(),
                        AssistantMessageChunk.builder().content("done").build()).iterator());
        testAgent.setLlm(model);
        AgentSessionApi session = new AgentSessionApi("run-stream-round-session", null, testAgent.getCard(),
                List.of(StreamMode.OUTPUT));
        List<Object> captured = new ArrayList<>();
        session.setStreamTap(captured::add);

        Map<String, Object> result = testAgent.runStreamRound(Map.of("query", "run"), session);

        assertThat(result.get("result_type")).isEqualTo("answer");
        assertThat(captured).anyMatch(output -> output instanceof OutputSchema outputSchema
                && "answer".equals(outputSchema.getType())
                && outputSchema.getPayload() instanceof Map<?, ?> payload
                && "answer".equals(payload.get("result_type"))
                && payload.get("output").equals(result.get("output")));
    }

    @Test
    void testInvokeNormalizesMissingToolCallIndexInStoredContext() throws Exception {
        TestableReActAgent testAgent = new TestableReActAgent("react-context-tool-index");
        testAgent.configure(ReActAgentConfig.builder().maxIterations(2).build());
        ToolCall rawToolCall =
                ToolCall.builder().id("call-index").name("indexed-tool").arguments("{}").build();
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssistantMessage.builder().content("").toolCalls(List.of(rawToolCall)).build(),
                        AssistantMessage.builder().content("done").build());
        when(testAgent.getAbilityManager()
                .execute(any(AgentCallbackContext.class), any(), any(Session.class), isNull()))
                .thenReturn(List.of(new AbilityManager.ToolExecutionEntry("ok", new ToolMessage("ok", "call-index"))));
        testAgent.setLlm(model);
        TestSession session = new TestSession("react-context-tool-index-session");

        Object result = testAgent.invoke(Map.of("query", "run"), session);

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> resultMap = (Map<?, ?>) result;
        assertThat(resultMap.get("result_type")).isEqualTo("answer");
        assertThat(resultMap.get("output")).isEqualTo("done");
        assertThat(rawToolCall.getIndex()).isNull();
        ModelContext context = testAgent.getContextEngine().getContext(null, session.getSessionId());
        assertThat(context.getMessages()).hasSize(4);
        AssistantMessage storedAssistant = (AssistantMessage) context.getMessages().get(1);
        assertThat(storedAssistant.getToolCalls()).singleElement().extracting(ToolCall::getIndex).isEqualTo(0);
    }

    @Test
    void testEnableReloadRegistersContextReloaderTool() {
        ReActAgentConfig config = ReActAgentConfig.builder().build().configureContextEngine(200, 10, true);
        agent.configure(config);
        Session session = new TestSession("react-reload-session");

        assertThatThrownBy(() -> agent.invoke(Map.of("query", "reload"), session)).isInstanceOf(Exception.class);

        assertThat(agent.getAbilityManager().get("reload_original_context_messages")).isNotNull();
    }
}
