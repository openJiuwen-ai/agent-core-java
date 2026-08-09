/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.processor.compressor.DialogueCompressor;
import com.openjiuwen.core.context.processor.offloader.MessageOffloader;
import com.openjiuwen.core.context.processor.offloader.MessageOffloaderConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context-engine integration tests for KV cache release after processor changes.
 *
 * <p>Mirrors Python's {@code TestKVCacheManager} in
 * {@code tests/unit_tests/core/context_engine/test_kv_cache_manager.py}.</p>
 *
 * <p>Mirrors Python's context-engine InferenceAffinity integration tests in
 * {@code tests/unit_tests/core/context_engine/test_inference_affinity_kv_cache_release_with_processors.py}.</p>
 */
class ContextEngineKVCacheReleaseTest {

    @AfterEach
    void restoreProcessorRegistrations() {
        ContextEngine.registerProcessor("DialogueCompressor", DialogueCompressor.class);
        ContextEngine.registerProcessor("MessageOffloader", MessageOffloader.class);
    }

    @Test
    void kvCacheReleaseTriggeredAfterMessageOffloader(@TempDir Path workspace) {
        ContextEngine.registerProcessor("MessageOffloader", MessageOffloader.class);
        ContextEngineConfig engineConfig = kvEnabledConfig();
        ContextEngine engine = new ContextEngine(engineConfig, workspace::toString, null);
        MessageOffloaderConfig offloaderConfig = new MessageOffloaderConfig();
        offloaderConfig.setMessagesThreshold(3);
        offloaderConfig.setTokensThreshold(100000);
        offloaderConfig.setLargeMessageThreshold(50);
        offloaderConfig.setTrimSize(10);
        offloaderConfig.setOffloadMessageType(List.of("tool"));
        offloaderConfig.setKeepLastRound(false);
        RecordingReleaseModel model = new RecordingReleaseModel();

        ModelContext context = engine.createContext(
                "offload_ctx",
                null,
                List.of(new ContextEngine.ProcessorSpec("MessageOffloader", offloaderConfig)),
                List.of(
                        new UserMessage("u0"),
                        new ToolMessage("A".repeat(100), "t1"),
                        new AssistantMessage("a0")),
                null);

        context.getContextWindow(List.of(), List.of(), null, null, Map.of("model", model))
                .toCompletableFuture()
                .join();
        assertThat(model.calls).isEmpty();

        context.addMessages(List.of(
                new UserMessage("Follow up question"),
                new AssistantMessage("Follow up answer"))).toCompletableFuture().join();
        context.getContextWindow(List.of(), List.of(), null, null, Map.of("model", model))
                .toCompletableFuture()
                .join();

        assertThat(model.calls).hasSize(1);
        assertThat(model.calls.get(0).sessionId()).isEqualTo("default_session_id");
    }

    @Test
    void kvCacheReleaseTriggeredAfterDialogueCompression() {
        registerSyntheticDialogueCompressor();
        ContextEngineConfig engineConfig = kvEnabledConfig();
        ContextEngine engine = new ContextEngine(engineConfig);
        RecordingReleaseModel model = new RecordingReleaseModel();

        ModelContext context = engine.createContext(
                "dialogue_ctx",
                null,
                List.of(new ContextEngine.ProcessorSpec("DialogueCompressor", 6)),
                reactMessages("Tool result: important data xyz", "Based on the result, the answer is X."),
                null);

        context.getContextWindow(List.of(), List.of(), null, null, Map.of("model", model))
                .toCompletableFuture()
                .join();
        assertThat(model.calls).isEmpty();

        context.addMessages(reactMessages("Follow up tool result", "Follow up final answer."))
                .toCompletableFuture()
                .join();
        assertThat(context.getMessages(null, true))
                .extracting(BaseMessage::getContentAsString)
                .anyMatch(content -> content.contains(DialogueCompressor.DIALOGUE_MEMORY_BLOCK_MARKER));

        context.getContextWindow(List.of(), List.of(), null, null, Map.of("model", model))
                .toCompletableFuture()
                .join();

        assertThat(model.calls).hasSize(1);
    }

    @Test
    void kvCacheReleaseWithMultipleCompressionRounds() {
        registerSyntheticDialogueCompressor();
        ContextEngineConfig engineConfig = kvEnabledConfig();
        ContextEngine engine = new ContextEngine(engineConfig);
        RecordingReleaseModel model = new RecordingReleaseModel();
        List<BaseMessage> initialMessages = new ArrayList<>();
        initialMessages.addAll(reactMessages("Round 0 tool output data", "Round 0 final answer."));
        initialMessages.addAll(reactMessages("Round 1 tool output data", "Round 1 final answer."));

        ModelContext context = engine.createContext(
                "multi_round_ctx",
                null,
                List.of(new ContextEngine.ProcessorSpec("DialogueCompressor", 6)),
                initialMessages,
                null);

        context.getContextWindow(List.of(), List.of(), null, null, Map.of("model", model))
                .toCompletableFuture()
                .join();
        int initialReleaseCount = model.calls.size();

        context.addMessages(reactMessages("New tool output", "New round answer."))
                .toCompletableFuture()
                .join();
        context.getContextWindow(List.of(), List.of(), null, null, Map.of("model", model))
                .toCompletableFuture()
                .join();

        assertThat(model.calls.size()).isGreaterThan(initialReleaseCount);
    }

    @Test
    void kvCacheNoReleaseWhenDisabled() {
        registerSyntheticDialogueCompressor();
        ContextEngineConfig engineConfig = new ContextEngineConfig();
        engineConfig.setEnableKvCacheRelease(false);
        engineConfig.setDefaultWindowMessageNum(100);
        ContextEngine engine = new ContextEngine(engineConfig);
        RecordingReleaseModel model = new RecordingReleaseModel();

        ModelContext context = engine.createContext(
                "no_cache_ctx",
                null,
                List.of(new ContextEngine.ProcessorSpec("DialogueCompressor", 2)),
                reactMessages("Tool result data", "Answer based on tool."),
                null);

        context.getContextWindow(List.of(), List.of(), null, null, Map.of("model", model))
                .toCompletableFuture()
                .join();
        context.addMessages(new UserMessage("Follow up")).toCompletableFuture().join();
        context.getContextWindow(List.of(), List.of(), null, null, Map.of("model", model))
                .toCompletableFuture()
                .join();

        assertThat(model.calls).isEmpty();
    }

    @Test
    void kvCacheNoReleaseWhenNoProcessorModifiesMessages() {
        ContextEngineConfig engineConfig = kvEnabledConfig();
        ContextEngine engine = new ContextEngine(engineConfig);
        RecordingReleaseModel model = new RecordingReleaseModel();

        ModelContext context = engine.createContext(
                "no_release_ctx",
                null,
                List.of(),
                List.of(
                        new UserMessage("Hello"),
                        new AssistantMessage("Hi there")),
                null);

        context.getContextWindow(List.of(), List.of(), null, null, Map.of("model", model))
                .toCompletableFuture()
                .join();
        context.addMessages(new UserMessage("How are you?")).toCompletableFuture().join();
        ContextWindow windowAfterAppend = context.getContextWindow(List.of(), List.of(), null, null,
                        Map.of("model", model))
                .toCompletableFuture()
                .join();

        assertThat(model.calls).isEmpty();
        assertThat(windowAfterAppend.getMessages())
                .extracting(BaseMessage::getContentAsString)
                .containsExactly("Hello", "Hi there", "How are you?");
    }

    private static ContextEngineConfig kvEnabledConfig() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setEnableKvCacheRelease(true);
        config.setDefaultWindowMessageNum(100);
        return config;
    }

    private static void registerSyntheticDialogueCompressor() {
        ContextEngine.registerProcessor("DialogueCompressor", SyntheticDialogueCompressor::new);
    }

    private static List<BaseMessage> reactMessages(String toolContent, String finalAnswer) {
        return List.of(
                new UserMessage("Call the tool"),
                AssistantMessage.builder()
                        .role("assistant")
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("tc-" + Math.abs(toolContent.hashCode()))
                                .name("test-tool")
                                .type("function")
                                .arguments("{}")
                                .build()))
                        .build(),
                new ToolMessage(toolContent, "tc-" + Math.abs(toolContent.hashCode())),
                new AssistantMessage(finalAnswer));
    }

    private record ReleaseCall(String sessionId, List<BaseMessage> messages, Integer messagesReleasedIndex,
                               List<ToolInfo> tools, Integer toolsReleasedIndex) {
    }

    /**
     * Release-capable model test double.
     *
     * <p>Mirrors Python's {@code _FakeInferenceAffinityModel} in
     * {@code tests/unit_tests/core/context_engine/test_kv_cache_manager.py}.</p>
     */
    private static final class RecordingReleaseModel implements KVCacheManager.ReleaseCapableModel {
        private final List<ReleaseCall> calls = new ArrayList<>();

        @Override
        public CompletionStage<Boolean> release(String sessionId, List<BaseMessage> messages,
                                                Integer messagesReleasedIndex, List<ToolInfo> tools,
                                                Integer toolsReleasedIndex) {
            calls.add(new ReleaseCall(sessionId, messages, messagesReleasedIndex, tools, toolsReleasedIndex));
            return CompletableFuture.completedFuture(true);
        }
    }

    /**
     * Deterministic compression processor for context-engine KV release integration.
     *
     * <p>Mirrors the patched Python {@code DialogueCompressor} behavior in
     * {@code tests/unit_tests/core/context_engine/test_kv_cache_manager.py}.</p>
     */
    private static final class SyntheticDialogueCompressor implements SessionModelContext.ContextProcessorPort {
        private final int messagesThreshold;

        private SyntheticDialogueCompressor(Object config) {
            this.messagesThreshold = config instanceof Number number ? number.intValue() : 6;
        }

        @Override
        public String processorType() {
            return "DialogueCompressor";
        }

        @Override
        public CompletionStage<Boolean> triggerAddMessages(SessionModelContext context, List<BaseMessage> messages,
                                                           Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(context.length() + (messages == null ? 0 : messages.size())
                    > messagesThreshold);
        }

        @Override
        public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                                List<BaseMessage> messages,
                                                                                boolean force,
                                                                                Map<String, Object> kwargs) {
            List<BaseMessage> updated = new ArrayList<>();
            updated.add(new UserMessage(DialogueCompressor.DIALOGUE_MEMORY_BLOCK_MARKER
                    + "\nSummary:\nCompressed round content."));
            updated.addAll(messages == null ? List.of() : messages);
            context.setMessages(updated, true);
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(
                    new CompressionEvent(),
                    List.of(),
                    null));
        }
    }

    private static final class CompressionEvent implements SessionModelContext.ContextProcessorEventPort {
        @Override
        public List<Integer> messagesToModify() {
            return List.of(0);
        }

        @Override
        public String compactSummary() {
            return DialogueCompressor.DIALOGUE_MEMORY_BLOCK_MARKER;
        }
    }
}
