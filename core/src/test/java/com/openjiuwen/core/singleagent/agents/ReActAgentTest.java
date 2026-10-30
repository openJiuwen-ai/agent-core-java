/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ModelRequestHeadersRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Focused parity tests for the Java ReAct agent translation.
 *
 * <p>Mirrors Python's {@code ReActAgent} behavior in
 * {@code openjiuwen/core/single_agent/agents/react_agent.py}.</p>
 *
 * <p>Mirrors Python's KV cache release entry-point tests in
 * {@code tests/unit_tests/core/context_engine/test_react_agent_kv_cache_release.py}.</p>
 */
class ReActAgentTest {

    @ParameterizedTest
    @ValueSource(strings = {"append", "delete", "replace", "edit"})
    void processedWindowEditsReachTheSelectedModel(String operation) {
        RecordingModelClient selectedClient = new RecordingModelClient();
        Model selectedModel = new Model(selectedClient);
        ReActAgent agent = new ReActAgent(new AgentCard("processed-rail", "test", "test")) {
            @Override
            protected Model getLlm(AgentCallbackContext context) {
                if ("selected".equals(context.getDynamicModelId())) {
                    return selectedModel;
                }
                return super.getLlm(context);
            }
        };
        RecordingModelClient oldClient = new RecordingModelClient();
        ReActAgentConfig agentConfig = new ReActAgentConfig();
        agentConfig.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        agent.configure(agentConfig);
        agent.setLlm(new Model(oldClient));
        AtomicInteger preparations = new AtomicInteger();
        SessionModelContext modelContext = processedContext(preparations);
        AgentRail rail = processedRequestRail(operation);
        agent.registerRail(rail).toCompletableFuture().join();
        try {
            AgentCallbackContext context = new AgentCallbackContext(agent);
            context.setSession(new MemorySession("processed-rail"));
            context.setContext(modelContext);
            agent.callModel(context, modelContext, List.of());
            assertProcessedRequest(operation, selectedClient, modelContext);
            assertEquals(0, oldClient.invokeCount());
            assertEquals(1, preparations.get());
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    private static SessionModelContext processedContext(AtomicInteger preparations) {
        SessionModelContext.ContextProcessorPort processor = new SessionModelContext.ContextProcessorPort() {
            @Override
            public String processorType() {
                return "compact";
            }

            @Override
            public CompletionStage<Boolean> triggerGetContextWindow(SessionModelContext context,
                    ContextWindow window, Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletionStage<SessionModelContext.ProcessResult> onGetContextWindow(SessionModelContext context,
                    ContextWindow window, Map<String, Object> kwargs) {
                preparations.incrementAndGet();
                window.setContextMessages(List.of(new UserMessage("summary"), new UserMessage("tail")));
                return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(null, null, window));
            }
        };
        return new SessionModelContext("ctx", "processed-rail", new ContextEngineConfig(),
                List.of(new UserMessage("raw history")), List.of(processor), messages -> messages.size(),
                null, null, null, null);
    }

    private static AgentRail processedRequestRail(String operation) {
        return new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext context) {
                ModelCallInputs inputs = assertInstanceOf(ModelCallInputs.class, context.getInputs());
                assertEquals(List.of("System", "summary", "tail"), inputs.getMessages().stream()
                        .map(BaseMessage.class::cast).map(BaseMessage::getContentAsString).toList());
                switch (operation) {
                    case "append" -> inputs.getMessages().add(new UserMessage("directive"));
                    case "delete" -> inputs.getMessages().remove(2);
                    case "replace" -> inputs.getMessages().set(2, new UserMessage("replacement"));
                    case "edit" -> BaseMessage.class.cast(inputs.getMessages().get(2)).setContent("edited");
                    default -> throw new IllegalArgumentException("Unknown request edit: " + operation);
                }
                context.setDynamicModelId("selected");
            }
        };
    }

    private static void assertProcessedRequest(String operation, RecordingModelClient client,
            SessionModelContext context) {
        List<String> expected = switch (operation) {
            case "append" -> List.of("System", "summary", "tail", "directive");
            case "delete" -> List.of("System", "summary");
            case "replace" -> List.of("System", "summary", "replacement");
            case "edit" -> List.of("System", "summary", "edited");
            default -> throw new IllegalArgumentException("Unknown request edit: " + operation);
        };
        assertEquals(expected, client.lastMessages.stream().map(BaseMessage::getContentAsString).toList());
        assertEquals(List.of("raw history"), context.getMessages().stream()
                .map(BaseMessage::getContentAsString).toList());
    }

    @Test
    void beforeModelRailKeepsCustomSystemMessageTypes() {
        RecordingModelClient client = new RecordingModelClient();
        ReActAgent agent = configuredAgent("openai", new Model(client));
        BaseMessage directive = new BaseMessage("system", "typed directive");
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext context) {
                assertInstanceOf(ModelCallInputs.class, context.getInputs()).getMessages().add(0, directive);
            }
        };
        agent.registerRail(rail).toCompletableFuture().join();
        try {
            callModelForKvTest(agent, new RecordingModelContext("rail-custom"), new MemorySession("rail-custom"));

            assertSame(directive, client.lastMessages.get(0));
            assertEquals(List.of("typed directive", "System", "question"),
                    client.lastMessages.stream().map(BaseMessage::getContentAsString).toList());
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    @Test
    void beforeModelRailPreservesSystemDirectiveWhileStreaming() {
        StreamingRecordingModelClient client = new StreamingRecordingModelClient();
        ReActAgent agent = configuredAgent("openai", new Model(client));
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext context) {
                ModelCallInputs inputs = assertInstanceOf(ModelCallInputs.class, context.getInputs());
                inputs.getMessages().add(0, new SystemMessage("stream directive"));
            }
        };
        agent.registerRail(rail).toCompletableFuture().join();
        AgentCallbackContext context = modelCallContext(agent, "rail-stream");
        context.getExtra().put("_streaming", true);
        try {
            agent.callModel(context, context.getContext(), null);

            assertEquals(List.of("stream directive", "System", "question"),
                    client.streamMessages.stream().map(BaseMessage::getContentAsString).toList());
            assertEquals(0, client.invokeCount());
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    @Test
    void beforeModelRailUsesFinalRequestWithoutRebuildingOrReordering() {
        RecordingModelClient client = new RecordingModelClient();
        ReActAgent agent = configuredAgent("openai", new Model(client));
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext context) {
                agent.addPromptBuilderSection("compression", "offload instructions", 90);
                ModelCallInputs inputs = assertInstanceOf(ModelCallInputs.class, context.getInputs());
                inputs.getMessages().add(new SystemMessage("directive"));
            }
        };
        agent.registerRail(rail).toCompletableFuture().join();
        try {
            callModelForKvTest(agent, new RecordingModelContext("rail-builder"), new MemorySession("rail-builder"));

            assertEquals(List.of("System", "question", "directive"),
                    client.lastMessages.stream().map(BaseMessage::getContentAsString).toList());
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    @Test
    void beforeModelRailKeepsRequestUserMessageWhileStreaming() {
        StreamingRecordingModelClient client = new StreamingRecordingModelClient();
        ReActAgent agent = configuredAgent("openai", new Model(client));
        UserMessage digest = new UserMessage("stream digest");
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext context) {
                assertInstanceOf(ModelCallInputs.class, context.getInputs()).getMessages().add(digest);
            }
        };
        agent.registerRail(rail).toCompletableFuture().join();
        AgentCallbackContext context = modelCallContext(agent, "rail-user-stream");
        context.getExtra().put("_streaming", true);
        try {
            agent.callModel(context, context.getContext(), null);
            assertSame(digest, client.streamMessages.get(2));
            assertEquals(List.of("System", "question", "stream digest"),
                    client.streamMessages.stream().map(BaseMessage::getContentAsString).toList());
            assertEquals(0, client.invokeCount());
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    @Test
    void afterInvokeGetsAnswerInputs() {
        ReActAgent agent = agentWithFakeModel(new AssistantMessage("done"));
        AtomicReference<Object> observed = new AtomicReference<>();
        AgentRail rail = new AgentRail() {
            @Override
            public void afterInvoke(AgentCallbackContext context) {
                observed.set(context.getInputs());
            }
        };
        agent.registerRail(rail).toCompletableFuture().join();
        try {
            Object result = agent.invoke(Map.of("query", "hi"), new MemorySession("answer-inputs"));
            InvokeInputs inputs = assertInstanceOf(InvokeInputs.class, observed.get());
            assertEquals("hi", inputs.getQuery());
            assertSame(result, inputs.getResult());
            assertEquals("answer", inputs.getResult().get("result_type"));
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    @Test
    void afterInvokeGetsInterruptInputs() {
        AtomicReference<Object> observed = new AtomicReference<>();
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeToolCall(AgentCallbackContext context) {
                ToolCallInputs inputs = assertInstanceOf(ToolCallInputs.class, context.getInputs());
                InterruptRequest request = new InterruptRequest("confirm", Map.of(), "");
                throw new AbortError("confirm", new ToolInterruptException(request,
                        assertInstanceOf(ToolCall.class, inputs.getToolCall())));
            }

            @Override
            public void afterInvoke(AgentCallbackContext context) {
                observed.set(context.getInputs());
            }
        };
        ToolCall call = ToolCall.builder().id("confirm-call").name("confirm").arguments("{}").build();
        ReActAgent agent = agentWithFakeModel(AssistantMessage.builder().toolCalls(List.of(call)).build());
        agent.registerRail(rail).toCompletableFuture().join();
        try {
            Object result = agent.invoke(Map.of("query", "hi"), new MemorySession("interrupt-inputs"));
            InvokeInputs inputs = assertInstanceOf(InvokeInputs.class, observed.get());
            assertSame(result, inputs.getResult());
            assertEquals("interrupt", inputs.getResult().get("result_type"));
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    @Test
    void afterInvokeGetsInputsOnModelFailure() {
        AtomicReference<Object> before = new AtomicReference<>();
        AtomicReference<Object> after = new AtomicReference<>();
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext context) {
                before.set(context.getInputs());
            }

            @Override
            public void beforeModelCall(AgentCallbackContext context) {
                throw new AbortError("model failure");
            }

            @Override
            public void afterInvoke(AgentCallbackContext context) {
                after.set(context.getInputs());
            }
        };
        ReActAgent agent = agentWithFakeModel(new AssistantMessage("unused"));
        agent.registerRail(rail).toCompletableFuture().join();
        try {
            assertThrows(AbortError.class,
                    () -> agent.invoke(Map.of("query", "hi"), new MemorySession("error-inputs")));
            assertInstanceOf(InvokeInputs.class, after.get());
            assertSame(before.get(), after.get());
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    @Test
    void configChainsMirrorPythonMutators() {
        ReActAgentConfig config = new ReActAgentConfig()
                .configureModel("qwen")
                .configureModelProvider("openai", "key", "base")
                .configurePrompt("default")
                .configurePromptTemplate(List.of(Map.of("role", "system", "content", "Hello {query}")))
                .configureMemScope("scope")
                .configureMaxIterations(3)
                .configureCustomHeaders(Map.of("X-Test", "1"));

        assertEquals("qwen", config.getModelName());
        assertEquals("openai", config.getModelProvider());
        assertEquals("key", config.getApiKey());
        assertEquals("base", config.getApiBase());
        assertEquals("default", config.getPromptTemplateName());
        assertEquals("scope", config.getMemScopeId());
        assertEquals(3, config.getMaxIterations());
        assertEquals("1", config.getCustomHeaders().get("X-Test"));
    }

    @Test
    void multimodalToolResultsAreAggregatedIntoOneUserMessage() {
        Map<String, Object> first = toolResult("a.png", "data:image/png;base64,aaa");
        Map<String, Object> second = toolResult("b.png", "data:image/png;base64,bbb");

        var message = ReActAgent.buildMultimodalToolResultsMessage(List.of(first, second));

        assertInstanceOf(List.class, message.getContent());
        List<?> content = (List<?>) message.getContent();
        assertEquals(5, content.size());
        assertTrue(String.valueOf(((Map<?, ?>) content.get(0)).get("text")).contains("Images loaded by tool results"));
        assertEquals("image_url", ((Map<?, ?>) content.get(2)).get("type"));
        assertEquals("image_url", ((Map<?, ?>) content.get(4)).get("type"));
    }

    @Test
    void invokeReturnsAssistantAnswerAndCanClearContextMessages() {
        ReActAgent agent = agentWithFakeModel(new AssistantMessage("done"));
        MemorySession session = new MemorySession("session-1");

        Object result = agent.invoke(Map.of("query", "hi"), session);

        assertInstanceOf(Map.class, result);
        assertEquals("done", ((Map<?, ?>) result).get("output"));
        assertEquals("answer", ((Map<?, ?>) result).get("result_type"));
        assertTrue(agent.clearContextMessages("session-1", "default_context_id"));
    }

    @Test
    void contextEngineRefreshesAfterMutableConfigUpdate() {
        ReActAgent agent = new ReActAgent(new AgentCard("mutable-1", "mutable", "desc"));
        ContextEngine initialEngine = agent.getContextEngine();
        ReActAgentConfig mutableConfig = assertInstanceOf(ReActAgentConfig.class, agent.getConfig());

        mutableConfig.configureContextEngine(2, 1, false);

        assertTrue(agent.getContextEngine() != initialEngine);
        ModelContext context = agent.getContextEngine().createContext("mutable-config", new MemorySession("mutable"));
        context.addMessages(List.of(new UserMessage("first"), new UserMessage("second"), new UserMessage("third")))
                .toCompletableFuture().join();
        assertEquals(List.of("second", "third"),
                context.getMessages().stream().map(BaseMessage::getContent).toList());
    }

    @Test
    void contextEngineIsReusedWhenMutableConfigIsUnchanged() {
        ReActAgent agent = new ReActAgent(new AgentCard("reuse-1", "reuse", "desc"));
        assertSame(agent.getContextEngine(), agent.getContextEngine());
    }

    @Test
    void copyContextEngineConfigPreservesOverflowRecoverySettings() throws Exception {
        java.lang.reflect.Method copy = ReActAgent.class.getDeclaredMethod(
                "copyContextEngineConfig", ContextEngineConfig.class);
        copy.setAccessible(true);
        ContextEngineConfig source = ContextEngineConfig.builder()
                .enableModelContextOverflowRecovery(false)
                .extraContextOverflowKeywords(List.of("custom_overflow_marker"))
                .build();

        ContextEngineConfig snapshot = (ContextEngineConfig) copy.invoke(null, source);

        assertFalse(snapshot.isEnableModelContextOverflowRecovery());
        assertEquals(List.of("custom_overflow_marker"), snapshot.getExtraContextOverflowKeywords());
        assertEquals(source, snapshot);
    }

    @Test
    void contextEngineHonorsOverflowRecoverySnapshotWithoutForcedRebuild() {
        ContextEngine.registerProcessor("T00919ReActOverflowCompact", OverflowSnapshotCompactProcessor::new);
        ContextEngine.ProcessorSpec compactSpec =
                new ContextEngine.ProcessorSpec("T00919ReActOverflowCompact", new Object());

        // Switch on + custom keyword must reach the live ContextEngine via config snapshot.
        ReActAgent enabledAgent = new ReActAgent(new AgentCard("overflow-on", "overflow-on", "desc"));
        enabledAgent.configure(ReActAgentConfig.builder()
                .contextEngineConfig(ContextEngineConfig.builder()
                        .enableModelContextOverflowRecovery(true)
                        .extraContextOverflowKeywords(List.of("vendor_overflow_signal"))
                        .build())
                .contextProcessors(List.of(compactSpec))
                .build());
        ContextEngine enabledEngine = enabledAgent.getContextEngine();
        MemorySession enabledSession = new MemorySession("overflow-on-session");
        ModelContext enabledContext = enabledAgent.initContext(enabledSession);
        enabledContext.setMessages(
                List.of(new UserMessage("large historical context that must shrink")), true);

        assertTrue(enabledEngine.recoverFromModelException(
                enabledContext.contextId(),
                enabledSession,
                new RuntimeException("vendor_overflow_signal from provider"),
                false,
                0,
                null),
                "extraContextOverflowKeywords from the ReAct snapshot must enable recovery");
        assertSame(enabledEngine, enabledAgent.getContextEngine());
        assertEquals(List.of("recovered by react overflow snapshot"),
                enabledContext.getMessages(null, true).stream().map(BaseMessage::getContentAsString).toList());

        // Same processor path with switch off must decline recovery (not merely "no processor").
        ReActAgent disabledAgent = new ReActAgent(new AgentCard("overflow-off", "overflow-off", "desc"));
        disabledAgent.configure(ReActAgentConfig.builder()
                .contextEngineConfig(ContextEngineConfig.builder()
                        .enableModelContextOverflowRecovery(false)
                        .extraContextOverflowKeywords(List.of("vendor_overflow_signal"))
                        .build())
                .contextProcessors(List.of(compactSpec))
                .build());
        ContextEngine disabledEngine = disabledAgent.getContextEngine();
        MemorySession disabledSession = new MemorySession("overflow-off-session");
        ModelContext disabledContext = disabledAgent.initContext(disabledSession);
        disabledContext.setMessages(
                List.of(new UserMessage("large historical context that must shrink")), true);

        assertFalse(disabledEngine.recoverFromModelException(
                disabledContext.contextId(),
                disabledSession,
                new RuntimeException("This model's maximum context length is 1048576 tokens."),
                false,
                0,
                null),
                "enableModelContextOverflowRecovery=false from the ReAct snapshot must block recovery");
        assertSame(disabledEngine, disabledAgent.getContextEngine());
        assertEquals(List.of("large historical context that must shrink"),
                disabledContext.getMessages(null, true).stream().map(BaseMessage::getContentAsString).toList());
    }

    @Test
    void beforeModelCallSeesPreparedRequestMessages() {
        AtomicReference<List<String>> beforeContents = new AtomicReference<>();
        AgentRail observingRail = new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext context) {
                ModelCallInputs inputs = assertInstanceOf(ModelCallInputs.class, context.getInputs());
                beforeContents.set(inputs.getMessages().stream()
                        .map(message -> ((BaseMessage) message).getContentAsString())
                        .toList());
            }
        };
        ReActAgent agent = agentWithFakeModel(new AssistantMessage("ok"));
        agent.registerRail(observingRail).toCompletableFuture().join();
        try {
            AgentSessionApi session = new MemorySession("before-messages");
            ModelContext modelContext = agent.initContext(session);
            modelContext.addMessages(new UserMessage("visible-to-before-hook"))
                    .toCompletableFuture().join();
            AgentCallbackContext ctx = new AgentCallbackContext(agent);
            ctx.setSession(session);
            ctx.setContext(modelContext);

            agent.callModel(ctx, modelContext, List.of());

            assertNotNull(beforeContents.get());
            assertTrue(beforeContents.get().stream().anyMatch(content -> content.contains("visible-to-before-hook")),
                    "BEFORE_MODEL_CALL must observe prepareModelRequest messages");
        } finally {
            agent.unregisterRail(observingRail).toCompletableFuture().join();
        }
    }

    @Test
    void contextEngineRefreshesAfterNestedConfigMapUpdate() {
        ReActAgent agent = new ReActAgent(new AgentCard("nested-1", "nested", "desc"));
        Map<String, Integer> modelWindowTokens = new LinkedHashMap<>();
        modelWindowTokens.put("model-a", 1000);
        agent.configure(ReActAgentConfig.builder()
                .contextEngineConfig(ContextEngineConfig.builder()
                        .modelContextWindowTokens(modelWindowTokens)
                        .build())
                .build());
        ContextEngine initialEngine = agent.getContextEngine();

        ReActAgentConfig config = assertInstanceOf(ReActAgentConfig.class, agent.getConfig());
        config.getContextEngineConfig().getModelContextWindowTokens().put("model-a", 2000);

        assertTrue(agent.getContextEngine() != initialEngine);
    }

    @Test
    void invokeRefreshesContextEngineAfterMutableConfigUpdate() {
        ReActAgent agent = agentWithFakeModel(new AssistantMessage("done"));
        ContextEngine initialEngine = agent.getContextEngine();
        ReActAgentConfig mutableConfig = assertInstanceOf(ReActAgentConfig.class, agent.getConfig());
        mutableConfig.configureContextEngine(2, 1, false);
        MemorySession session = new MemorySession("mutable-invoke");

        agent.invoke(Map.of("query", "first"), session);

        ContextEngine refreshedEngine = agent.getContextEngine();
        assertTrue(refreshedEngine != initialEngine);
        ModelContext context = refreshedEngine.getContext(null, session.getSessionId());
        assertTrue(context != null);
        context.addMessages(List.of(new UserMessage("second"), new UserMessage("third")))
                .toCompletableFuture().join();
        assertEquals(List.of("second", "third"),
                context.getMessages().stream().map(BaseMessage::getContent).toList());
    }

    @Test
    void configureRefreshesContextEngineForSameMutableConfigInstance() {
        ReActAgent agent = new ReActAgent(new AgentCard("same-1", "same", "desc"));
        ContextEngine initialEngine = agent.getContextEngine();
        ReActAgentConfig mutableConfig = assertInstanceOf(ReActAgentConfig.class, agent.getConfig());
        mutableConfig.configureContextEngine(10, 2, true);

        agent.configure(mutableConfig);

        assertTrue(agent.getContextEngine() != initialEngine);
    }

    @Test
    void nonAssistantModelResultIsPassedThrough() {
        ReActAgent agent = new ReActAgent(new AgentCard("agent-1", "agent", "desc")) {
            @Override
            public Object callModel(AgentCallbackContext ctx, ModelContext context,
                                    List<com.openjiuwen.core.foundation.tool.schema.ToolInfo> tools) {
                return Map.of("output", "direct", "result_type", "answer");
            }
        };
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        config.setMaxIterations(1);
        agent.configure(config);

        Object result = agent.invoke(Map.of("query", "hi"), new MemorySession("session-2"));

        assertInstanceOf(Map.class, result);
        assertEquals("direct", ((Map<?, ?>) result).get("output"));
    }

    @Test
    void summarizeToolCallUsesPythonFunctionShape() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", "search");
        function.put("arguments", "{\"query\":\"abc\"}");
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("function", function);

        assertEquals("search({\"query\":\"abc\"})", ReActAgent.summarizeToolCall(call));
    }

    @Test
    void invokeForwardsRequestHeadersAndCopiesProviderMap() {
        RecordingModelClient client = new RecordingModelClient();
        ReActAgent agent = configuredAgent("headers-invoke-agent", new Model(client));
        Map<String, String> providedHeaders = new LinkedHashMap<>();
        providedHeaders.put("Authorization", "Bearer invoke-token");
        ModelRequestHeadersRail rail = new ModelRequestHeadersRail(context ->
                CompletableFuture.completedFuture(providedHeaders));
        agent.registerRail(rail).toCompletableFuture().join();

        try {
            callModelForKvTest(agent, new RecordingModelContext("headers-invoke"),
                    new MemorySession("headers-invoke"));
            providedHeaders.put("Authorization", "Bearer changed-token");

            assertEquals("Bearer invoke-token",
                    client.lastOptions().getRequestHeaders().get("Authorization"));
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    @Test
    void modelCallConsumesHeadersAndDoesNotExposeThemOutsideInvokeOptions() {
        RecordingModelClient client = new RecordingModelClient();
        ReActAgent agent = configuredAgent("headers-consume-agent", new Model(client));
        AtomicReference<Map<String, String>> headersAfterCall = new AtomicReference<>();
        AtomicReference<Map<String, Object>> extraAfterCall = new AtomicReference<>();
        AtomicReference<Object> responseAfterCall = new AtomicReference<>();
        ModelRequestHeadersRail headersRail = new ModelRequestHeadersRail(context ->
                CompletableFuture.completedFuture(Map.of("Authorization", "Bearer consume-secret")));
        AgentRail observerRail = new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext context) {
                ModelCallInputs inputs = (ModelCallInputs) context.getInputs();
                headersAfterCall.set(inputs.getRequestHeaders());
                extraAfterCall.set(new LinkedHashMap<>(context.getExtra()));
                responseAfterCall.set(inputs.getResponse());
                return;
            }
        };
        agent.registerRail(headersRail).toCompletableFuture().join();
        agent.registerRail(observerRail).toCompletableFuture().join();

        try {
            callModelForKvTest(agent, new RecordingModelContext("headers-consume"),
                    new MemorySession("headers-consume"));

            assertTrue(headersAfterCall.get().isEmpty());
            assertFalse(extraAfterCall.get().toString().contains("consume-secret"));
            assertInstanceOf(AssistantMessage.class, responseAfterCall.get());
            assertFalse(client.lastOptions().getExtraFields().toString().contains("consume-secret"));
        } finally {
            agent.unregisterRail(observerRail).toCompletableFuture().join();
            agent.unregisterRail(headersRail).toCompletableFuture().join();
        }
    }

    @Test
    void streamForwardsRequestHeadersThroughActualStreamingPath() {
        StreamingRecordingModelClient client = new StreamingRecordingModelClient();
        ReActAgent agent = configuredAgent("headers-stream-agent", new Model(client));
        ModelRequestHeadersRail rail = new ModelRequestHeadersRail(context ->
                CompletableFuture.completedFuture(Map.of("Authorization", "Bearer stream-token")));
        agent.registerRail(rail).toCompletableFuture().join();
        AgentCallbackContext context = modelCallContext(agent, "headers-stream");
        context.getExtra().put("_streaming", true);

        try {
            agent.callModel(context, context.getContext(), null);

            assertEquals("Bearer stream-token",
                    client.streamOptions().getRequestHeaders().get("Authorization"));
            assertEquals(0, client.invokeCount());
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    @Test
    void retryObtainsFreshRequestHeadersForEachAttempt() {
        RetryingRecordingModelClient client = new RetryingRecordingModelClient();
        ReActAgent agent = configuredAgent("headers-retry-agent", new Model(client));
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicBoolean retryRequested = new AtomicBoolean();
        ModelRequestHeadersRail headersRail = new ModelRequestHeadersRail(context -> {
            int call = providerCalls.incrementAndGet();
            return CompletableFuture.completedFuture(Map.of("Authorization", "Bearer retry-" + call));
        });
        AgentRail retryRail = new AgentRail() {
            @Override
            public void onModelException(AgentCallbackContext context) {
                if (retryRequested.compareAndSet(false, true)) {
                    context.requestRetry(0);
                }
                return;
            }
        };
        agent.registerRail(headersRail).toCompletableFuture().join();
        agent.registerRail(retryRail).toCompletableFuture().join();

        try {
            callModelForKvTest(agent, new RecordingModelContext("headers-retry"),
                    new MemorySession("headers-retry"));

            assertEquals(2, providerCalls.get());
            assertEquals(List.of("Bearer retry-1", "Bearer retry-2"), client.authorizationValues());
        } finally {
            agent.unregisterRail(retryRail).toCompletableFuture().join();
            agent.unregisterRail(headersRail).toCompletableFuture().join();
        }
    }

    @Test
    void preparationFailureOccursBeforeModelRailLifecycle() {
        AtomicInteger lifecycleCalls = new AtomicInteger();
        AgentRail observer = new AgentRail() {
            @Override
            public void onModelException(AgentCallbackContext context) {
                lifecycleCalls.incrementAndGet();
                context.requestRetry(0);
            }

            @Override
            public void afterModelCall(AgentCallbackContext context) {
                lifecycleCalls.incrementAndGet();
            }
        };
        RecordingModelClient client = new RecordingModelClient();
        ReActAgent agent = configuredAgent("headers-preparation-failure-agent", new Model(client));
        AtomicInteger providerCalls = new AtomicInteger();
        ModelRequestHeadersRail headersRail = new ModelRequestHeadersRail(context -> {
            providerCalls.incrementAndGet();
            return CompletableFuture.completedFuture(Map.of("Authorization", "unused"));
        });
        agent.registerRail(headersRail).toCompletableFuture().join();
        agent.registerRail(observer).toCompletableFuture().join();
        try {
            RecordingModelContext context = new FailingOnceRecordingModelContext("headers-preparation-failure");
            CompletionException failure = assertThrows(CompletionException.class,
                    () -> callModelForKvTest(agent, context, new MemorySession("headers-preparation-failure")));
            assertEquals("context preparation failed", failure.getCause().getMessage());
            assertEquals(0, providerCalls.get());
            assertEquals(0, lifecycleCalls.get());
            assertEquals(0, client.invokeCount());
        } finally {
            agent.unregisterRail(observer).toCompletableFuture().join();
            agent.unregisterRail(headersRail).toCompletableFuture().join();
        }
    }

    @Test
    void beforeModelAbortClearsHeadersBeforeExceptionAfterAndRetry() {
        RecordingModelClient client = new RecordingModelClient();
        ReActAgent agent = configuredAgent("headers-before-abort-agent", new Model(client));
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicBoolean abortFirstAttempt = new AtomicBoolean(true);
        AtomicBoolean retryRequested = new AtomicBoolean();
        List<Map<String, String>> exceptionHeaders = new ArrayList<>();
        List<Map<String, String>> afterHeaders = new ArrayList<>();
        ModelRequestHeadersRail headersRail = new ModelRequestHeadersRail(context -> {
            int call = providerCalls.incrementAndGet();
            Map<String, String> headers = call == 1
                    ? Map.of("Authorization", "Bearer first", "X-Stale", "stale")
                    : Map.of("Authorization", "Bearer second", "X-Fresh", "fresh");
            return CompletableFuture.completedFuture(headers);
        });
        headersRail.setPriority(100);
        AgentRail abortRail = new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext context) {
                if (abortFirstAttempt.compareAndSet(true, false)) {
                    throw new AbortError("abort first model attempt");
                }
                return;
            }
        };
        abortRail.setPriority(50);
        AgentRail retryObserverRail = new AgentRail() {
            @Override
            public void onModelException(AgentCallbackContext context) {
                exceptionHeaders.add(((ModelCallInputs) context.getInputs()).getRequestHeaders());
                if (retryRequested.compareAndSet(false, true)) {
                    context.requestRetry(0);
                }
                return;
            }

            @Override
            public void afterModelCall(AgentCallbackContext context) {
                afterHeaders.add(((ModelCallInputs) context.getInputs()).getRequestHeaders());
                return;
            }
        };
        retryObserverRail.setPriority(10);
        agent.registerRail(headersRail).toCompletableFuture().join();
        agent.registerRail(abortRail).toCompletableFuture().join();
        agent.registerRail(retryObserverRail).toCompletableFuture().join();

        try {
            callModelForKvTest(agent, new RecordingModelContext("headers-before-abort"),
                    new MemorySession("headers-before-abort"));

            assertEquals(2, providerCalls.get());
            assertEquals(1, client.invokeCount());
            assertEquals(Map.of("Authorization", "Bearer second", "X-Fresh", "fresh"),
                    client.lastOptions().getRequestHeaders());
            assertFalse(client.lastOptions().getRequestHeaders().containsKey("X-Stale"));
            assertEquals(List.of(Map.of()), exceptionHeaders);
            assertEquals(List.of(Map.of(), Map.of()), afterHeaders);
        } finally {
            agent.unregisterRail(retryObserverRail).toCompletableFuture().join();
            agent.unregisterRail(abortRail).toCompletableFuture().join();
            agent.unregisterRail(headersRail).toCompletableFuture().join();
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void typePollutedProviderMapAbortsThroughActualCallbackChainBeforeModelInvocation() {
        RecordingModelClient client = new RecordingModelClient();
        ReActAgent agent = configuredAgent("headers-type-pollution-agent", new Model(client));
        CompletionStage<Map<String, String>> pollutedHeaders = (CompletionStage) CompletableFuture.completedFuture(
                Map.of(7, "must-not-reach-model"));
        ModelRequestHeadersRail rail = new ModelRequestHeadersRail(context -> pollutedHeaders);
        agent.registerRail(rail).toCompletableFuture().join();

        try {
            AbortError error = assertThrows(AbortError.class, () ->
                    callModelForKvTest(agent, new RecordingModelContext("headers-type-pollution"),
                            new MemorySession("headers-type-pollution")));

            assertTrue(error.getReason().toLowerCase().contains("headers"));
            assertFalse(error.getReason().contains("must-not-reach-model"));
            assertNull(error.getCause());
            assertEquals(0, client.invokeCount());
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    @Test
    void concurrentModelCallsKeepRequestHeadersIsolated() {
        ConcurrentRecordingModelClient client = new ConcurrentRecordingModelClient();
        Model sharedModel = new Model(client);
        ReActAgent agent = configuredAgent("headers-concurrent-agent", sharedModel);
        CyclicBarrier preparationBarrier = new CyclicBarrier(2);
        ModelRequestHeadersRail rail = sessionHeaderRail();
        agent.registerRail(rail).toCompletableFuture().join();

        try {
            CompletableFuture<Void> firstCall = CompletableFuture.runAsync(() ->
                    callModelForKvTest(agent,
                            new BarrierRecordingModelContext("request-1", preparationBarrier),
                            new MemorySession("request-1")));
            CompletableFuture<Void> secondCall = CompletableFuture.runAsync(() ->
                    callModelForKvTest(agent,
                            new BarrierRecordingModelContext("request-2", preparationBarrier),
                            new MemorySession("request-2")));
            CompletableFuture.allOf(firstCall, secondCall).join();

            assertEquals(2, client.invokeCount());
            assertEquals(Map.of(
                    "request-1", "Bearer token-1",
                    "request-2", "Bearer token-2"
            ), client.authorizationByRequest());
        } finally {
            agent.unregisterRail(rail).toCompletableFuture().join();
        }
    }

    private static ReActAgent agentWithFakeModel(AssistantMessage response) {
        ReActAgent agent = new ReActAgent(new AgentCard("agent-1", "agent", "desc"));
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System {query}")));
        config.setMaxIterations(2);
        agent.configure(config);
        agent.setLlm(new Model(new Model.ModelClient() {
            @Override
            public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
                return CompletableFuture.completedFuture(response);
            }
        }));
        return agent;
    }

    private static ReActAgent configuredAgent(String agentId, Model model) {
        ReActAgent agent = new ReActAgent(new AgentCard(agentId, "test", "desc"));
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        agent.configure(config);
        agent.setLlm(model);
        return agent;
    }

    /** TC_006-UT: enableReload 双腿——false 时 reloader 工具不进 AbilityManager，true 时注册。 */
    @org.junit.jupiter.api.Test
    void reloaderToolAbilityListFollowsEnableReloadSwitch() {
        java.util.function.Function<Boolean, ReActAgent> agentWithReload = enableReload -> {
            ReActAgent agent = new ReActAgent(new AgentCard("tc006-reload-" + enableReload, "test", "desc"));
            ReActAgentConfig config = new ReActAgentConfig();
            config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
            config.setContextEngineConfig(ContextEngineConfig.builder()
                    .enableReload(enableReload)
                    .build());
            agent.configure(config);
            return agent;
        };

        ReActAgent disabledAgent = agentWithReload.apply(false);
        disabledAgent.initContext(new MemorySession("tc006-disabled"));
        assertFalse(disabledAgent.getAbilityManager().get("reload_original_context_messages").isPresent(),
                "enableReload=false must leave the reloader tool out of the ability list");
        assertTrue(disabledAgent.getAbilityManager().getTools().keySet().stream()
                        .noneMatch(name -> name.contains("reload")),
                "no reload-flavored tool may be registered when reload is disabled");

        ReActAgent enabledAgent = agentWithReload.apply(true);
        enabledAgent.initContext(new MemorySession("tc006-enabled"));
        assertTrue(enabledAgent.getAbilityManager().get("reload_original_context_messages").isPresent(),
                "enableReload=true must register the reloader tool in the ability list");
    }

    /** TC_204-UT: enableReload × enableOffload 四开关组合矩阵——工具面与服务面的正交行为。 */
    @org.junit.jupiter.api.Test
    void contextSwitchMatrixKeepsReloadToolFaceAndOffloadServiceFaceOrthogonal() {
        java.util.function.BiFunction<Boolean, Boolean, ReActAgent> agentWithSwitches = (enableReload, enableOffload) -> {
            ReActAgent agent = new ReActAgent(new AgentCard("tc204-" + enableReload + "-" + enableOffload, "test", "desc"));
            ReActAgentConfig config = new ReActAgentConfig();
            config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
            com.openjiuwen.core.context.offload.model.OffloadConfig offloadConfig =
                    new com.openjiuwen.core.context.offload.model.OffloadConfig();
            offloadConfig.setPrimaryBackend(com.openjiuwen.core.context.offload.model.OffloadBackend.MEMORY);
            offloadConfig.setRecentMessageProtectionCount(0);
            config.setContextEngineConfig(ContextEngineConfig.builder()
                    .enableReload(enableReload)
                    .enableOffload(enableOffload)
                    .offload(offloadConfig)
                    .build());
            config.setContextProcessors(enableOffload
                    ? List.of(new ContextEngine.ProcessorSpec("MessageOffloader",
                            new com.openjiuwen.core.context.processor.offloader.MessageOffloaderConfig()))
                    : List.of());
            agent.configure(config);
            return agent;
        };

        ReActAgent neitherAgent = agentWithSwitches.apply(false, false);
        neitherAgent.initContext(new MemorySession("tc204-neither"));
        assertFalse(neitherAgent.getAbilityManager().get("reload_original_context_messages").isPresent(),
                "reload off must keep the reloader tool out of the ability list");
        assertNull(neitherAgent.getOffloadService(), "offload off must keep the v2 service unbound");

        ReActAgent reloadOnlyAgent = agentWithSwitches.apply(true, false);
        reloadOnlyAgent.initContext(new MemorySession("tc204-reload-only"));
        assertTrue(reloadOnlyAgent.getAbilityManager().get("reload_original_context_messages").isPresent(),
                "reload on must register the reloader tool even when offload is off");
        assertNull(reloadOnlyAgent.getOffloadService(), "offload off must keep the v2 service unbound");

        ReActAgent offloadOnlyAgent = agentWithSwitches.apply(false, true);
        offloadOnlyAgent.initContext(new MemorySession("tc204-offload-only"));
        assertFalse(offloadOnlyAgent.getAbilityManager().get("reload_original_context_messages").isPresent(),
                "reload off must keep the reloader tool out even when offload is on");
        assertNotNull(offloadOnlyAgent.getOffloadService(), "offload on must bind the v2 service");
        offloadOnlyAgent.close();

        ReActAgent bothAgent = agentWithSwitches.apply(true, true);
        bothAgent.initContext(new MemorySession("tc204-both"));
        assertTrue(bothAgent.getAbilityManager().get("reload_original_context_messages").isPresent(),
                "reload on must register the reloader tool when offload is on");
        assertNotNull(bothAgent.getOffloadService(), "offload on must bind the v2 service");
        bothAgent.close();
    }

    /** TC_211-UT: CD-011 魔数语义——默认值不覆盖 offloader 配置，非默认值透传。 */
    @org.junit.jupiter.api.Test
    void syncProtectionConfigOverwritesOnlyNonDefaultPipelineValues() throws Exception {
        ReActAgent agent = new ReActAgent(new AgentCard("tc211-cd011", "test", "desc"));
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        agent.configure(config);

        java.lang.reflect.Method sync = ReActAgent.class.getDeclaredMethod("syncProtectionConfig",
                com.openjiuwen.core.context.processor.offloader.MessageOffloader.class,
                com.openjiuwen.core.context.offload.model.OffloadConfig.class);
        sync.setAccessible(true);

        com.openjiuwen.core.context.offload.model.OffloadConfig defaultPipeline =
                new com.openjiuwen.core.context.offload.model.OffloadConfig();
        com.openjiuwen.core.context.processor.offloader.MessageOffloaderConfig keepTarget =
                new com.openjiuwen.core.context.processor.offloader.MessageOffloaderConfig();
        keepTarget.setRecentMessageProtectionCount(2);
        keepTarget.setProtectedToolNames(List.of("tc211_custom_tool"));
        com.openjiuwen.core.context.processor.offloader.MessageOffloader offloader =
                new com.openjiuwen.core.context.processor.offloader.MessageOffloader(keepTarget);
        sync.invoke(null, offloader, defaultPipeline);
        assertEquals(2, offloader.getConfig().getRecentMessageProtectionCount(),
                "default protection count (5) must not overwrite the offloader's own value");
        assertEquals(List.of("tc211_custom_tool"), offloader.getConfig().getProtectedToolNames(),
                "default protected tool names must not overwrite the offloader's own list");

        com.openjiuwen.core.context.offload.model.OffloadConfig customPipeline =
                new com.openjiuwen.core.context.offload.model.OffloadConfig();
        customPipeline.setRecentMessageProtectionCount(3);
        customPipeline.setProtectedToolNames(List.of("tc211_pipeline_tool"));
        sync.invoke(null, offloader, customPipeline);
        assertEquals(3, offloader.getConfig().getRecentMessageProtectionCount(),
                "non-default protection count must propagate to the offloader");
        assertEquals(List.of("tc211_pipeline_tool"), offloader.getConfig().getProtectedToolNames(),
                "non-default protected tool names must propagate to the offloader");
    }

    private static AgentCallbackContext modelCallContext(ReActAgent agent, String sessionId) {
        AgentCallbackContext context = new AgentCallbackContext(agent);
        context.setSession(new MemorySession(sessionId));
        context.setContext(new RecordingModelContext(sessionId));
        return context;
    }

    private static ModelRequestHeadersRail sessionHeaderRail() {
        return new ModelRequestHeadersRail(context -> {
            String sessionId = context.getSession().getSessionId();
            String token = switch (sessionId) {
                case "request-1" -> "Bearer token-1";
                case "request-2" -> "Bearer token-2";
                default -> throw new IllegalArgumentException("Unexpected session: " + sessionId);
            };
            return CompletableFuture.completedFuture(Map.of("Authorization", token));
        });
    }

    private static Map<String, Object> toolResult(String sourcePath, String dataUrl) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("type", "image");
        image.put("source_path", sourcePath);
        image.put("data_url", dataUrl);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("multimodal", List.of(image));
        return Map.of("data", data);
    }

    private static Object callModelForKvTest(ReActAgent agent, RecordingModelContext context, AgentSessionApi session) {
        AgentCallbackContext ctx = new AgentCallbackContext(agent);
        ctx.setSession(session);
        ctx.setContext(context);
        return agent._call_model(ctx, context, null);
    }

    private static final class RecordingModelClient implements Model.ModelClient {
        private int invokeCount;
        private ModelInvokeOptions lastOptions;
        private List<BaseMessage> lastMessages = List.of();

        private RecordingModelClient() {
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeCount++;
            lastOptions = options;
            lastMessages = new ArrayList<>(messages);
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }

        private int invokeCount() {
            return invokeCount;
        }

        private ModelInvokeOptions lastOptions() {
            return lastOptions;
        }
    }

    private static final class StreamingRecordingModelClient implements Model.ModelClient {
        private int invokeCount;
        private ModelInvokeOptions streamOptions;
        private List<BaseMessage> streamMessages = List.of();

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeCount++;
            return CompletableFuture.failedFuture(new AssertionError("Streaming path must not invoke the model"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            streamOptions = options;
            streamMessages = new ArrayList<>(messages);
            List<AssistantMessageChunk> chunks = List.of(
                    AssistantMessageChunk.builder().content("streamed").build());
            return chunks.iterator();
        }

        private int invokeCount() {
            return invokeCount;
        }

        private ModelInvokeOptions streamOptions() {
            return streamOptions;
        }
    }

    private static final class RetryingRecordingModelClient implements Model.ModelClient {
        private final List<ModelInvokeOptions> options = new ArrayList<>();

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            this.options.add(options);
            if (this.options.size() == 1) {
                return CompletableFuture.failedFuture(new IllegalStateException("first attempt failed"));
            }
            return CompletableFuture.completedFuture(new AssistantMessage("retried"));
        }

        private List<String> authorizationValues() {
            return options.stream()
                    .map(option -> option.getRequestHeaders().get("Authorization"))
                    .toList();
        }

    }

    private static final class ConcurrentRecordingModelClient implements Model.ModelClient {
        private final Map<String, String> authorizationByRequest = new ConcurrentHashMap<>();
        private final AtomicInteger invokeCount = new AtomicInteger();

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            String requestId = messages.stream()
                    .map(BaseMessage::getContent)
                    .map(String::valueOf)
                    .filter(content -> content.startsWith("request-"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Request id message is missing"));
            authorizationByRequest.put(requestId, options.getRequestHeaders().get("Authorization"));
            invokeCount.incrementAndGet();
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }

        private Map<String, String> authorizationByRequest() {
            return Map.copyOf(authorizationByRequest);
        }

        private int invokeCount() {
            return invokeCount.get();
        }
    }

    private static class RecordingModelContext implements ModelContext {
        private final String sessionId;
        private List<BaseMessage> messages = new ArrayList<>();
        private Map<String, Object> lastKwargs = new LinkedHashMap<>();

        private RecordingModelContext(String sessionId) {
            this.sessionId = sessionId;
        }

        private Map<String, Object> lastKwargs() {
            return new LinkedHashMap<>(lastKwargs);
        }

        @Override
        public int length() {
            return messages.size();
        }

        @Override
        public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
            return new ArrayList<>(messages);
        }

        @Override
        public void setMessages(List<BaseMessage> messages, boolean withHistory) {
            this.messages = new ArrayList<>(messages == null ? List.of() : messages);
        }

        @Override
        public List<BaseMessage> popMessages(int size, boolean withHistory) {
            int from = Math.max(0, messages.size() - size);
            List<BaseMessage> popped = new ArrayList<>(messages.subList(from, messages.size()));
            messages = new ArrayList<>(messages.subList(0, from));
            return popped;
        }

        @Override
        public CompletionStage<Void> clearMessages(boolean withHistory) {
            messages.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(BaseMessage message) {
            return addMessages(List.of(message));
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(List<BaseMessage> messagesToAdd) {
            List<BaseMessage> safeMessages = messagesToAdd == null ? List.of() : messagesToAdd;
            messages.addAll(safeMessages);
            return CompletableFuture.completedFuture(new ArrayList<>(safeMessages));
        }

        @Override
        public CompletionStage<ContextWindow> getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools,
                                                               Integer windowSize, Integer dialogueRound,
                                                               Map<String, Object> kwargs) {
            lastKwargs = new LinkedHashMap<>(kwargs == null ? Map.of() : kwargs);
            ContextWindow window = new ContextWindow(
                    systemMessages,
                    messages.isEmpty() ? List.of(new UserMessage("question")) : messages,
                    tools == null ? List.of() : tools,
                    new ContextStats());
            return CompletableFuture.completedFuture(window);
        }

        @Override
        public ContextStats statistic() {
            return new ContextStats();
        }

        @Override
        public String sessionId() {
            return sessionId;
        }

        @Override
        public String contextId() {
            return "default_context_id";
        }

        @Override
        public TokenCounterPort tokenCounter() {
            return messages -> 0;
        }

        @Override
        public ToolPort reloaderTool() {
            return () -> "reload_original_context_messages";
        }
    }

    private static final class FailingOnceRecordingModelContext extends RecordingModelContext {
        private final AtomicBoolean firstCall = new AtomicBoolean(true);

        private FailingOnceRecordingModelContext(String sessionId) {
            super(sessionId);
        }

        @Override
        public CompletionStage<ContextWindow> getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools,
                                                               Integer windowSize, Integer dialogueRound,
                                                               Map<String, Object> kwargs) {
            if (firstCall.compareAndSet(true, false)) {
                return CompletableFuture.failedFuture(new IllegalStateException("context preparation failed"));
            }
            return super.getContextWindow(systemMessages, tools, windowSize, dialogueRound, kwargs);
        }
    }

    private static final class BarrierRecordingModelContext extends RecordingModelContext {
        private final String requestId;
        private final CyclicBarrier barrier;

        private BarrierRecordingModelContext(String requestId, CyclicBarrier barrier) {
            super(requestId);
            this.requestId = requestId;
            this.barrier = barrier;
        }

        @Override
        public CompletionStage<ContextWindow> getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools,
                                                               Integer windowSize, Integer dialogueRound,
                                                               Map<String, Object> kwargs) {
            try {
                barrier.await(10, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException("Concurrent preparation barrier failed", exception);
            }
            return CompletableFuture.completedFuture(new ContextWindow(
                    systemMessages,
                    List.of(new UserMessage(requestId)),
                    tools == null ? List.of() : tools,
                    new ContextStats()
            ));
        }
    }

    /**
     * Force-compresses to a shorter message so overflow recovery can succeed when enabled.
     */
    private static final class OverflowSnapshotCompactProcessor
            implements SessionModelContext.ContextProcessorPort {
        private OverflowSnapshotCompactProcessor(Object config) {
        }

        @Override
        public String processorType() {
            return "T00919ReActOverflowCompact";
        }

        @Override
        public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                                List<BaseMessage> messages,
                                                                                boolean force,
                                                                                Map<String, Object> kwargs) {
            if (force) {
                context.setMessages(List.of(new UserMessage("recovered by react overflow snapshot")), true);
                return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(
                        new SessionModelContext.ContextProcessorEventPort() {
                            @Override
                            public String compactSummary() {
                                return "recovered by react overflow snapshot";
                            }
                        },
                        List.of(),
                        null));
            }
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(null, messages, null));
        }
    }

    /**
     * In-memory session test double for ReActAgent parity checks.
     *
     * <p>Mirrors Python's session interactions used by {@code ReActAgent} in
     * {@code openjiuwen/core/single_agent/agents/react_agent.py}.</p>
     */
    public static final class MemorySession implements AgentSessionApi, ContextEngine.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        private MemorySession(String sessionId) {
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
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }
}
