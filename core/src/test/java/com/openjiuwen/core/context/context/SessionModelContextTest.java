/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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

        // G.TYP.13: guard the cast with an instanceof check before downcasting
        Object rawTool = context.reloaderTool();
        SessionModelContext.ReloaderTool tool =
                rawTool instanceof SessionModelContext.ReloaderTool t ? t : null;
        String reloaded = tool.reloadOriginalContextMessages("handle-1", "in_memory");

        assertThat(tool.name()).isEqualTo("reload_original_context_messages");
        assertThat(reloaded).contains("reload messages with handle=handle-1");
        assertThat(reloaded).contains("offloaded");
    }

    @Test
    void reloaderToolPaginatesWithOffsetAndLimit() {
        // T18 / AC-4-11: offset/limit slice the payload; message numbering
        // continues from offset so the model can keep its place.
        ContextEngineConfig config = new ContextEngineConfig();
        SessionModelContext context = new SessionModelContext("ctx", "session", config);
        context.offloadMessages("handle-page", List.of(
                new BaseMessage("user", "m1"),
                new BaseMessage("assistant", "m2"),
                new BaseMessage("user", "m3"),
                new BaseMessage("assistant", "m4")));

        // G.TYP.13: guard the cast with an instanceof check before downcasting
        Object rawTool = context.reloaderTool();
        SessionModelContext.ReloaderTool tool =
                rawTool instanceof SessionModelContext.ReloaderTool t ? t : null;
        String page = tool.reloadOriginalContextMessages("handle-page", "in_memory", 2, 2);

        assertThat(page).contains("offset=2");
        assertThat(page).contains("m3").contains("m4");
        assertThat(page).doesNotContain("m1").doesNotContain("m2");
        assertThat(page).contains("message 3:").contains("message 4:");
        assertThat(page).contains(SessionModelContext.OFFLOAD_DONE_METADATA_KEY + "=true");
    }

    @Test
    void reloaderToolPaginationViaInvokeArguments() {
        // invoke() path: offset/limit may arrive as strings from model JSON.
        ContextEngineConfig config = new ContextEngineConfig();
        SessionModelContext context = new SessionModelContext("ctx", "session", config);
        context.offloadMessages("handle-invoke", List.of(
                new BaseMessage("user", "a1"),
                new BaseMessage("assistant", "a2"),
                new BaseMessage("user", "a3")));

        // G.TYP.13: guard the cast with an instanceof check before downcasting
        Object rawTool = context.reloaderTool();
        SessionModelContext.ReloaderTool tool =
                rawTool instanceof SessionModelContext.ReloaderTool t ? t : null;
        Object result = tool.invoke(Map.of(
                "offload_handle", "handle-invoke",
                "offload_type", "in_memory",
                "offset", "1",
                "limit", 1));

        assertThat(result).isInstanceOf(String.class);
        // G.TYP.13: guard the cast with an instanceof check before downcasting
        String page = result instanceof String s ? s : null;
        // Content asserts are quoted: bare substrings like "a1" can collide
        // with the hex context-message ids rendered in the dump.
        assertThat(page).contains("\"a2\"");
        assertThat(page).doesNotContain("\"a1\"").doesNotContain("\"a3\"");
    }

    @Test
    void reloaderToolOffsetBeyondPayloadFallsBackToTrailingWindow() {
        // 空载荷保护：offset 越界时回退到尾部窗口，模型不会拿到空结果。
        ContextEngineConfig config = new ContextEngineConfig();
        SessionModelContext context = new SessionModelContext("ctx", "session", config);
        context.offloadMessages("handle-far", List.of(
                new BaseMessage("user", "b1"),
                new BaseMessage("assistant", "b2")));

        // G.TYP.13: guard the cast with an instanceof check before downcasting
        Object rawTool = context.reloaderTool();
        SessionModelContext.ReloaderTool tool =
                rawTool instanceof SessionModelContext.ReloaderTool t ? t : null;
        String page = tool.reloadOriginalContextMessages("handle-far", "in_memory", 50, 2);

        assertThat(page).contains("b1").contains("b2");
    }

    @Test
    void reloaderToolZeroOffsetZeroLimitKeepsFullPayload() {
        ContextEngineConfig config = new ContextEngineConfig();
        SessionModelContext context = new SessionModelContext("ctx", "session", config);
        context.offloadMessages("handle-full", List.of(
                new BaseMessage("user", "c1"),
                new BaseMessage("assistant", "c2"),
                new BaseMessage("user", "c3")));

        // G.TYP.13: guard the cast with an instanceof check before downcasting
        Object rawTool = context.reloaderTool();
        SessionModelContext.ReloaderTool tool =
                rawTool instanceof SessionModelContext.ReloaderTool t ? t : null;
        String full = tool.reloadOriginalContextMessages("handle-full", "in_memory", 0, 0);

        assertThat(full).contains("c1").contains("c2").contains("c3");
        assertThat(full).doesNotContain("offset=");
    }

    @Test
    void reloaderToolLimitBeyondRemainingReturnsAllRest() {
        // TC_011-UT: limit 大于剩余消息数时返回从 offset 起的全部剩余。
        ContextEngineConfig config = new ContextEngineConfig();
        SessionModelContext context = new SessionModelContext("ctx", "session", config);
        context.offloadMessages("handle-rest", List.of(
                new BaseMessage("user", "r1"),
                new BaseMessage("assistant", "r2"),
                new BaseMessage("user", "r3")));

        // G.TYP.13: guard the cast with an instanceof check before downcasting
        Object rawTool = context.reloaderTool();
        SessionModelContext.ReloaderTool tool =
                rawTool instanceof SessionModelContext.ReloaderTool t ? t : null;
        String page = tool.reloadOriginalContextMessages("handle-rest", "in_memory", 1, 99);

        assertThat(page).contains("offset=1");
        assertThat(page).contains("r2").contains("r3");
        assertThat(page).doesNotContain("r1");
    }

    @Test
    void reloaderToolRendersStructuredErrorsWithoutStacksOrPaths() {
        // TC_059: FORBIDDEN/GONE/CORRUPT 经 reloader 渲染为 "code: handle=..."，
        // 无堆栈、无绝对路径（共享约定 9 反向断言）。
        ContextEngineConfig config = new ContextEngineConfig();
        SessionModelContext context = new SessionModelContext("ctx", "session", config);
        context.bindOffloadService(new ForbiddenService(), () -> null);

        // G.TYP.13: guard the cast with an instanceof check before downcasting
        Object rawTool = context.reloaderTool();
        SessionModelContext.ReloaderTool tool =
                rawTool instanceof SessionModelContext.ReloaderTool t ? t : null;
        String text = tool.reloadOriginalContextMessages("gone-handle", "in_memory");

        assertThat(text).isEqualTo("OFFLOAD_GONE: handle=gone-handle");
        assertThat(text).doesNotContain("at com.openjiuwen");
        assertThat(text).doesNotContain(java.io.File.separator);
    }

    /** Service raising three distinct load errors to drive the error-render path. */
    private static final class ForbiddenService implements com.openjiuwen.core.context.offload.api.OffloadService {
        @Override
        public java.util.Optional<com.openjiuwen.core.context.offload.model.OffloadRef> save(
                com.openjiuwen.core.context.offload.model.OffloadRequest request) {
            return java.util.Optional.empty();
        }

        @Override
        public com.openjiuwen.core.context.offload.model.OffloadPayload load(
                com.openjiuwen.core.context.offload.model.OffloadNamespace ns, String handle, String type) {
            throw new com.openjiuwen.core.context.offload.api.OffloadException(
                    com.openjiuwen.core.context.offload.api.OffloadErrorCode.OFFLOAD_GONE,
                    "offload backing entry vanished: " + handle);
        }

        @Override
        public boolean delete(com.openjiuwen.core.context.offload.model.OffloadNamespace ns, String handle) {
            return false;
        }

        @Override
        public void deleteNamespace(com.openjiuwen.core.context.offload.model.OffloadNamespace ns) {
        }

        @Override
        public void pin(String handle, java.time.Duration duration) {
            throw new com.openjiuwen.core.context.offload.api.OffloadException(
                    com.openjiuwen.core.context.offload.api.OffloadErrorCode.OFFLOAD_MISSING, "gone");
        }

        @Override
        public void unpin(String handle) {
        }

        @Override
        public void extendTtl(String handle, java.time.Duration delta) {
            throw new com.openjiuwen.core.context.offload.api.OffloadException(
                    com.openjiuwen.core.context.offload.api.OffloadErrorCode.OFFLOAD_MISSING, "gone");
        }

        @Override
        public void capOrphanTtl(String handle) {
        }
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
        // G.TYP.13: guard the cast with an instanceof check before downcasting
        Object rawTool = restored.reloaderTool();
        SessionModelContext.ReloaderTool tool =
                rawTool instanceof SessionModelContext.ReloaderTool t ? t : null;
        assertThat(tool.reloadOriginalContextMessages("handle-2", "in_memory")).contains("stored");
    }

    @Test
    void saveAndLoadStatePreservesUserMultimodalListContent() {
        UserMessage multimodal = new UserMessage();
        List<Map<String, Object>> parts = new ArrayList<>();
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", "look at this");
        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", new LinkedHashMap<>(Map.of("url", "data:image/png;base64,aaa")));
        parts.add(textPart);
        parts.add(imagePart);
        multimodal.setContent(parts);
        ContextEngineConfig config = new ContextEngineConfig();
        SessionModelContext source = new SessionModelContext("ctx", "session", config);
        source.addMessages(List.of(multimodal)).toCompletableFuture().join();

        SessionModelContext restored = new SessionModelContext("ctx", "session", config);
        restored.loadState(Map.of("ctx", source.saveState()));

        List<BaseMessage> messages = restored.getMessages(null, true);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isInstanceOf(List.class);
        List<?> restoredParts = (List<?>) messages.get(0).getContent();
        assertThat(restoredParts).hasSize(2);
        assertThat(asMap(restoredParts.get(0)))
                .containsEntry("type", "text")
                .containsEntry("text", "look at this");
        assertThat(asMap(restoredParts.get(1))).containsEntry("type", "image_url");
    }

    @Test
    void saveAndLoadStatePreservesStructuredMapContent() {
        ContextEngineConfig config = new ContextEngineConfig();
        SessionModelContext source = new SessionModelContext("ctx", "session", config);
        UserMessage structured = new UserMessage();
        Map<String, Object> mapContent = new LinkedHashMap<>();
        mapContent.put("type", "text");
        mapContent.put("text", "structured map");
        structured.setContent(mapContent);
        source.addMessages(List.of(structured)).toCompletableFuture().join();

        SessionModelContext restored = new SessionModelContext("ctx", "session", config);
        restored.loadState(Map.of("ctx", source.saveState()));

        List<BaseMessage> messages = restored.getMessages(null, true);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isInstanceOf(Map.class);
        assertThat(asMap(messages.get(0).getContent()))
                .containsEntry("type", "text")
                .containsEntry("text", "structured map");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @Test
    void getContextWindowRunsProcessors() {
        RecordingGetProcessor processor = new RecordingGetProcessor();
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(), List.of(processor), null, null, null, null, null);

        ContextWindow window = context.getContextWindow(List.of(), List.of(), null, null,
                Map.of("model", "m1")).toCompletableFuture().join();

        assertThat(window.getContextMessages()).extracting(BaseMessage::getContent).containsExactly("processed");
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

}
