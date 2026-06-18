/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.context_engine.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for {@link ContextEngine}.
 *
 * <p>Mirrors Python's {@code ContextEngine} in
 * {@code openjiuwen/core/context_engine/context_engine.py}.</p>
 */
class ContextEngineTest {

    @Test
    void createContextUsesSessionHistoryAndDottedContextIds() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        RecordingSession session = new RecordingSession("session-a");
        List<BaseMessage> history = List.of(new BaseMessage("user", "hello"));

        ModelContext context = engine.createContext("a.b.c", session, null, history, null);

        assertThat(context).isInstanceOf(SessionModelContext.class);
        assertThat(context.sessionId()).isEqualTo("session-a");
        assertThat(context.contextId()).isEqualTo("a_b_c");
        assertThat(context.getMessages(null, true)).extracting(BaseMessage::getContent).containsExactly("hello");
        assertThat(engine.getContext("a.b.c", "session-a")).isSameAs(context);
    }

    @Test
    void createContextReusesExistingPerSessionAndKeepsSessionsIsolated() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        RecordingSession firstSession = new RecordingSession("same-context");
        RecordingSession secondSession = new RecordingSession("other-context");

        ModelContext first = engine.createContext("ctx", firstSession);
        ModelContext reused = engine.createContext("ctx", firstSession);
        ModelContext isolated = engine.createContext("ctx", secondSession);

        assertThat(reused).isSameAs(first);
        assertThat(isolated).isNotSameAs(first);
        assertThat(engine.getContext("ctx", "same-context")).isSameAs(first);
        assertThat(engine.getContext("ctx", "other-context")).isSameAs(isolated);
    }

    @Test
    void saveContextsPersistsAndCreateContextLoadsSessionState() {
        ContextEngine writer = new ContextEngine(new ContextEngineConfig());
        RecordingSession session = new RecordingSession("session-save");
        SessionModelContext context = (SessionModelContext) writer.createContext("ctx", session);
        context.addMessages(new BaseMessage("user", "persisted")).toCompletableFuture().join();

        Map<String, Object> saved = writer.saveContexts(session, List.of("ctx"));
        ContextEngine reader = new ContextEngine(new ContextEngineConfig());
        ModelContext restored = reader.createContext("ctx", session);

        assertThat(saved).containsKey("ctx");
        assertThat(session.getState("context")).isSameAs(saved);
        assertThat(restored.getMessages(null, true)).extracting(BaseMessage::getContent)
                .containsExactly("persisted");
    }

    @Test
    void clearContextFollowsAllSessionAndSingleContextModes() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        RecordingSession firstSession = new RecordingSession("session-1");
        RecordingSession secondSession = new RecordingSession("session-2");
        engine.createContext("one", firstSession);
        engine.createContext("two", firstSession);
        engine.createContext("one", secondSession);

        engine.clearContext("one", "session-1");
        assertThat(engine.getContext("one", "session-1")).isNull();
        assertThat(engine.getContext("two", "session-1")).isNotNull();

        engine.clearContext(null, "session-1");
        assertThat(engine.getContext("two", "session-1")).isNull();
        assertThat(engine.getContext("one", "session-2")).isNotNull();

        engine.clearContext();
        assertThat(engine.getContext("one", "session-2")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void registeredProcessorCanBeCreatedAndCompressedThroughEngine() {
        ContextEngine.registerProcessor("T00919CompactProcessor", RecordingCompactProcessor::new);
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        RecordingSession session = new RecordingSession("session-processor");
        engine.createContext("ctx", session,
                List.of(new ContextEngine.ProcessorSpec("T00919CompactProcessor", Map.of("enabled", true))),
                null,
                null);

        Object result = engine.compressContext("ctx", session, null,
                List.of("T00919CompactProcessor"),
                Map.of("return_state", true));

        assertThat(ContextEngine.registeredProcessorTypes()).contains("T00919CompactProcessor");
        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> payload = (Map<String, Object>) result;
        assertThat(payload.get("result")).isEqualTo(SessionModelContext.ACTIVE_COMPRESSION_RESULT_COMPRESSED);
        assertThat(payload.get("compact_summary")).isEqualTo("recorded by test processor");
    }

    @Test
    void unknownProcessorAndMissingContextRaiseContextExecutionError() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        RecordingSession session = new RecordingSession("session-error");

        assertThatThrownBy(() -> engine.createContext("ctx", session,
                List.of(new ContextEngine.ProcessorSpec("MissingProcessor", Map.of())),
                null,
                null))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getCode())
                .isEqualTo(StatusCode.CONTEXT_EXECUTION_ERROR.getCode());

        assertThatThrownBy(() -> engine.compressContext("missing", session))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("cannot find context");
    }

    @Test
    void openrouterConfigPrefetchesModelWindowTokensBeforeContextCreation() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setEnableOpenrouterModelContextWindowTokens(true);
        config.setOpenrouterRequestTimeout(0.25d);
        RecordingTokenProvider provider = new RecordingTokenProvider();
        ContextEngine engine = new ContextEngine(config, null, null, provider);

        engine.createContext("ctx", new RecordingSession("session-openrouter"));

        assertThat(provider.timeouts).isNotEmpty();
        assertThat(provider.timeouts.getFirst()).isEqualTo(0.25d);
    }

    private static final class RecordingSession implements ContextEngine.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private RecordingSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        public Object getState(String key) {
            return state.get(key);
        }

        public void updateState(Map<String, Object> update) {
            state.putAll(update);
        }
    }

    private static final class RecordingTokenProvider implements SessionModelContext.ModelContextWindowTokenProvider {
        private final List<Double> timeouts = new ArrayList<>();

        @Override
        public Map<String, Integer> fetch(double timeoutSeconds) {
            timeouts.add(timeoutSeconds);
            return Map.of("test-model", 8192);
        }
    }

    /**
     * Compression processor test double.
     *
     * <p>Mirrors Python's registered {@code ContextProcessor} collaborator in
     * {@code openjiuwen/core/context_engine/context_engine.py}.</p>
     */
    private static final class RecordingCompactProcessor implements SessionModelContext.ContextProcessorPort {
        private final Object config;

        private RecordingCompactProcessor(Object config) {
            this.config = config;
        }

        @Override
        public String processorType() {
            return "T00919CompactProcessor";
        }

        @Override
        public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                                List<BaseMessage> messages,
                                                                                boolean force,
                                                                                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(
                    new RecordingEvent(config),
                    messages,
                    null
            ));
        }
    }

    private static final class RecordingEvent implements SessionModelContext.ContextProcessorEventPort {
        private final Object config;

        private RecordingEvent(Object config) {
            this.config = config;
        }

        @Override
        public String compactSummary() {
            return "recorded by test processor";
        }

        @Override
        public Object compressionUsage() {
            return Map.of("config", config);
        }
    }
}
