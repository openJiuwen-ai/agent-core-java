/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.context;

import com.openjiuwen.core.context_engine.ContextStats;
import com.openjiuwen.core.context_engine.ContextWindow;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
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
 * Focused parity tests for session-memory manager behavior.
 *
 * <p>Mirrors Python's {@code session_memory_manager.py} in
 * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
 */
class SessionMemoryManagerTest {

    @TempDir
    private Path tempDir;

    @Test
    void runtimeStateAndApiRoundHelpersMirrorPythonBoundaries() {
        FakeSession session = new FakeSession("session-1");

        assertThat(SessionMemorySupport.getSessionMemoryRuntime(session))
                .containsEntry("memory_path", "")
                .containsEntry("initialized", false)
                .containsEntry("is_extracting", false);

        SessionMemorySupport.updateSessionMemoryRuntime(session, Map.of(
                "memory_path", "notes.md",
                "tokens_at_last_update", 10,
                "notes_upto_message_id", "m1"
        ));
        SessionMemorySupport.invalidateSessionMemoryAnchor(session);

        Map<String, Object> runtime = SessionMemorySupport.getSessionMemoryRuntime(session);
        assertThat(runtime).containsEntry("memory_path", "notes.md")
                .containsEntry("tokens_at_last_update", 0)
                .containsEntry("last_summarized_message_count", 0);
        assertThat(runtime.get("notes_upto_message_id")).isNull();

        AssistantMessage assistantWithTool = AssistantMessage.builder()
                .role("assistant")
                .content("call")
                .toolCalls(List.of(ToolCall.builder().id("call-1").name("edit_file").arguments("{}").build()))
                .build();
        List<BaseMessage> messages = List.of(
                new UserMessage("q1"),
                assistantWithTool,
                new ToolMessage("done", "call-1"),
                new UserMessage("q2"),
                new AssistantMessage("a2"),
                new UserMessage("q3"),
                AssistantMessage.builder()
                        .role("assistant")
                        .content("unfinished")
                        .toolCalls(List.of(ToolCall.builder().id("call-2").name("edit_file").arguments("{}").build()))
                        .build()
        );

        assertThat(SessionMemorySupport.groupCompletedApiRounds(messages)).containsExactly(
                new SessionMemorySupport.ApiRound(0, 3),
                new SessionMemorySupport.ApiRound(3, 5)
        );
        assertThat(SessionMemoryManager.findLastCompletedApiRoundEnd(messages)).isEqualTo(5);
        assertThat(SessionMemoryManager.truncateMessagesToCompletedApiRound(messages)).hasSize(5);
    }

    @Test
    void updateAgentClosesStreamThenCommitsAfterAgentInvoke() throws Exception {
        SessionMemoryConfig config = new SessionMemoryConfig();
        RecordingAgent agent = new RecordingAgent();
        RecordingUpdateSession session = new RecordingUpdateSession(agent.events);
        SessionMemoryUpdateAgent updateAgent = new SessionMemoryUpdateAgent(
                config,
                (notesPath, cfg, namespace, inheritedPrompt) -> agent,
                (sessionId, createdAgent) -> session
        );
        updateAgent.setInheritedSystemPrompt("system inherited");
        Path notesPath = tempDir.resolve("workspace/context/s1/session_memory/session_context.md");
        Files.createDirectories(notesPath.getParent());
        Files.writeString(notesPath, "current");

        updateAgent.invoke(List.of(new UserMessage("hello")), notesPath, "current").toCompletableFuture().join();

        assertThat(agent.events).containsExactly(
                "preRun",
                "initContext",
                "addMessages:1",
                "invoke",
                "closeStream",
                "commit"
        );
        assertThat(agent.inputs.get("query").toString()).contains(String.valueOf(notesPath)).contains("current");
        assertThat(agent.inputs.get("conversation_id")).asString().startsWith("session_memory_update_");
    }

    @Test
    void directReplaceUsesModelResponseAndNormalizesMarkdownFence() throws Exception {
        SessionMemoryConfig config = new SessionMemoryConfig();
        config.setUpdateMode(SessionMemoryConfig.UpdateMode.DIRECT_REPLACE);
        SessionMemoryUpdateAgent updateAgent = new SessionMemoryUpdateAgent(config);
        updateAgent.setDirectModel(new Model((messages, modelConfig, modelClientConfig, options) -> {
            assertThat(messages).hasSize(2);
            assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
            assertThat(messages.get(1).getContentAsString()).contains("old notes");
            return CompletableFuture.completedFuture(new AssistantMessage("```markdown\n# Updated\n```"));
        }));
        updateAgent.setInheritedSystemPrompt("system");
        Path notesPath = tempDir.resolve("session_context.md");
        Files.writeString(notesPath, "old notes");

        updateAgent.invoke(List.of(), notesPath, "old notes").toCompletableFuture().join();

        assertThat(Files.readString(notesPath)).isEqualTo("# Updated");
    }

    @Test
    void shouldUpdateUsesTokenAndToolCallBaselines() {
        SessionMemoryConfig config = new SessionMemoryConfig();
        config.setTriggerTokens(5);
        config.setTriggerAddTokens(2);
        config.setToolMin(1);
        SessionMemoryManager manager = new SessionMemoryManager(config);
        FakeSession session = new FakeSession("s1");
        FakeModelContext context = new FakeModelContext("ctx", "s1", List.of(new UserMessage("123456789")), 9);
        ContextWindow window = new ContextWindow(List.of(), List.of(new UserMessage("123456789")), List.of(), null);

        assertThat(manager.shouldUpdate(session, context, window)).isTrue();
        assertThat(SessionMemorySupport.getSessionMemoryRuntime(session)).containsEntry("initialized", true);

        AssistantMessage assistantWithTool = AssistantMessage.builder()
                .role("assistant")
                .content("call")
                .toolCalls(List.of(ToolCall.builder().id("call").name("edit_file").arguments("{}").build()))
                .build();
        SessionMemorySupport.updateSessionMemoryRuntime(session, Map.of(
                "initialized", true,
                "tokens_at_last_update", 100,
                "tool_calls_at_last_update", 5
        ));
        FakeModelContext smallerContext = new FakeModelContext("ctx", "s1",
                List.of(new UserMessage("now"), assistantWithTool), 10);

        assertThat(manager.shouldUpdate(session, smallerContext,
                new ContextWindow(List.of(), smallerContext.messages, List.of(), null))).isTrue();
        Map<String, Object> runtime = SessionMemorySupport.getSessionMemoryRuntime(session);
        assertThat(runtime).containsEntry("tokens_at_last_update", 0)
                .containsEntry("tool_calls_at_last_update", 0);
    }

    @Test
    void maybeScheduleUpdateWritesPendingNotesAndFinalRuntime() throws Exception {
        SessionMemoryConfig config = new SessionMemoryConfig();
        config.setTriggerTokens(1);
        config.setUpdateMode(SessionMemoryConfig.UpdateMode.DIRECT_REPLACE);
        SessionMemoryUpdateAgent updateAgent = new SessionMemoryUpdateAgent(config);
        updateAgent.setDirectModel(new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("# Session Title\nUpdated"))));
        SessionMemoryManager manager = new SessionMemoryManager(config, updateAgent, Runnable::run);
        FakeSession session = new FakeSession("s1");
        BaseMessage finalMessage = new AssistantMessage("answer");
        finalMessage.setMetadata(Map.of(SessionMemorySupport.CONTEXT_MESSAGE_ID_KEY, "m-final"));
        FakeModelContext context = new FakeModelContext("ctx", "s1",
                List.of(new UserMessage("question"), finalMessage), 10);
        Path activePath = SessionMemoryManager.getSessionMemoryPath(() -> tempDir, "s1");
        Files.createDirectories(activePath.getParent());
        Files.writeString(activePath, "old");

        manager.maybeScheduleUpdate(new FakeCallback(session, context, List.of()), () -> tempDir)
                .toCompletableFuture()
                .join();

        assertThat(Files.readString(activePath)).isEqualTo("# Session Title\nUpdated");
        Map<String, Object> runtime = SessionMemorySupport.getSessionMemoryRuntime(session);
        assertThat(runtime).containsEntry("initialized", true)
                .containsEntry("is_extracting", false)
                .containsEntry("last_summarized_message_count", 2)
                .containsEntry("notes_upto_message_id", "m-final");
    }

    /**
     * Mutable fake session.
     *
     * <p>Mirrors Python's session state object in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
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
     * Recording update agent fake.
     *
     * <p>Mirrors Python's {@code ReActAgent} collaborator in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    private static final class RecordingAgent implements SessionMemoryUpdateAgent.AgentPort {
        private final List<String> events = new ArrayList<>();
        private Map<String, Object> inputs = Map.of();

        @Override
        public CompletionStage<SessionMemoryUpdateAgent.AgentContextPort> initContext(
                SessionMemoryUpdateAgent.UpdateAgentSessionPort session) {
            events.add("initContext");
            return CompletableFuture.completedFuture(new RecordingAgentContext(events));
        }

        @Override
        public CompletionStage<?> invoke(Map<String, Object> inputs,
                                         SessionMemoryUpdateAgent.UpdateAgentSessionPort session) {
            events.add("invoke");
            this.inputs = new LinkedHashMap<>(inputs);
            return CompletableFuture.completedFuture("ok");
        }
    }

    /**
     * Recording update-agent context fake.
     *
     * <p>Mirrors Python's initialized agent context in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    private record RecordingAgentContext(List<String> events) implements SessionMemoryUpdateAgent.AgentContextPort {
        @Override
        public List<BaseMessage> getMessages() {
            return List.of();
        }

        @Override
        public CompletionStage<Void> addMessages(List<BaseMessage> messages) {
            events.add("addMessages:" + messages.size());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Recording updater session fake.
     *
     * <p>Mirrors Python's updater session lifecycle in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    private record RecordingUpdateSession(List<String> events)
            implements SessionMemoryUpdateAgent.UpdateAgentSessionPort {
        @Override
        public CompletionStage<Void> preRun(Map<String, Object> inputs) {
            events.add("preRun");
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> closeStream() {
            events.add("closeStream");
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> commit() {
            events.add("commit");
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Minimal context fake.
     *
     * <p>Mirrors Python's {@code ModelContext} dependency in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
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
     * Callback fake.
     *
     * <p>Mirrors Python's {@code AgentCallbackContext} in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    private record FakeCallback(FakeSession session, FakeModelContext context, List<BaseMessage> inputMessages)
            implements SessionMemoryManager.AgentCallbackContextPort {
        @Override
        public SessionMemoryManager.InputsPort inputs() {
            return () -> inputMessages;
        }
    }
}
