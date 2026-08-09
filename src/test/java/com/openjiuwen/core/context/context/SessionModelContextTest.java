/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for session model context behavior.
 *
 * <p>Mirrors Python's {@code SessionModelContext} in
 * {@code openjiuwen/core/context_engine/context/context.py}.</p>
 */
class SessionModelContextTest {

    @Test
    void addMessagesPreservesHistoryBoundaryAndContextMessageIds() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setMaxContextMessageNum(4);
        BaseMessage history = new BaseMessage("user", "history");
        SessionModelContext context = new SessionModelContext("ctx", "session", config, List.of(history), List.of(),
                null);

        BaseMessage current = new BaseMessage("assistant", "current");
        context.addMessages(current).toCompletableFuture().join();

        assertThat(context.length()).isEqualTo(2);
        assertThat(context.getMessages(null, true)).containsExactly(history, current);
        assertThat(context.getMessages(null, false)).containsExactly(current);
        assertThat(history.getMetadata()).containsKey(SessionModelContext.CONTEXT_MESSAGE_ID_KEY);
        assertThat(current.getMetadata()).containsKey(SessionModelContext.CONTEXT_MESSAGE_ID_KEY);
    }

    @Test
    void getContextWindowAddsReloadPromptAndAppliesWindowSize() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setEnableReload(true);
        config.setDefaultWindowMessageNum(4);
        config.setDefaultWindowRoundNum(1);
        SessionModelContext context = new SessionModelContext("ctx", "session", config);
        context.addMessages(List.of(
                new BaseMessage("user", "old question"),
                new AssistantMessage("old answer"),
                new BaseMessage("user", "latest question"),
                new AssistantMessage("latest answer")
        )).toCompletableFuture().join();

        ContextWindow window = context.getContextWindow(
                List.of(new SystemMessage("system")),
                List.of(ToolInfo.builder().name("search").description("Search docs").build()),
                null,
                null,
                Map.of()
        ).toCompletableFuture().join();

        assertThat(window.getSystemMessages()).hasSize(2);
        assertThat(window.getSystemMessages().get(0).getContent()).isEqualTo("system");
        assertThat(window.getSystemMessages().get(1).getContentAsString())
                .contains("reload_original_context_messages");
        assertThat(window.getContextMessages()).extracting(BaseMessage::getContent)
                .containsExactly("latest question", "latest answer");
        assertThat(window.getStatistic().getTools()).isEqualTo(1);
        assertThat(window.getStatistic().getTotalDialogues()).isEqualTo(1);
    }

    @Test
    void statisticUsesLastAssistantUsageMetadataBeforeFallbackCounting() {
        ContextEngineConfig config = new ContextEngineConfig();
        AssistantMessage assistant = new AssistantMessage("answer");
        assistant.setUsageMetadata(UsageMetadata.builder().totalTokens(77).build());
        SessionModelContext context = new SessionModelContext("ctx", "session", config);

        context.addMessages(List.of(new BaseMessage("user", "question"), assistant)).toCompletableFuture().join();

        assertThat(context.statistic().getTotalTokens()).isEqualTo(77);
        assertThat(context.statistic().getUserMessages()).isEqualTo(1);
        assertThat(context.statistic().getAssistantMessages()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void compressContextReturnsStatePayloadWhenRequested() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setModelName("gpt-5");
        RecordingCompressionProcessor processor = new RecordingCompressionProcessor();
        SessionModelContext context = new SessionModelContext("ctx", "session", config, List.of(), List.of(processor),
                null);

        Object result = context.compressContext(List.of("compact"), Map.of("return_state", true))
                .toCompletableFuture().join();

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> payload = (Map<String, Object>) result;
        assertThat(payload.get("result")).isEqualTo(SessionModelContext.ACTIVE_COMPRESSION_RESULT_COMPRESSED);
        assertThat(payload.get("compact_summary")).isEqualTo("kept latest turn");
        Map<String, Object> state = (Map<String, Object>) payload.get("state");
        assertThat(state.get("status")).isEqualTo("completed");
        assertThat(state.get("context_max")).isEqualTo(400000);
        assertThat(state.get("compression_usage")).isEqualTo(Map.of("input_tokens", 9));
    }

    @Test
    @SuppressWarnings("unchecked")
    void compressContextNoMatchingProcessorReturnsNoopState() {
        ContextEngineConfig config = new ContextEngineConfig();
        SessionModelContext context = new SessionModelContext("ctx", "session", config);

        Object result = context.compressContext(List.of("missing"), Map.of("return_state", true))
                .toCompletableFuture().join();

        Map<String, Object> payload = (Map<String, Object>) result;
        assertThat(payload.get("result")).isEqualTo(SessionModelContext.ACTIVE_COMPRESSION_RESULT_NOOP);
        Map<String, Object> state = (Map<String, Object>) payload.get("state");
        assertThat(state.get("status")).isEqualTo("skipped");
        assertThat(state.get("reason")).isEqualTo("no_matching_processor");
    }

    @Test
    void reloaderToolRestoresOffloadedInMemoryMessages() {
        ContextEngineConfig config = new ContextEngineConfig();
        SessionModelContext context = new SessionModelContext("ctx", "session", config);
        context.offloadMessages("handle-1", List.of(new BaseMessage("user", "offloaded")));

        SessionModelContext.ReloaderTool tool = (SessionModelContext.ReloaderTool) context.reloaderTool();
        String reloaded = tool.reloadOriginalContextMessages("handle-1", "in_memory");

        assertThat(tool.name()).isEqualTo("reload_original_context_messages");
        assertThat(reloaded).contains("reload messages with handle=handle-1");
        assertThat(reloaded).contains("offloaded");
    }

    @Test
    void saveAndLoadStateUseContextIdEnvelope() {
        ContextEngineConfig config = new ContextEngineConfig();
        SessionModelContext source = new SessionModelContext("ctx", "session", config);
        source.addMessages(List.of(new BaseMessage("user", "persisted"))).toCompletableFuture().join();
        source.offloadMessages("handle-2", List.of(new BaseMessage("assistant", "stored")));

        SessionModelContext restored = new SessionModelContext("ctx", "session", config);
        restored.loadState(Map.of("ctx", source.saveState()));

        assertThat(restored.getMessages(null, true)).extracting(BaseMessage::getContent).containsExactly("persisted");
        SessionModelContext.ReloaderTool tool = (SessionModelContext.ReloaderTool) restored.reloaderTool();
        assertThat(tool.reloadOriginalContextMessages("handle-2", "in_memory")).contains("stored");
    }

    @Test
    void getContextWindowRunsProcessorsAndKvCacheRelease() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setEnableKvCacheRelease(true);
        RecordingGetProcessor processor = new RecordingGetProcessor();
        RecordingKvCacheManager kvCacheManager = new RecordingKvCacheManager();
        SessionModelContext context = new SessionModelContext("ctx", "session", config, List.of(), List.of(processor),
                null, null, null, null, kvCacheManager, null);

        ContextWindow window = context.getContextWindow(List.of(), List.of(), null, null,
                Map.of("model", "m1")).toCompletableFuture().join();

        assertThat(window.getContextMessages()).extracting(BaseMessage::getContent).containsExactly("processed");
        assertThat(kvCacheManager.releasedWindows).hasSize(1);
        assertThat(kvCacheManager.models).containsExactly("m1");
    }

    /**
     * Compression processor test double.
     *
     * <p>Mirrors Python's {@code ContextProcessor} collaborator in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    private static final class RecordingCompressionProcessor implements SessionModelContext.ContextProcessorPort {
        @Override
        public String processorType() {
            return "compact";
        }

        @Override
        public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                                List<BaseMessage> messages,
                                                                                boolean force,
                                                                                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(
                    new RecordingEvent(),
                    messages,
                    null
            ));
        }
    }

    /**
     * Context-window processor test double.
     *
     * <p>Mirrors Python's {@code ContextProcessor} collaborator in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    private static final class RecordingGetProcessor implements SessionModelContext.ContextProcessorPort {
        @Override
        public String processorType() {
            return "get-window";
        }

        @Override
        public CompletionStage<Boolean> triggerGetContextWindow(SessionModelContext context, ContextWindow window,
                                                                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletionStage<SessionModelContext.ProcessResult> onGetContextWindow(SessionModelContext context,
                                                                                     ContextWindow window,
                                                                                     Map<String, Object> kwargs) {
            ContextWindow processed = new ContextWindow(List.of(), List.of(new BaseMessage("user", "processed")),
                    List.of(), null);
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(null, null, processed));
        }
    }

    /**
     * Processor event test double.
     *
     * <p>Mirrors Python's processor event object in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    private static final class RecordingEvent implements SessionModelContext.ContextProcessorEventPort {
        @Override
        public String compactSummary() {
            return "kept latest turn";
        }

        @Override
        public Object compressionUsage() {
            return Map.of("input_tokens", 9);
        }
    }

    /**
     * KV-cache release test double.
     *
     * <p>Mirrors Python's {@code KVCacheManager} collaborator in
     * {@code openjiuwen/core/context_engine/context/context.py}.</p>
     */
    private static final class RecordingKvCacheManager implements SessionModelContext.KvCacheManagerPort {
        private final List<ContextWindow> releasedWindows = new ArrayList<>();
        private final List<Object> models = new ArrayList<>();

        @Override
        public void release(ContextWindow contextWindow, Object model) {
            releasedWindows.add(contextWindow);
            models.add(model);
        }
    }
}
