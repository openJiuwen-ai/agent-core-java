/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.compressor;

import com.openjiuwen.core.context_engine.context.SessionMemorySupport;
import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.context_engine.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for full-compaction behavior.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/core/context_engine/processor/compressor/full_compact_processor.py}.</p>
 */
class FullCompactProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void triggerAddMessagesRequiresCompletedRoundAndTokenThreshold() {
        FullCompactProcessorConfig config = config();
        config.setTriggerTotalTokens(10);
        FullCompactProcessor processor = new FullCompactProcessor(config, null);
        SessionModelContext context = new SessionModelContext(
                "ctx",
                "session",
                new ContextEngineConfig(),
                List.of(new UserMessage("trigger message ".repeat(10))),
                List.of(processor),
                messages -> messages.stream().mapToInt(message -> message.getContentAsString().length()).sum());

        boolean triggered = processor.triggerAddMessages(context,
                List.of(new AssistantMessage("new assistant payload")), Map.of()).toCompletableFuture().join();

        assertThat(triggered).isTrue();
    }

    @Test
    void addMessagesBuildsFullCompactReplacementAndCarriesSummaryAndUsage() {
        FullCompactProcessorConfig config = config();
        config.setTriggerTotalTokens(1);
        config.setCompressionCallMaxTokens(2000);
        config.setMessagesToKeep(0);
        config.setSessionMemoryEnabled(false);
        AssistantMessage response = new AssistantMessage("<analysis>hidden</analysis><summary>Generated compact</summary>");
        response.setUsageMetadata(UsageMetadata.builder().inputTokens(2).outputTokens(3).totalTokens(5).build());
        FullCompactProcessor processor = new FullCompactProcessor(config, modelReturning(response));
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(), List.of(processor), messages -> 100);

        context.addMessages(List.of(new UserMessage("Please compact"), new AssistantMessage("Done")))
                .toCompletableFuture().join();

        List<BaseMessage> messages = context.getMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(0).getContentAsString()).startsWith(FullCompactProcessor.FULL_COMPACT_BOUNDARY_MARKER);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getContentAsString()).contains("Summary:\nGenerated compact");
        Map<String, Object> completed = context.compressionHistory().get(context.compressionHistory().size() - 1);
        assertThat(completed.get("compact_summary")).isEqualTo("Summary:\nGenerated compact");
        Map<?, ?> usage = (Map<?, ?>) completed.get("compression_usage");
        assertThat(((Number) usage.get("total_tokens")).longValue()).isEqualTo(5L);
    }

    @Test
    void sessionMemoryCandidateUsesCommittedNotesAndPreservesMessagesAfterAnchor() throws Exception {
        FullCompactProcessorConfig config = config();
        config.setTriggerTotalTokens(10000);
        FullCompactProcessor processor = new FullCompactProcessor(config, null);
        Path notes = tempDir.resolve("memory.md");
        Files.writeString(notes, "Persisted session memory");
        FakeSession session = new FakeSession("s1");
        SessionMemorySupport.updateSessionMemoryRuntime(session, Map.of(
                "memory_path", notes.toString(),
                "notes_upto_message_id", "msg-2",
                "is_extracting", true));
        SessionModelContext context = new SessionModelContext("ctx", "session", new ContextEngineConfig(),
                List.of(), List.of(processor), null);
        context.setSessionRef(session);
        List<BaseMessage> activeMessages = List.of(
                user("old-a", "msg-1"),
                assistant("old-b", "msg-2"),
                user("keep", "msg-3"));

        FullCompactProcessor.SessionMemoryReplacement result =
                processor.buildSessionMemoryMessages(context, List.of(), activeMessages, false);

        assertThat(result).isNotNull();
        assertThat(result.sessionMemoryMessage().getContentAsString()).contains("Persisted session memory");
        assertThat(result.messages().subList(2, result.messages().size())).containsExactly(activeMessages.get(2));
    }

    @Test
    void selectMessagesAfterSessionMemoryRejectsUnsafeAssistantToolAnchor() {
        FullCompactProcessor processor = new FullCompactProcessor(config(), null);
        AssistantMessage assistantWithTool = AssistantMessage.builder()
                .role("assistant")
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id("tc-1")
                        .name("read_file")
                        .type("function")
                        .arguments("{}")
                        .build()))
                .metadata(Map.of("context_message_id", "msg-2"))
                .build();
        List<BaseMessage> messages = List.of(
                user("u", "msg-1"),
                assistantWithTool,
                new ToolMessage("tool", "tc-1"),
                new AssistantMessage("answer"));

        List<BaseMessage> preserved = processor.selectMessagesAfterSessionMemory(messages,
                Map.of("notes_upto_message_id", "msg-2"), false);

        assertThat(preserved).isNull();
    }

    @Test
    void reinjectsSkillReadRoundOutsideKeptMessages() {
        FullCompactProcessorConfig config = config();
        config.setReinjectRecentSkills(1);
        FullCompactProcessor processor = new FullCompactProcessor(config, null);
        List<BaseMessage> source = List.of(
                new UserMessage("read skill"),
                AssistantMessage.builder()
                        .role("assistant")
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("tc-skill")
                                .name("read_file")
                                .type("function")
                                .arguments("{\"file_path\":\"/skills/demo/SKILL.md\"}")
                                .build()))
                        .build(),
                new ToolMessage("{\"content\":\"# Demo\"}", "tc-skill"),
                new AssistantMessage("loaded"),
                new UserMessage("keep me"));

        List<BaseMessage> reinjected = processor.buildReinjectedStateMessages(null, source,
                List.of(source.get(4)), new UserMessage("summary"), new SystemMessage("boundary"), List.of("skills"));

        assertThat(reinjected).hasSize(1);
        assertThat(reinjected.get(0).getContentAsString())
                .startsWith(FullCompactProcessor.FULL_COMPACT_STATE_MARKER)
                .contains("[SKILLS]")
                .contains("{\"content\":\"# Demo\"}");
    }

    @Test
    void utilityExtractToolResultHintRespectsConfiguredTools() {
        assertThat(CompressorUtils.extractToolResultHint("grep", "{\"count\":3}", List.of("read_file"))).isEmpty();
        assertThat(CompressorUtils.extractToolResultHint("read_file",
                "{\"file_path\":\"/tmp/a.txt\",\"line_count\":7}", List.of("read_file")))
                .isEqualTo("result_path=/tmp/a.txt lines=7");
    }

    private static FullCompactProcessorConfig config() {
        FullCompactProcessorConfig config = new FullCompactProcessorConfig();
        config.setCompressionCallMaxTokens(2000);
        return config;
    }

    private static BaseMessage user(String content, String contextMessageId) {
        UserMessage message = new UserMessage(content);
        message.setMetadata(Map.of("context_message_id", contextMessageId));
        return message;
    }

    private static BaseMessage assistant(String content, String contextMessageId) {
        AssistantMessage message = new AssistantMessage(content);
        message.setMetadata(Map.of("context_message_id", contextMessageId));
        return message;
    }

    private static Model modelReturning(AssistantMessage message) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(message));
    }

    private static final class FakeSession implements SessionMemorySupport.SessionStatePort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private FakeSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> update) {
            state.putAll(update);
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }
    }
}
