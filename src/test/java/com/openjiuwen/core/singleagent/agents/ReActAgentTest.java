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
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        assertFalse(agent.isKvReleaseWarningLogged());
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

    private static final class RecordingModelContext implements ModelContext {
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
