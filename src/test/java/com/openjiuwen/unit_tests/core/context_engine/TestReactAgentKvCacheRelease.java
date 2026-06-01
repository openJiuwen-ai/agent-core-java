/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ReActAgent KV cache release entry-point wiring.
 * <p>
 * Mirrors Python's
 * {@code agent-core-0.1.12/tests/unit_tests/core/context_engine/test_react_agent_kv_cache_release.py}.
 */
@DisplayName("TestReactAgentKvCacheRelease")
class TestReactAgentKvCacheRelease {

    private static final String WARNING_SUBSTR = "ContextEngineConfig.enable_kv_cache_release is True";
    private static final String KV_NOT_TAKE_EFFECT_SUBSTR = "KV cache release will not take effect.";

    @Test
    @DisplayName("KV release warning once when OpenAI is not supported and enabled")
    void testKvReleaseWarningOnceWhenOpenAiNotSupportedAndEnabled() throws Exception {
        Fixture fixture = newFixture("OpenAI", true, false);
        try (CapturedLogs logs = CapturedLogs.attach(Loggers.AGENT)) {
            fixture.invoke();
            assertEquals(1, logs.countKvWarnings());
            assertFalse(fixture.context.lastKwargs.containsKey("model"));
            assertNull(fixture.model.lastInvokeKwargs);

            logs.clear();
            fixture.invoke();
            assertEquals(0, logs.countKvWarnings());
        }
    }

    @Test
    @DisplayName("KV release warning once when SiliconFlow is not supported and enabled")
    void testKvReleaseWarningOnceWhenSiliconFlowNotSupportedAndEnabled() throws Exception {
        Fixture fixture = newFixture("SiliconFlow", true, false);
        try (CapturedLogs logs = CapturedLogs.attach(Loggers.AGENT)) {
            fixture.invoke();
            assertEquals(1, logs.countKvWarnings());
            assertFalse(fixture.context.lastKwargs.containsKey("model"));
            assertNull(fixture.model.lastInvokeKwargs);

            logs.clear();
            fixture.invoke();
            assertEquals(0, logs.countKvWarnings());
        }
    }

    @Test
    @DisplayName("KV release warning again after switching provider")
    void testKvReleaseWarningAgainAfterSwitchProvider() throws Exception {
        Fixture fixture = newFixture("OpenAI", true, false);
        try (CapturedLogs logs = CapturedLogs.attach(Loggers.AGENT)) {
            fixture.invoke();
            assertEquals(1, logs.countKvWarnings());

            fixture.configure("SiliconFlow", true);
            logs.clear();
            fixture.invoke();
            assertEquals(1, logs.countKvWarnings());
        }
    }

    @Test
    @DisplayName("KV release no warning when release disabled")
    void testKvReleaseNoWarningWhenReleaseDisabled() throws Exception {
        Fixture fixture = newFixture("OpenAI", false, false);
        try (CapturedLogs logs = CapturedLogs.attach(Loggers.AGENT)) {
            fixture.invoke();
            assertEquals(0, logs.countKvWarnings());
            assertFalse(fixture.context.lastKwargs.containsKey("model"));
            assertNull(fixture.model.lastInvokeKwargs);
        }
    }

    @Test
    @DisplayName("KV release model passed when InferenceAffinity and enabled")
    void testKvReleaseModelPassedWhenInferenceAffinityAndEnabled() throws Exception {
        Fixture fixture = newFixture("InferenceAffinity", true, true);
        try (CapturedLogs logs = CapturedLogs.attach(Loggers.AGENT)) {
            fixture.invoke();

            assertEquals(0, logs.countKvWarnings());
            assertSame(fixture.model, fixture.context.lastKwargs.get("model"));
            assertEquals("sess-1", fixture.model.lastInvokeKwargs.get("session_id"));
            assertEquals(Boolean.TRUE, fixture.model.lastInvokeKwargs.get("enable_cache_sharing"));
        }
    }

    private static Fixture newFixture(String provider, boolean enableKvRelease, boolean modelSupportsKvRelease)
            throws Exception {
        RecordingModel model = new RecordingModel(modelSupportsKvRelease);
        TestableReActAgent agent = new TestableReActAgent(model);
        SimpleSession session = new SimpleSession("sess-1");
        Fixture fixture = new Fixture(agent, model, session);
        fixture.configure(provider, enableKvRelease);
        return fixture;
    }

    private static final class Fixture {
        private final TestableReActAgent agent;
        private final RecordingModel model;
        private final SimpleSession session;
        private RecordingContext context;

        private Fixture(TestableReActAgent agent, RecordingModel model, SimpleSession session) {
            this.agent = agent;
            this.model = model;
            this.session = session;
        }

        void configure(String provider, boolean enableKvRelease) throws Exception {
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .maxIterations(1)
                    .build();
            config.configureModelClient(
                    provider,
                    "test-key",
                    provider.equals("InferenceAffinity") ? "http://test:8111" : "https://api.example.test/v1",
                    "test-model",
                    false);
            config.getContextEngineConfig().setEnableKvCacheRelease(enableKvRelease);
            agent.configure(config);
            context = new RecordingContext(session.getSessionId(), "default_context_id");
            setContextEngine(agent, new RecordingContextEngine(context));
        }

        void invoke() {
            agent.invoke(Map.of("query", "hello"), session);
        }
    }

    private static void setContextEngine(ReActAgent agent, ContextEngine contextEngine) throws Exception {
        Field field = ReActAgent.class.getDeclaredField("contextEngine");
        field.setAccessible(true);
        field.set(agent, contextEngine);
    }

    private static final class TestableReActAgent extends ReActAgent {
        private final RecordingModel model;

        private TestableReActAgent(RecordingModel model) {
            super(AgentCard.builder().id("test").name("test").build());
            this.model = model;
        }

        @Override
        protected Model getLlm() {
            return model;
        }
    }

    private static final class RecordingModel extends Model {
        private final boolean supportsKvCacheRelease;
        private Map<String, Object> lastInvokeKwargs;

        private RecordingModel(boolean supportsKvCacheRelease) {
            super(
                    ModelClientConfig.builder()
                            .clientProvider("OpenAI")
                            .apiKey("test-key")
                            .apiBase("https://api.example.test/v1")
                            .verifySsl(false)
                            .build(),
                    ModelRequestConfig.builder().modelName("test-model").build());
            this.supportsKvCacheRelease = supportsKvCacheRelease;
        }

        @Override
        public boolean supportsKvCacheRelease() {
            return supportsKvCacheRelease;
        }

        @Override
        public AssistantMessage invoke(Object messages,
                                       Object tools,
                                       Float temperature,
                                       Float topP,
                                       String model,
                                       Integer maxTokens,
                                       String stop,
                                       BaseOutputParser outputParser,
                                       Float timeout,
                                       Map<String, Object> kwargs) {
            lastInvokeKwargs = kwargs == null ? null : new LinkedHashMap<>(kwargs);
            return new AssistantMessage("ok");
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages,
                                                      Object tools,
                                                      Float temperature,
                                                      Float topP,
                                                      String model,
                                                      Integer maxTokens,
                                                      String stop,
                                                      BaseOutputParser outputParser,
                                                      Float timeout,
                                                      Map<String, Object> kwargs) {
            lastInvokeKwargs = kwargs == null ? null : new LinkedHashMap<>(kwargs);
            return List.of(AssistantMessageChunk.builder().content("ok").build()).iterator();
        }
    }

    private static final class RecordingContextEngine extends ContextEngine {
        private final RecordingContext context;

        private RecordingContextEngine(RecordingContext context) {
            this.context = context;
        }

        @Override
        public ModelContext createContext(
                String contextId,
                Session session,
                List<ProcessorSpec> processors,
                List<BaseMessage> historyMessages,
                TokenCounter tokenCounter) {
            return context;
        }
    }

    private static final class RecordingContext extends ModelContext {
        private final String sessionId;
        private final String contextId;
        private final List<BaseMessage> messages = new ArrayList<>();
        private Map<String, Object> lastKwargs = Map.of();
        private final Tool reloaderTool = new NoopTool();

        private RecordingContext(String sessionId, String contextId) {
            this.sessionId = sessionId;
            this.contextId = contextId;
        }

        @Override
        public int size() {
            return messages.size();
        }

        @Override
        public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
            if (size == null || size >= messages.size()) {
                return new ArrayList<>(messages);
            }
            return new ArrayList<>(messages.subList(messages.size() - size, messages.size()));
        }

        @Override
        public void setMessages(List<BaseMessage> messages, boolean withHistory) {
            this.messages.clear();
            this.messages.addAll(messages);
        }

        @Override
        public List<BaseMessage> popMessages(int size, boolean withHistory) {
            int start = Math.max(0, messages.size() - size);
            List<BaseMessage> removed = new ArrayList<>(messages.subList(start, messages.size()));
            messages.subList(start, messages.size()).clear();
            return removed;
        }

        @Override
        public void clearMessages(boolean withHistory) {
            messages.clear();
        }

        @Override
        public List<BaseMessage> addMessages(List<BaseMessage> messages) {
            this.messages.addAll(messages);
            return new ArrayList<>(this.messages);
        }

        @Override
        public ContextWindow getContextWindow(
                List<BaseMessage> systemMessages,
                List<ToolInfo> tools,
                Integer windowSize,
                Integer dialogueRound,
                Map<String, Object> kwargs) {
            lastKwargs = kwargs == null ? Map.of() : new LinkedHashMap<>(kwargs);
            return ContextWindow.builder()
                    .systemMessages(systemMessages != null ? systemMessages : List.of())
                    .contextMessages(new ArrayList<>(messages))
                    .tools(tools != null ? tools : List.of())
                    .build();
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
            return contextId;
        }

        @Override
        public TokenCounter tokenCounter() {
            return null;
        }

        @Override
        public Tool reloaderTool() {
            return reloaderTool;
        }
    }

    private static final class NoopTool extends Tool {
        private NoopTool() {
            super(ToolCard.builder().id("noop-reloader").name("reload_original_context_messages").build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of().iterator();
        }
    }

    private static final class SimpleSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new HashMap<>();

        private SimpleSession(String sessionId) {
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

    private static final class CapturedLogs implements AutoCloseable {
        private final LoggerProtocol logger;
        private final CapturingHandler handler = new CapturingHandler();

        private CapturedLogs(LoggerProtocol logger) {
            this.logger = logger;
            this.handler.setLevel(Level.ALL);
            logger.setLevel(20);
            logger.addHandler(handler);
        }

        private static CapturedLogs attach(LoggerProtocol logger) {
            return new CapturedLogs(logger);
        }

        private long countKvWarnings() {
            return handler.messages.stream()
                    .filter(msg -> msg.contains(WARNING_SUBSTR) && msg.contains(KV_NOT_TAKE_EFFECT_SUBSTR))
                    .count();
        }

        private void clear() {
            handler.messages.clear();
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
        }
    }

    private static final class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
