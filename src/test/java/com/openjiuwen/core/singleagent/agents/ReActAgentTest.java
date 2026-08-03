/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ContextStats;
import com.openjiuwen.core.context_engine.ContextWindow;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ModelRequestHeadersRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        Object result = agent.invoke(Map.of("query", "hi"), session).toCompletableFuture().join();

        assertInstanceOf(Map.class, result);
        assertEquals("done", ((Map<?, ?>) result).get("output"));
        assertEquals("answer", ((Map<?, ?>) result).get("result_type"));
        assertTrue(agent.clearContextMessages("session-1", "default_context_id"));
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

        Object result = agent.invoke(Map.of("query", "hi"), new MemorySession("session-2"))
                .toCompletableFuture()
                .join();

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
    void kvReleaseWarningOnceWhenOpenAiNotSupportedAndEnabled() {
        assertKvReleaseWarningOnceWhenNotSupportedAndEnabled("OpenAI");
    }

    @Test
    void kvReleaseWarningOnceWhenSiliconFlowNotSupportedAndEnabled() {
        assertKvReleaseWarningOnceWhenNotSupportedAndEnabled("SiliconFlow");
    }

    @Test
    void kvReleaseWarningAgainAfterSwitchProvider() {
        RecordingModelClient client = new RecordingModelClient(false);
        Model model = new Model(client);
        ReActAgent agent = configuredKvAgent("OpenAI", true, model);
        MemorySession session = new MemorySession("sess-1");

        callModelForKvTest(agent, new RecordingModelContext("sess-1"), session);
        assertTrue(agent.isKvReleaseWarningLogged());

        configureKvAgent(agent, "SiliconFlow", true, model);
        assertFalse(agent.isKvReleaseWarningLogged());

        callModelForKvTest(agent, new RecordingModelContext("sess-1"), session);
        assertTrue(agent.isKvReleaseWarningLogged());
    }

    @Test
    void kvReleaseNoWarningWhenReleaseDisabled() {
        RecordingModelClient client = new RecordingModelClient(false);
        ReActAgent agent = configuredKvAgent("OpenAI", false, new Model(client));
        RecordingModelContext context = new RecordingModelContext("sess-1");

        callModelForKvTest(agent, context, new MemorySession("sess-1"));

        assertFalse(agent.isKvReleaseWarningLogged());
        assertFalse(context.lastKwargs().containsKey("model"));
        assertTrue(client.lastOptions().getExtraFields().isEmpty());
        assertTrue(client.lastOptions().getRequestHeaders().isEmpty());
    }

    @Test
    void kvReleaseModelPassedWhenModelSupportsReleaseAndEnabled() {
        RecordingModelClient client = new RecordingModelClient(true);
        Model model = new Model(client);
        ReActAgent agent = configuredKvAgent("InferenceAffinity", true, model);
        RecordingModelContext context = new RecordingModelContext("sess-1");

        callModelForKvTest(agent, context, new MemorySession("sess-1"));

        assertSame(model, context.lastKwargs().get("model"));
        assertEquals("sess-1", client.lastOptions().getExtraFields().get("session_id"));
        assertEquals(Boolean.TRUE, client.lastOptions().getExtraFields().get("enable_cache_sharing"));
        assertTrue(client.lastOptions().getRequestHeaders().isEmpty());
        assertFalse(agent.isKvReleaseWarningLogged());
    }

    @Test
    void invokeForwardsRequestHeadersAndCopiesProviderMap() {
        RecordingModelClient client = new RecordingModelClient(false);
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
        RecordingModelClient client = new RecordingModelClient(false);
        ReActAgent agent = configuredAgent("headers-consume-agent", new Model(client));
        AtomicReference<Map<String, String>> headersAfterCall = new AtomicReference<>();
        AtomicReference<Map<String, Object>> extraAfterCall = new AtomicReference<>();
        AtomicReference<Object> responseAfterCall = new AtomicReference<>();
        ModelRequestHeadersRail headersRail = new ModelRequestHeadersRail(context ->
                CompletableFuture.completedFuture(Map.of("Authorization", "Bearer consume-secret")));
        AgentRail observerRail = new AgentRail() {
            @Override
            public CompletionStage<Void> afterModelCall(AgentCallbackContext context) {
                ModelCallInputs inputs = (ModelCallInputs) context.getInputs();
                headersAfterCall.set(inputs.getRequestHeaders());
                extraAfterCall.set(new LinkedHashMap<>(context.getExtra()));
                responseAfterCall.set(inputs.getResponse());
                return CompletableFuture.completedFuture(null);
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
            public CompletionStage<Void> onModelException(AgentCallbackContext context) {
                if (retryRequested.compareAndSet(false, true)) {
                    context.requestRetry(0);
                }
                return CompletableFuture.completedFuture(null);
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
    void retryAfterPreparationFailureConsumesOldHeadersBeforeCallbacks() {
        RecordingModelClient client = new RecordingModelClient(false);
        ReActAgent agent = configuredAgent("headers-preparation-retry-agent", new Model(client));
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicBoolean retryRequested = new AtomicBoolean();
        AtomicReference<Map<String, String>> firstAttemptHeadersOnException = new AtomicReference<>();
        AtomicReference<Map<String, String>> firstAttemptHeadersAfterCall = new AtomicReference<>();
        ModelRequestHeadersRail headersRail = new ModelRequestHeadersRail(context -> {
            int call = providerCalls.incrementAndGet();
            Map<String, String> headers = call == 1
                    ? Map.of("X-First-Attempt", "first-attempt-only")
                    : Map.of("Authorization", "Bearer preparation-2");
            return CompletableFuture.completedFuture(headers);
        });
        AgentRail retryObserverRail = new AgentRail() {
            @Override
            public CompletionStage<Void> onModelException(AgentCallbackContext context) {
                firstAttemptHeadersOnException.set(((ModelCallInputs) context.getInputs()).getRequestHeaders());
                if (retryRequested.compareAndSet(false, true)) {
                    context.requestRetry(0);
                }
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> afterModelCall(AgentCallbackContext context) {
                if (context.getRetryAttempt() == 0) {
                    firstAttemptHeadersAfterCall.set(((ModelCallInputs) context.getInputs()).getRequestHeaders());
                }
                return CompletableFuture.completedFuture(null);
            }
        };
        agent.registerRail(headersRail).toCompletableFuture().join();
        agent.registerRail(retryObserverRail).toCompletableFuture().join();

        try {
            RecordingModelContext context = new FailingOnceRecordingModelContext("headers-preparation-retry");
            callModelForKvTest(agent, context, new MemorySession("headers-preparation-retry"));

            assertTrue(firstAttemptHeadersOnException.get().isEmpty());
            assertTrue(firstAttemptHeadersAfterCall.get().isEmpty());
            assertEquals(2, providerCalls.get());
            assertEquals(Map.of("Authorization", "Bearer preparation-2"),
                    client.lastOptions().getRequestHeaders());
        } finally {
            agent.unregisterRail(retryObserverRail).toCompletableFuture().join();
            agent.unregisterRail(headersRail).toCompletableFuture().join();
        }
    }

    @Test
    void beforeModelAbortClearsHeadersBeforeExceptionAfterAndRetry() {
        RecordingModelClient client = new RecordingModelClient(false);
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
            public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
                if (abortFirstAttempt.compareAndSet(true, false)) {
                    throw new AbortError("abort first model attempt");
                }
                return CompletableFuture.completedFuture(null);
            }
        };
        abortRail.setPriority(50);
        AgentRail retryObserverRail = new AgentRail() {
            @Override
            public CompletionStage<Void> onModelException(AgentCallbackContext context) {
                exceptionHeaders.add(((ModelCallInputs) context.getInputs()).getRequestHeaders());
                if (retryRequested.compareAndSet(false, true)) {
                    context.requestRetry(0);
                }
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> afterModelCall(AgentCallbackContext context) {
                afterHeaders.add(((ModelCallInputs) context.getInputs()).getRequestHeaders());
                return CompletableFuture.completedFuture(null);
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
        RecordingModelClient client = new RecordingModelClient(false);
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

    private static ReActAgent configuredKvAgent(String provider, boolean enableKvRelease, Model model) {
        ReActAgent agent = new ReActAgent(new AgentCard("test", "test", "desc"));
        configureKvAgent(agent, provider, enableKvRelease, model);
        return agent;
    }

    private static void assertKvReleaseWarningOnceWhenNotSupportedAndEnabled(String provider) {
        RecordingModelClient client = new RecordingModelClient(false);
        Model model = new Model(client);
        ReActAgent agent = configuredKvAgent(provider, true, model);
        MemorySession session = new MemorySession("sess-1");
        RecordingModelContext context = new RecordingModelContext("sess-1");

        callModelForKvTest(agent, context, session);
        assertTrue(agent.isKvReleaseWarningLogged());
        assertFalse(context.lastKwargs().containsKey("model"));

        callModelForKvTest(agent, context, session);
        assertTrue(agent.isKvReleaseWarningLogged());
        assertFalse(context.lastKwargs().containsKey("model"));
        assertEquals(2, client.invokeCount());
    }

    private static void configureKvAgent(ReActAgent agent, String provider, boolean enableKvRelease, Model model) {
        ReActAgentConfig config = new ReActAgentConfig();
        config.configureModelClient(provider, "test-key", apiBase(provider), "test-model", false);
        config.configureContextEngine(100, null, false, enableKvRelease);
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        agent.configure(config);
        agent.setLlm(model);
    }

    private static String apiBase(String provider) {
        return switch (provider) {
            case "SiliconFlow" -> "https://api.siliconflow.cn/v1";
            case "InferenceAffinity" -> "http://test:8111";
            default -> "https://api.openai.com/v1";
        };
    }

    private static Object callModelForKvTest(ReActAgent agent, RecordingModelContext context, AgentSessionApi session) {
        AgentCallbackContext ctx = new AgentCallbackContext(agent);
        ctx.setSession(session);
        ctx.setContext(context);
        return agent._call_model(ctx, context, null);
    }

    private static final class RecordingModelClient implements Model.ModelClient {
        private final boolean supportsKvCacheRelease;
        private int invokeCount;
        private ModelInvokeOptions lastOptions;

        private RecordingModelClient(boolean supportsKvCacheRelease) {
            this.supportsKvCacheRelease = supportsKvCacheRelease;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeCount++;
            lastOptions = options;
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }

        @Override
        public boolean supportsKvCacheRelease() {
            return supportsKvCacheRelease;
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

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeCount++;
            return CompletableFuture.failedFuture(new AssertionError("Streaming path must not invoke the model"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            streamOptions = options;
            return List.of(AssistantMessageChunk.builder().content("streamed").build()).iterator();
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
                    List.of(new UserMessage("question")),
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
