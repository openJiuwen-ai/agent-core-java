/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionMemoryConfig;
import com.openjiuwen.core.context.context.SessionMemoryManager;
import com.openjiuwen.core.context.context.SessionMemorySupport;
import com.openjiuwen.core.context.context.SessionMemoryUpdateAgent;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Missing supplemental parity tests for full compact processor edge cases.
 *
 * <p>Mirrors Python's {@code TestFullCompactProcessor} in
 * {@code tests/unit_tests/core/context_engine/test_full_compact_processor.py}.</p>
 */
class FullCompactProcessorMissingTest {

    @TempDir
    private Path tempDir;

    @Test
    void manualCompressContextReturnsFullCompactStatePair() {
        FullCompactProcessorConfig config = config();
        config.setTriggerTotalTokens(1);
        config.setMessagesToKeep(0);
        config.setSessionMemoryEnabled(false);
        FullCompactProcessor processor = new FullCompactProcessor(
                config,
                modelReturning(new AssistantMessage("<analysis>hidden</analysis><summary>Generated compact summary</summary>")));
        SessionModelContext context = contextWith(processor,
                List.of(new UserMessage("Please implement compact summary state."),
                        new AssistantMessage("I will wire the generated summary through.")),
                null,
                messages -> 100);

        Map<String, Object> result = compressWithState(context);

        assertThat(result).containsEntry("result", SessionModelContext.ACTIVE_COMPRESSION_RESULT_COMPRESSED);
        assertThat(result).containsEntry("compact_summary", "Summary:\nGenerated compact summary");
        List<Map<String, Object>> history = context.compressionHistory();
        assertThat(history).hasSize(2);
        assertThat(history.get(0)).containsEntry("status", "started")
                .containsEntry("phase", "active_compress")
                .containsEntry("processor", "FullCompactProcessor");
        assertThat(history.get(0).get("compact_summary")).isEqualTo("");
        assertThat(history.get(1)).containsEntry("status", "completed")
                .containsEntry("phase", "active_compress")
                .containsEntry("compact_summary", "Summary:\nGenerated compact summary");
    }

    @Test
    void sessionMemoryReplacementManualCompressReturnsSummaryState() throws Exception {
        Path notes = tempDir.resolve("memory.md");
        Files.writeString(notes, "Persisted session memory");
        FakeSession session = new FakeSession("session-memory-summary-session");
        SessionMemorySupport.updateSessionMemoryRuntime(session, runtime(
                "is_extracting", false,
                "notes_upto_message_id", "msg-2",
                "memory_path", notes.toString()));
        FullCompactProcessorConfig config = config();
        config.setTriggerTotalTokens(1000);
        config.setMessagesToKeep(0);
        FullCompactProcessor processor = new FullCompactProcessor(config, null);
        SessionModelContext context = contextWith(processor,
                List.of(user("old-a", "msg-1"), assistant("old-b", "msg-2"), user("keep", "msg-3")),
                session,
                messages -> 10);

        Map<String, Object> result = compressWithState(context);

        assertThat(result).containsEntry("result", SessionModelContext.ACTIVE_COMPRESSION_RESULT_COMPRESSED);
        assertThat((String) result.get("compact_summary")).contains("Persisted session memory");
        Map<String, Object> completed = context.compressionHistory().get(1);
        assertThat(completed).containsEntry("phase", "active_compress");
        assertThat((String) completed.get("compact_summary")).contains("Persisted session memory");
        assertThat(context.getMessages().get(0).getContentAsString())
                .startsWith(FullCompactProcessor.SESSION_MEMORY_BOUNDARY_MARKER);
    }

    @Test
    void replacementPrefersSessionMemoryCandidateAfterPriorFullCompact() throws Exception {
        Path notes = tempDir.resolve("prior-memory.md");
        Files.writeString(notes, "notes after prior compact");
        FakeSession session = new FakeSession("s-prior");
        SessionMemorySupport.updateSessionMemoryRuntime(session, runtime(
                "memory_path", notes.toString(),
                "notes_upto_message_id", "msg-4"));
        FullCompactProcessorConfig config = config();
        config.setTriggerTotalTokens(1000);
        FullCompactProcessor processor = new FullCompactProcessor(config, modelReturning(new AssistantMessage("unused")));
        SessionModelContext context = contextWith(processor, List.of(), session, messages -> 10);
        List<BaseMessage> allMessages = List.of(
                new SystemMessage(FullCompactProcessor.FULL_COMPACT_BOUNDARY_MARKER + "\nConversation compacted"),
                user("recent user", "msg-3"),
                assistant("recent assistant", "msg-4"));

        FullCompactProcessor.ReplacementResult result = processor.buildReplacementMessages(context, allMessages);

        assertThat(result).isNotNull();
        assertThat(result.sessionMemoryMessage()).isNotNull();
        assertThat(result.sessionMemoryMessage().getContentAsString()).contains("notes after prior compact");
        assertThat(result.messages().get(0).getContentAsString())
                .startsWith(FullCompactProcessor.SESSION_MEMORY_BOUNDARY_MARKER);
    }

    @Test
    void buildSessionMemoryMessagesReturnsNullWhenCommittedNotesUnavailable() throws Exception {
        Path notes = tempDir.resolve("blank-memory.md");
        Files.writeString(notes, "");
        FakeSession session = new FakeSession("s-blank");
        SessionMemorySupport.updateSessionMemoryRuntime(session, runtime(
                "is_extracting", true,
                "notes_upto_message_id", "msg-2",
                "memory_path", notes.toString()));
        FullCompactProcessor processor = new FullCompactProcessor(config(), null);
        SessionModelContext context = contextWith(processor, List.of(), session, null);

        FullCompactProcessor.SessionMemoryReplacement result = processor.buildSessionMemoryMessages(
                context,
                List.of(),
                List.of(user("keep-1", "msg-3"), assistant("keep-2", "msg-4")),
                false);

        assertThat(result).isNull();
    }

    @Test
    void buildSessionMemoryMessagesReadsRuntimeUpdatedBySessionMemoryManager() {
        FullCompactProcessor processor = new FullCompactProcessor(config(), null);
        SessionMemoryConfig memoryConfig = new SessionMemoryConfig();
        memoryConfig.setTriggerTokens(1);
        memoryConfig.setUpdateMode(SessionMemoryConfig.UpdateMode.DIRECT_REPLACE);
        SessionMemoryManager manager = new SessionMemoryManager(memoryConfig, directUpdateAgent(memoryConfig), Runnable::run);
        FakeSession session = new FakeSession("test-session");
        List<BaseMessage> activeMessages = List.of(
                user("old-a", "msg-1"),
                assistant("old-b", "msg-2"),
                user("keep-1", "msg-3"),
                assistant("keep-2", "msg-4"));
        FakeModelContext modelContext = new FakeModelContext("ctx", "test-session", activeMessages, 10);

        manager.maybeScheduleUpdate(new FakeCallback(session, modelContext, List.of()), () -> tempDir)
                .toCompletableFuture()
                .join();
        FullCompactProcessor.SessionMemoryReplacement result = processor.buildSessionMemoryMessages(
                contextWith(processor, List.of(), session, null),
                List.of(),
                activeMessages,
                false);

        assertThat(result).isNotNull();
        assertThat(result.sessionMemoryMessage().getContentAsString()).startsWith(
                FullCompactProcessor.SESSION_MEMORY_SUMMARY_INTRO);
        assertThat(result.sessionMemoryMessage().getContentAsString()).contains("updated notes");
        assertThat(result.messages()).hasSize(2);
        assertThat(SessionMemorySupport.getSessionMemoryRuntime(session))
                .containsEntry("notes_upto_message_id", "msg-4")
                .containsEntry("last_summarized_message_count", 4);
    }

    @Test
    void selectMessagesAfterSessionMemoryPrefersContextMessageId() {
        FullCompactProcessor processor = new FullCompactProcessor(config(), null);
        List<BaseMessage> activeMessages = List.of(user("keep-1", "msg-3"), assistant("keep-2", "msg-4"));

        List<BaseMessage> preserved = processor.selectMessagesAfterSessionMemory(activeMessages,
                runtime("notes_upto_message_id", "msg-2", "last_summarized_message_count", 1),
                false);

        assertThat(preserved).isNull();
    }

    @Test
    void selectMessagesAfterSessionMemoryIgnoresPrefixAnchorAfterBoundary() {
        FullCompactProcessor processor = new FullCompactProcessor(config(), null);
        List<BaseMessage> activeMessages = List.of(user("recent-u", "msg-3"), assistant("recent-a", "msg-4"));

        List<BaseMessage> preserved = processor.selectMessagesAfterSessionMemory(activeMessages,
                runtime("notes_upto_message_id", "msg-2", "last_summarized_message_count", 2),
                true);

        assertThat(preserved).isNull();
    }

    @Test
    void selectMessagesAfterSessionMemoryAcceptsSyntheticSummaryAnchor() {
        FullCompactProcessor processor = new FullCompactProcessor(config(), null);
        BaseMessage summary = user(FullCompactProcessor.SESSION_MEMORY_SUMMARY_INTRO + "\n\nnotes body", "msg-1");
        BaseMessage recentUser = user("recent-u", "msg-2");
        BaseMessage recentAssistant = assistant("recent-a", "msg-3");
        List<BaseMessage> activeMessages = List.of(summary, recentUser, recentAssistant);

        List<BaseMessage> preserved = processor.selectMessagesAfterSessionMemory(activeMessages,
                runtime("notes_upto_message_id", "msg-1"),
                true);

        assertThat(preserved).containsExactly(recentUser, recentAssistant);
    }

    @Test
    void selectMessagesAfterSessionMemoryRequiresContextIdAnchorBeforeFirstBoundary() {
        FullCompactProcessor processor = new FullCompactProcessor(config(), null);
        List<BaseMessage> activeMessages = List.of(
                new UserMessage("u1"),
                new AssistantMessage("a1"),
                new UserMessage("u2"),
                new AssistantMessage("a2"));

        List<BaseMessage> preserved = processor.selectMessagesAfterSessionMemory(activeMessages,
                runtime("last_summarized_message_count", 2),
                false);

        assertThat(preserved).isNull();
    }

    @Test
    void groupMessagesByApiRoundSplitsUserAndFollowingAssistantToolMessages() {
        List<BaseMessage> messages = List.of(
                new UserMessage("u1"),
                assistantWithTool("tc-1", "read_file", "{}"),
                new ToolMessage("tool-1", "tc-1"),
                new AssistantMessage("a1"),
                new UserMessage("u2"),
                new AssistantMessage("a2"));

        List<List<BaseMessage>> groups = CompressorUtils.groupCompletedApiRoundMessages(messages);

        assertThat(groups).hasSize(3);
        assertThat(groups.get(0).stream().map(BaseMessage::getContentAsString).toList())
                .containsExactly("u1", "", "tool-1");
        assertThat(groups.get(1).stream().map(BaseMessage::getContentAsString).toList()).containsExactly("a1");
        assertThat(groups.get(2).stream().map(BaseMessage::getContentAsString).toList()).containsExactly("u2", "a2");
    }

    @Test
    void sessionMemoryManagerSelectUnsummarizedMessagesPrefersMessageId() {
        BaseMessage firstSame = assistant("same", "msg-2");
        BaseMessage secondSame = assistant("same", "msg-3");
        List<BaseMessage> messages = List.of(user("a", "msg-1"), firstSame, secondSame);

        List<BaseMessage> selected = SessionMemoryManager.selectUnsummarizedMessages(messages, "msg-2");

        assertThat(selected).containsExactly(secondSame);
    }

    @Test
    void findMessageIndexByContextMessageIdUsesStableMessageId() {
        BaseMessage before = user("before", "msg-1");
        ToolMessage tool = new ToolMessage("tool-old", "tc-1");
        tool.setMetadata(Map.of("context_message_id", "msg-2"));
        List<BaseMessage> messages = List.of(before, tool);

        assertThat(SessionMemorySupport.findMessageIndexByContextMessageId(messages, "msg-2")).isEqualTo(1);
        tool.setContent("tool-new");
        assertThat(SessionMemorySupport.findMessageIndexByContextMessageId(messages, "msg-2")).isEqualTo(1);
    }

    @Test
    void sessionMemoryManagerTruncatesToCompletedApiRound() {
        List<BaseMessage> messages = List.of(
                new UserMessage("u1"),
                new AssistantMessage("a1"),
                new UserMessage("u2"),
                assistantWithTool("tc-1", "read_file", "{}"),
                new ToolMessage("tool", "tc-1"));

        List<BaseMessage> truncated = SessionMemoryManager.truncateMessagesToCompletedApiRound(messages);

        assertThat(truncated).containsExactlyElementsOf(messages);
    }

    @Test
    void groupCompletedApiRoundsEndsToolRoundAtToolMessage() {
        List<BaseMessage> messages = List.of(
                new UserMessage("u1"),
                assistantWithTool("tc-1", "read_file", "{}"),
                new ToolMessage("tool-1", "tc-1"),
                new AssistantMessage("a1"),
                new UserMessage("u2"),
                new AssistantMessage("a2"));

        List<SessionMemorySupport.ApiRound> rounds = SessionMemorySupport.groupCompletedApiRounds(messages);

        assertThat(rounds).containsExactly(
                new SessionMemorySupport.ApiRound(0, 3),
                new SessionMemorySupport.ApiRound(3, 4),
                new SessionMemorySupport.ApiRound(4, 6));
    }

    @Test
    void fullCompactInvalidatesSessionMemoryAnchor() {
        FakeSession session = new FakeSession("s-anchor");
        SessionMemorySupport.updateSessionMemoryRuntime(session, runtime(
                "last_summarized_message_count", 9,
                "notes_upto_message_id", "anchor-id"));
        FullCompactProcessorConfig config = config();
        config.setTriggerTotalTokens(1);
        config.setSessionMemoryEnabled(false);
        config.setMessagesToKeep(0);
        FullCompactProcessor processor = new FullCompactProcessor(
                config,
                modelReturning(new AssistantMessage("<summary>full compact</summary>")));
        SessionModelContext context = contextWith(processor,
                List.of(new UserMessage("old"), new AssistantMessage("answer")),
                session,
                messages -> 100);

        compressWithState(context);

        Map<String, Object> runtime = SessionMemorySupport.getSessionMemoryRuntime(session);
        assertThat(runtime).containsEntry("last_summarized_message_count", 0);
        assertThat(runtime.get("notes_upto_message_id")).isNull();
    }

    @Test
    void getRuntimeStateKeepsMessageIdAnchor() {
        FakeSession session = new FakeSession("s1");
        SessionMemorySupport.updateSessionMemoryRuntime(session, runtime(
                "session_id", "s1",
                "last_summarized_message_count", 99,
                "notes_upto_message_id", "anchor-id"));

        Map<String, Object> runtime = SessionMemoryManager.getRuntimeState(session);

        assertThat(runtime).containsEntry("last_summarized_message_count", 99)
                .containsEntry("notes_upto_message_id", "anchor-id");
    }

    private static FullCompactProcessorConfig config() {
        FullCompactProcessorConfig config = new FullCompactProcessorConfig();
        config.setCompressionCallMaxTokens(2000);
        return config;
    }

    private static SessionModelContext contextWith(FullCompactProcessor processor, List<BaseMessage> messages,
                                                   Object session, ModelContext.TokenCounterPort tokenCounter) {
        return new SessionModelContext(
                "ctx",
                "session",
                new ContextEngineConfig(),
                messages,
                List.of(processor),
                tokenCounter,
                session,
                null,
                null,
                null,
                null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> compressWithState(SessionModelContext context) {
        Object result = context.compressContext(List.of("FullCompactProcessor"), Map.of("return_state", true))
                .toCompletableFuture()
                .join();
        assertThat(result).isInstanceOf(Map.class);
        return (Map<String, Object>) result;
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

    private static AssistantMessage assistantWithTool(String toolCallId, String name, String arguments) {
        return AssistantMessage.builder()
                .role("assistant")
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id(toolCallId)
                        .name(name)
                        .type("function")
                        .arguments(arguments)
                        .build()))
                .build();
    }

    private static Model modelReturning(AssistantMessage message) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(message));
    }

    private static SessionMemoryUpdateAgent directUpdateAgent(SessionMemoryConfig config) {
        SessionMemoryUpdateAgent updateAgent = new SessionMemoryUpdateAgent(config);
        updateAgent.setDirectModel(new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("updated notes"))));
        return updateAgent;
    }

    private static Map<String, Object> runtime(Object... keyValues) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            runtime.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return runtime;
    }

    /**
     * Mutable fake session for session-memory runtime assertions.
     *
     * <p>Mirrors Python's dynamic session test doubles in
     * {@code tests/unit_tests/core/context_engine/test_full_compact_processor.py}.</p>
     */
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

    /**
     * Minimal model context fake used by the session-memory manager.
     *
     * <p>Mirrors Python's mocked model context in
     * {@code tests/unit_tests/core/context_engine/test_full_compact_processor.py}.</p>
     */
    private static final class FakeModelContext implements ModelContext {
        private final String contextId;
        private final String sessionId;
        private final List<BaseMessage> messages;
        private final int tokenCount;

        private FakeModelContext(String contextId, String sessionId, List<BaseMessage> messages, int tokenCount) {
            this.contextId = contextId;
            this.sessionId = sessionId;
            this.messages = new ArrayList<>(messages);
            this.tokenCount = tokenCount;
        }

        @Override
        public int length() {
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
            List<BaseMessage> result = getMessages(size, withHistory);
            messages.removeAll(result);
            return result;
        }

        @Override
        public CompletionStage<Void> clearMessages(boolean withHistory) {
            messages.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(BaseMessage message) {
            messages.add(message);
            return CompletableFuture.completedFuture(List.of(message));
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(List<BaseMessage> messages) {
            this.messages.addAll(messages);
            return CompletableFuture.completedFuture(messages);
        }

        @Override
        public CompletionStage<ContextWindow> getContextWindow(List<BaseMessage> systemMessages,
                                                               List<ToolInfo> tools,
                                                               Integer windowSize,
                                                               Integer dialogueRound,
                                                               Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(new ContextWindow(systemMessages, messages, tools, null));
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
        public TokenCounterPort tokenCounter() {
            return ignored -> tokenCount;
        }

        @Override
        public ToolPort reloaderTool() {
            return () -> "reload_original_context_messages";
        }
    }

    /**
     * Callback fake used by {@link SessionMemoryManager}.
     *
     * <p>Mirrors Python's {@code AgentCallbackContext} fixture in
     * {@code tests/unit_tests/core/context_engine/test_full_compact_processor.py}.</p>
     */
    private record FakeCallback(FakeSession session, FakeModelContext context, List<BaseMessage> inputMessages)
            implements SessionMemoryManager.AgentCallbackContextPort {
        @Override
        public SessionMemoryManager.InputsPort inputs() {
            return () -> inputMessages;
        }
    }
}
