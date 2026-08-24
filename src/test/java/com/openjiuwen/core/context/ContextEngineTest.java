/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
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
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.context_engine.test_context_engine} in
 * {@code tests/unit_tests/core/context_engine/test_context_engine.py}.</p>
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
        assertThat(provider.timeouts.get(0)).isEqualTo(0.25d);
    }

    @Test
    void createContextWithSessionNullUsesDefaultSessionId() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());

        ModelContext context = engine.createContext("ctx", null);

        assertThat(context.sessionId()).isEqualTo(ContextEngine.DEFAULT_SESSION_ID);
        assertThat(context.contextId()).isEqualTo("ctx");
        assertThat(engine.getContext("ctx", ContextEngine.DEFAULT_SESSION_ID)).isSameAs(context);
    }

    @Test
    void createContextEmptyHistoryMessages() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());

        ModelContext context = engine.createContext("ctx", new RecordingSession("session-empty"),
                null, List.of(), null);

        assertThat(context.getMessages(null, true)).isEmpty();
    }

    @Test
    void createContextHistoryMessagesNullCreatesEmpty() {
        ModelContext context = new ContextEngine(new ContextEngineConfig())
                .createContext("ctx", new RecordingSession("session-null-history"));

        assertThat(context.getMessages(null, true)).isEmpty();
    }

    @Test
    void createContextDefaultContextId() {
        ModelContext context = new ContextEngine(new ContextEngineConfig())
                .createContext(null, new RecordingSession("session-default-context"));

        assertThat(context.contextId()).isEqualTo(ContextEngine.DEFAULT_CONTEXT_ID);
    }

    @Test
    void createContextWithCustomTokenCounter() {
        ModelContext.TokenCounterPort tokenCounter = messages -> 42;
        ModelContext context = new ContextEngine(new ContextEngineConfig()).createContext(
                "ctx", new RecordingSession("session-token"), null, null, tokenCounter);

        assertThat(context.tokenCounter()).isSameAs(tokenCounter);
        assertThat(context.tokenCounter().countTokens(List.of(new BaseMessage("user", "hello")))).isEqualTo(42);
    }

    @Test
    void createContextWithRegisteredProcessor() {
        ContextEngine.registerProcessor("T00920CompactProcessor", RecordingCompactProcessor::new);
        ModelContext context = new ContextEngine(new ContextEngineConfig()).createContext(
                "ctx",
                new RecordingSession("session-registered-processor"),
                List.of(new ContextEngine.ProcessorSpec("T00920CompactProcessor", Map.of("enabled", true))),
                null,
                null);

        Object result = ((SessionModelContext) context).compressContext(
                List.of("T00920CompactProcessor"), Map.of()).toCompletableFuture().join();

        assertThat(result).isEqualTo(SessionModelContext.ACTIVE_COMPRESSION_RESULT_NOOP);
        assertThat(ContextEngine.registeredProcessorTypes()).contains("T00920CompactProcessor");
    }

    @Test
    void createContextUnknownProcessorTypeRaises() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());

        assertThatThrownBy(() -> engine.createContext("ctx", new RecordingSession("session-missing-processor"),
                List.of(new ContextEngine.ProcessorSpec("UnknownProcessorType", Map.of())),
                null,
                null))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getCode())
                .isEqualTo(StatusCode.CONTEXT_EXECUTION_ERROR.getCode());
    }

    @Test
    void createContextProcessorInitFailsRaises() {
        ContextEngine.registerProcessor("T00920FailingProcessor", ignored -> {
            throw new IllegalStateException("init failed");
        });
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());

        assertThatThrownBy(() -> engine.createContext("ctx", new RecordingSession("session-failing-processor"),
                List.of(new ContextEngine.ProcessorSpec("T00920FailingProcessor", Map.of())),
                null,
                null))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getCode())
                .isEqualTo(StatusCode.CONTEXT_EXECUTION_ERROR.getCode());
    }

    @Test
    void compressContextReturnsNoopWhenRequestedProcessorsDoNotMatch() {
        ContextEngine.registerProcessor("T00920NoopCompactProcessor", RecordingCompactProcessor::new);
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        RecordingSession session = new RecordingSession("session-noop");
        engine.createContext("ctx", session,
                List.of(new ContextEngine.ProcessorSpec("T00920NoopCompactProcessor", Map.of())),
                null,
                null);

        Object result = engine.compressContext("ctx", session, null, List.of("DialogueCompressor"), Map.of());

        assertThat(result).isEqualTo(SessionModelContext.ACTIVE_COMPRESSION_RESULT_NOOP);
    }

    @Test
    void getContextReturnsNullWhenNotExists() {
        assertThat(new ContextEngine(new ContextEngineConfig())
                .getContext("nonexistent", "session-none")).isNull();
    }

    @Test
    void getContextWithDottedContextId() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        RecordingSession session = new RecordingSession("session-dotted");

        ModelContext context = engine.createContext("x.y", session);

        assertThat(engine.getContext("x.y", session.getSessionId())).isSameAs(context);
        assertThat(context.contextId()).isEqualTo("x_y");
    }

    @Test
    void getContextDefaultParams() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        ModelContext context = engine.createContext(ContextEngine.DEFAULT_CONTEXT_ID, null);

        assertThat(engine.getContext()).isSameAs(context);
    }

    @Test
    void clearContextBySessionWhenSessionHasNoContexts() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());

        engine.clearContext(null, "session-empty");

        assertThat(engine.getContext("any", "session-empty")).isNull();
    }

    @Test
    void clearContextBySessionAndContextWhenContextNotExists() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());

        engine.clearContext("missing", "session-empty");

        assertThat(engine.getContext("missing", "session-empty")).isNull();
    }

    @Test
    void clearContextAllThenPoolEmpty() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        RecordingSession first = new RecordingSession("session-clear-one");
        RecordingSession second = new RecordingSession("session-clear-two");
        engine.createContext("c1", first);
        engine.createContext("c2", second);

        engine.clearContext();

        assertThat(engine.getContext("c1", first.getSessionId())).isNull();
        assertThat(engine.getContext("c2", second.getSessionId())).isNull();
    }

    @Test
    void saveContextsSessionNullReturnsNull() {
        assertThat(new ContextEngine(new ContextEngineConfig()).saveContexts(null)).isNull();
    }

    @Test
    void saveContextsPartialContextIdsMissingSkipped() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        RecordingSession session = new RecordingSession("session-partial-save");
        engine.createContext("exists", session);

        Map<String, Object> saved = engine.saveContexts(session, List.of("exists", "missing"));

        assertThat(saved).containsOnlyKeys("exists");
    }

    @Test
    void saveContextsContextIdsNullSavesAllForSession() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        RecordingSession session = new RecordingSession("session-save-all");
        engine.createContext("c1", session);
        engine.createContext("c2", session);

        Map<String, Object> saved = engine.saveContexts(session);

        assertThat(saved).containsKeys("c1", "c2");
        assertThat(session.getState("context")).isSameAs(saved);
    }

    @Test
    void engineConfigNullUsesDefault() {
        ModelContext context = new ContextEngine(null)
                .createContext("ctx", new RecordingSession("session-null-config"));

        assertThat(context).isInstanceOf(SessionModelContext.class);
    }

    @Test
    void engineCustomConfigReflectedInContextWindow() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setDefaultWindowMessageNum(1);
        ContextEngine engine = new ContextEngine(config);
        ModelContext context = engine.createContext("ctx", new RecordingSession("session-window"));
        context.addMessages(List.of(new UserMessage("first"), new AssistantMessage("second")))
                .toCompletableFuture().join();

        ContextWindow window = context.getContextWindow(null, null, null, null, null)
                .toCompletableFuture().join();

        assertThat(contents(window.getContextMessages())).containsExactly("second");
    }

    @Test
    void registerProcessorRegistersInMap() {
        ContextEngine.registerProcessor("T00920RegisteredProcessor", RecordingCompactProcessor::new);

        assertThat(ContextEngine.registeredProcessorTypes()).contains("T00920RegisteredProcessor");
    }

    @Test
    void multipleSessionsAndContextsIsolated() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        RecordingSession first = new RecordingSession("session-multi-one");
        RecordingSession second = new RecordingSession("session-multi-two");

        ModelContext c1 = engine.createContext("ctx_a", first);
        ModelContext c2 = engine.createContext("ctx_b", first);
        ModelContext c3 = engine.createContext("ctx_a", second);

        assertThat(c1).isNotSameAs(c2);
        assertThat(c1).isNotSameAs(c3);
        assertThat(engine.getContext("ctx_a", first.getSessionId())).isSameAs(c1);
        assertThat(engine.getContext("ctx_b", first.getSessionId())).isSameAs(c2);
        assertThat(engine.getContext("ctx_a", second.getSessionId())).isSameAs(c3);
    }

    @Test
    void saveContextHistoryOnlyLoadsPersisted() {
        ContextEngine writer = new ContextEngine(new ContextEngineConfig());
        RecordingSession session = new RecordingSession("session-load-history");
        ModelContext context = writer.createContext("ctx", session, null, List.of(new SystemMessage("history")), null);
        context.addMessages(new UserMessage("message")).toCompletableFuture().join();
        writer.saveContexts(session, List.of("ctx"));

        ModelContext restored = new ContextEngine(new ContextEngineConfig()).createContext("ctx", session);

        assertThat(contents(restored.getMessages(null, true))).containsExactly("history", "message");
    }

    @Test
    void getContextWindowCombinesSystemHistoryAndMessages() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        ModelContext context = engine.createContext("ctx", new RecordingSession("session-window-combine"),
                null, List.of(new UserMessage("history")), null);
        context.addMessages(new AssistantMessage("answer")).toCompletableFuture().join();

        ContextWindow window = context.getContextWindow(List.of(new SystemMessage("system")), null, null, null, null)
                .toCompletableFuture().join();

        assertThat(contents(window.getMessages())).containsExactly("system", "history", "answer");
    }

    @Test
    void getContextWindowWindowSizeUsesMostRecentMessages() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        ModelContext context = engine.createContext("ctx", new RecordingSession("session-window-size"));
        context.addMessages(List.of(new UserMessage("one"), new AssistantMessage("two"), new UserMessage("three")))
                .toCompletableFuture().join();

        ContextWindow window = context.getContextWindow(null, null, 2, null, null).toCompletableFuture().join();

        assertThat(contents(window.getContextMessages())).containsExactly("two", "three");
    }

    @Test
    void saveContextThenPopWithHistory() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        ModelContext context = engine.createContext("ctx", new RecordingSession("session-pop-history"),
                null, List.of(new SystemMessage("history")), null);
        context.addMessages(List.of(new UserMessage("one"), new AssistantMessage("two"))).toCompletableFuture().join();

        List<BaseMessage> popped = context.popMessages(1, true);

        assertThat(contents(popped)).containsExactly("two");
        assertThat(contents(context.getMessages(null, true))).containsExactly("history", "one");
    }

    @Test
    void popWithoutHistoryDoesNotRemoveHistory() {
        ModelContext context = new ContextEngine(new ContextEngineConfig()).createContext(
                "ctx", new RecordingSession("session-pop-no-history"), null,
                List.of(new SystemMessage("history")), null);

        List<BaseMessage> popped = context.popMessages(1, false);

        assertThat(popped).isEmpty();
        assertThat(contents(context.getMessages(null, true))).containsExactly("history");
    }

    @Test
    void setMessagesWithoutHistoryKeepsHistory() {
        ModelContext context = new ContextEngine(new ContextEngineConfig()).createContext(
                "ctx", new RecordingSession("session-set-no-history"), null,
                List.of(new SystemMessage("history")), null);

        context.setMessages(List.of(new UserMessage("fresh")), false);

        assertThat(contents(context.getMessages(null, true))).containsExactly("history", "fresh");
        assertThat(contents(context.getMessages(null, false))).containsExactly("fresh");
    }

    @Test
    void saveContextsContextIdsNoneSavesAllAndLoads() {
        ContextEngine writer = new ContextEngine(new ContextEngineConfig());
        RecordingSession session = new RecordingSession("session-load-all");
        writer.createContext("c1", session).addMessages(new UserMessage("one")).toCompletableFuture().join();
        writer.createContext("c2", session).addMessages(new UserMessage("two")).toCompletableFuture().join();
        writer.saveContexts(session);

        ContextEngine reader = new ContextEngine(new ContextEngineConfig());
        ModelContext c1 = reader.createContext("c1", session);
        ModelContext c2 = reader.createContext("c2", session);

        assertThat(contents(c1.getMessages(null, true))).containsExactly("one");
        assertThat(contents(c2.getMessages(null, true))).containsExactly("two");
    }

    @Test
    void compressContextMissingContextRaises() {
        assertThatThrownBy(() -> new ContextEngine(new ContextEngineConfig())
                .compressContext("missing", new RecordingSession("session-missing-context")))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("cannot find context");
    }

    @Test
    void clearContextBySessionOnlyKeepsOtherSessionContext() {
        ContextEngine engine = new ContextEngine(new ContextEngineConfig());
        RecordingSession first = new RecordingSession("session-clear-first");
        RecordingSession second = new RecordingSession("session-clear-second");
        engine.createContext("ctx", first);
        ModelContext retained = engine.createContext("ctx", second);

        engine.clearContext(null, first.getSessionId());

        assertThat(engine.getContext("ctx", first.getSessionId())).isNull();
        assertThat(engine.getContext("ctx", second.getSessionId())).isSameAs(retained);
    }

    private static List<String> contents(List<BaseMessage> messages) {
        return messages.stream().map(BaseMessage::getContentAsString).toList();
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
