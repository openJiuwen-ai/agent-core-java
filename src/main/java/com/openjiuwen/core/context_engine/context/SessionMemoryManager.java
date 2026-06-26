/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.context;

import com.openjiuwen.core.context_engine.ContextWindow;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Coordinates session-memory extraction scheduling, file staging, and runtime anchors.
 *
 * <p>Mirrors Python's {@code SessionMemoryManager} in
 * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
 */
public class SessionMemoryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionMemoryManager.class);

    private final SessionMemoryConfig config;
    private final SessionMemoryUpdateAgent updateAgent;
    private final Executor executor;
    private final Map<String, CompletableFuture<Void>> tasks = new ConcurrentHashMap<>();
    private final Map<String, TaskOwner> taskOwners = new ConcurrentHashMap<>();

    public SessionMemoryManager(SessionMemoryConfig config) {
        this(config, new SessionMemoryUpdateAgent(config), ForkJoinPool.commonPool());
    }

    public SessionMemoryManager(SessionMemoryConfig config, SessionMemoryUpdateAgent updateAgent, Executor executor) {
        this.config = config == null ? new SessionMemoryConfig() : config;
        this.updateAgent = updateAgent == null ? new SessionMemoryUpdateAgent(this.config) : updateAgent;
        this.executor = executor == null ? ForkJoinPool.commonPool() : executor;
    }

    public SessionMemoryConfig getConfig() {
        return config;
    }

    public void bindModelDefaults(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        if (config.getModel() == null) {
            config.setModel(modelConfig);
        }
        if (config.getModelClient() == null) {
            config.setModelClient(modelClientConfig);
        }
        updateAgent.bindModelDefaults(modelConfig, modelClientConfig);
    }

    public CompletionStage<Void> maybeScheduleUpdate(AgentCallbackContextPort callbackContext, WorkspacePort workspace) {
        if (workspace == null || callbackContext == null || callbackContext.session() == null) {
            return CompletableFuture.completedFuture(null);
        }

        SessionMemorySupport.SessionStatePort session = callbackContext.session();
        String sessionId = session.getSessionId();
        CompletableFuture<Void> existingTask = tasks.get(sessionId);
        if (existingTask != null && !existingTask.isDone()) {
            LOGGER.info("[SessionMemory] skip schedule: task already running session_id={}", sessionId);
            return CompletableFuture.completedFuture(null);
        }

        ContextWindow contextWindow = collectContextWindow(callbackContext);
        ContextWindow completedContextWindow = truncateContextWindowToCompletedApiRound(contextWindow);
        Path notesPath = getSessionMemoryPath(workspace, sessionId);
        Path pendingNotesPath = getPendingSessionMemoryPath(notesPath);
        Map<String, Object> runtimeUpdate = new LinkedHashMap<>();
        runtimeUpdate.put("session_id", sessionId);
        runtimeUpdate.put("memory_path", String.valueOf(notesPath));
        runtimeUpdate.put("pending_memory_path", String.valueOf(pendingNotesPath));
        SessionMemorySupport.updateSessionMemoryRuntime(session, runtimeUpdate);

        if (!shouldUpdate(session, callbackContext.context(), completedContextWindow)) {
            LOGGER.info("[SessionMemory] skip schedule: should_update returned False session_id={}", sessionId);
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Object> runtime = SessionMemorySupport.getSessionMemoryRuntime(session);
        runtime.put("is_extracting", true);
        SessionMemorySupport.updateSessionMemoryRuntime(session, runtime);
        LOGGER.info("[SessionMemory] schedule update session_id={} notes_path={} messages={}",
                sessionId, notesPath, completedContextWindow.getContextMessages().size());

        CompletableFuture<Void> task = CompletableFuture.runAsync(
                () -> updateBackground(callbackContext, workspace, completedContextWindow).toCompletableFuture().join(),
                executor
        );
        taskOwners.put(sessionId, new TaskOwner(session, callbackContext.context()));
        task.whenComplete((ignored, failure) -> onTaskDone(sessionId, task, failure));
        tasks.put(sessionId, task);
        return CompletableFuture.completedFuture(null);
    }

    public void updateInheritedSystemPrompt(AgentCallbackContextPort callbackContext) {
        List<BaseMessage> messages = callbackContext == null || callbackContext.inputs() == null
                ? List.of()
                : List.copyOf(callbackContext.inputs().messages());
        updateAgent.setInheritedSystemPrompt(SessionMemorySupport.buildSystemPromptText(messages));
    }

    public static ContextWindow collectContextWindow(AgentCallbackContextPort callbackContext) {
        if (callbackContext == null || callbackContext.context() == null) {
            return new ContextWindow(List.of(), List.of(), List.of(), null);
        }
        return new ContextWindow(
                List.of(),
                callbackContext.context().getMessages(null, true),
                List.of(),
                null
        );
    }

    public void shutdown() {
        for (CompletableFuture<Void> task : tasks.values()) {
            if (!task.isDone()) {
                task.cancel(true);
            }
        }
        tasks.clear();
        taskOwners.clear();
    }

    public boolean shouldUpdate(SessionMemorySupport.SessionStatePort session, ModelContext context,
                                ContextWindow contextWindow) {
        List<BaseMessage> messages = contextWindow == null ? List.of() : contextWindow.getContextMessages();
        if (session == null || context == null || messages.isEmpty()) {
            LOGGER.info("[SessionMemory] should_update skipped session_exists={} context_exists={} messages={}",
                    session != null, context != null, messages.size());
            return false;
        }

        Map<String, Object> runtime = getRuntimeState(session);
        int currentTokens = countTokens(context, contextWindow);
        if (!SessionMemorySupport.booleanValue(runtime.get("initialized"), false)) {
            if (currentTokens >= config.getTriggerTokens()) {
                runtime.put("initialized", true);
                setRuntimeState(session, runtime);
                return true;
            }
            return false;
        }

        int totalToolCalls = countToolCalls(messages);
        boolean baselineReset = false;
        int tokensAtLastUpdate = SessionMemorySupport.intValue(runtime.get("tokens_at_last_update"), 0);
        if (currentTokens < tokensAtLastUpdate) {
            runtime.put("tokens_at_last_update", 0);
            baselineReset = true;
        }
        int toolCallsAtLastUpdate = SessionMemorySupport.intValue(runtime.get("tool_calls_at_last_update"), 0);
        if (totalToolCalls < toolCallsAtLastUpdate) {
            runtime.put("tool_calls_at_last_update", 0);
            baselineReset = true;
        }
        if (baselineReset) {
            setRuntimeState(session, runtime);
        }

        int tokensSinceLast = currentTokens - SessionMemorySupport.intValue(runtime.get("tokens_at_last_update"), 0);
        if (tokensSinceLast < config.getTriggerAddTokens()) {
            return false;
        }
        int toolCallsSinceLast = totalToolCalls
                - SessionMemorySupport.intValue(runtime.get("tool_calls_at_last_update"), 0);
        return toolCallsSinceLast >= config.getToolMin();
    }

    private CompletionStage<Void> updateBackground(AgentCallbackContextPort callbackContext, WorkspacePort workspace,
                                                   ContextWindow contextWindow) {
        if (callbackContext.session() == null) {
            return CompletableFuture.completedFuture(null);
        }

        List<BaseMessage> messages = contextWindow == null ? List.of() : contextWindow.getContextMessages();
        SessionMemorySupport.SessionStatePort session = callbackContext.session();
        String sessionId = session.getSessionId();
        Map<String, Object> runtime = getRuntimeState(session);
        Path notesPath = getSessionMemoryPath(workspace, sessionId);
        Path pendingNotesPath = getPendingSessionMemoryPath(notesPath);
        String currentNotes;
        try {
            currentNotes = readOrInitSessionMemory(notesPath);
            preparePendingSessionMemory(notesPath, pendingNotesPath, currentNotes);
        } catch (IOException ex) {
            return CompletableFuture.failedFuture(ex);
        }

        CompletionStage<Void> updateStage = updateAgent.invoke(
                contextWindow == null ? List.of() : contextWindow.getMessages(),
                pendingNotesPath,
                currentNotes
        ).thenRun(() -> {
            try {
                commitPendingSessionMemory(pendingNotesPath, notesPath);
            } catch (IOException ex) {
                throw new CompletionException(ex);
            }
            if (callbackContext.context() != null) {
                runtime.put("tokens_at_last_update", countTokens(callbackContext.context(), contextWindow));
            }
            runtime.put("tool_calls_at_last_update", countToolCalls(messages));
            runtime.put("last_summarized_message_count", messages.size());
            runtime.put("notes_upto_message_id", messages.isEmpty()
                    ? null
                    : SessionMemorySupport.getContextMessageId(messages.get(messages.size() - 1)));
            runtime.put("initialized", true);
        });

        return updateStage.handle((ignored, failure) -> {
            runtime.put("is_extracting", false);
            setRuntimeState(session, runtime);
            if (failure != null) {
                LOGGER.warn("[SessionMemory] update failed session_id={} notes_path={} pending_notes_path={}",
                        sessionId, notesPath, pendingNotesPath, failure);
                throw new CompletionException(unwrap(failure));
            }
            return null;
        });
    }

    public static Path getSessionMemoryPath(WorkspacePort workspace, String sessionId) {
        return workspace.rootPath()
                .resolve("context")
                .resolve(sessionId + "_context")
                .resolve("session_memory")
                .resolve("session_context.md");
    }

    public static Path getPendingSessionMemoryPath(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String stem = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
        String suffix = dotIndex >= 0 ? fileName.substring(dotIndex) : "";
        return path.resolveSibling(stem + ".pending" + suffix);
    }

    public static String readOrInitSessionMemory(Path path) throws IOException {
        if (Files.exists(path)) {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        Files.createDirectories(path.getParent());
        Path templatePath = path.getParent().getParent().getParent().resolve("session_memory.md");
        if (Files.exists(templatePath)) {
            Files.copy(templatePath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        Files.writeString(path, SessionMemorySupport.DEFAULT_SESSION_MEMORY_TEMPLATE, StandardCharsets.UTF_8);
        return SessionMemorySupport.DEFAULT_SESSION_MEMORY_TEMPLATE;
    }

    public static void preparePendingSessionMemory(Path activePath, Path pendingPath, String currentNotes)
            throws IOException {
        Files.createDirectories(pendingPath.getParent());
        if (Files.exists(activePath)) {
            Files.copy(activePath, pendingPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }
        Files.writeString(pendingPath, currentNotes == null ? "" : currentNotes, StandardCharsets.UTF_8);
    }

    public static void commitPendingSessionMemory(Path pendingPath, Path activePath) throws IOException {
        if (!Files.exists(pendingPath)) {
            throw new IllegalStateException("Pending session memory does not exist: " + pendingPath);
        }
        Files.move(pendingPath, activePath, StandardCopyOption.REPLACE_EXISTING);
    }

    public static int countToolCalls(List<BaseMessage> messages) {
        int total = 0;
        for (BaseMessage message : messages == null ? List.<BaseMessage>of() : messages) {
            if (message instanceof AssistantMessage assistantMessage && assistantMessage.getToolCalls() != null) {
                total += assistantMessage.getToolCalls().size();
            }
        }
        return total;
    }

    public static int countTokens(ModelContext context, ContextWindow contextWindow) {
        ModelContext.TokenCounterPort tokenCounter = context.tokenCounter();
        List<BaseMessage> allMessages = new ArrayList<>();
        if (contextWindow != null) {
            allMessages.addAll(contextWindow.getSystemMessages());
            allMessages.addAll(contextWindow.getContextMessages());
        }
        if (tokenCounter != null) {
            try {
                return tokenCounter.countTokens(allMessages);
            } catch (RuntimeException ex) {
                LOGGER.debug("Failed to count session memory tokens with token counter", ex);
            }
        }
        int total = 0;
        for (BaseMessage message : allMessages) {
            total += estimateMessageTokens(message);
        }
        return total;
    }

    public static int estimateMessageTokens(BaseMessage message) {
        return ContextUtils.estimateMessageTokens(message);
    }

    public static int findLastCompletedApiRoundEnd(List<BaseMessage> messages) {
        return SessionMemorySupport.findLastCompletedApiRoundEnd(messages);
    }

    public static List<BaseMessage> truncateMessagesToCompletedApiRound(List<BaseMessage> messages) {
        int completedEnd = findLastCompletedApiRoundEnd(messages);
        if (completedEnd <= 0) {
            return List.of();
        }
        return new ArrayList<>(messages.subList(0, completedEnd));
    }

    public static ContextWindow truncateContextWindowToCompletedApiRound(ContextWindow contextWindow) {
        return new ContextWindow(
                contextWindow == null ? List.of() : contextWindow.getSystemMessages(),
                truncateMessagesToCompletedApiRound(contextWindow == null ? List.of() : contextWindow.getContextMessages()),
                contextWindow == null ? List.of() : contextWindow.getTools(),
                contextWindow == null ? null : contextWindow.getStatistic()
        );
    }

    public static List<BaseMessage> selectUnsummarizedMessages(List<BaseMessage> messages, String notesUptoMessageId) {
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        int messageIndex = SessionMemorySupport.findMessageIndexByContextMessageId(safeMessages, notesUptoMessageId);
        if (messageIndex >= 0) {
            return new ArrayList<>(safeMessages.subList(messageIndex + 1, safeMessages.size()));
        }
        return new ArrayList<>(safeMessages);
    }

    public static Map<String, Object> getRuntimeState(SessionMemorySupport.SessionStatePort session) {
        Map<String, Object> state = SessionMemorySupport.getSessionMemoryRuntime(session);
        return SessionMemorySupport.buildSessionMemoryRuntime(
                Objects.toString(state.get("memory_path"), ""),
                Objects.toString(state.get("pending_memory_path"), ""),
                SessionMemorySupport.booleanValue(state.get("initialized"), false),
                SessionMemorySupport.intValue(state.get("tokens_at_last_update"), 0),
                SessionMemorySupport.intValue(state.get("tool_calls_at_last_update"), 0),
                SessionMemorySupport.intValue(state.get("last_summarized_message_count"), 0),
                SessionMemorySupport.stringValue(state.get("notes_upto_message_id")),
                SessionMemorySupport.booleanValue(state.get("is_extracting"), false)
        );
    }

    public static void setRuntimeState(SessionMemorySupport.SessionStatePort session, Map<String, Object> state) {
        SessionMemorySupport.updateSessionMemoryRuntime(session, state);
    }

    CompletableFuture<Void> getTask(String sessionId) {
        return tasks.get(sessionId);
    }

    private void onTaskDone(String sessionId, CompletableFuture<Void> task, Throwable failure) {
        tasks.remove(sessionId, task);
        taskOwners.remove(sessionId);
        if (task.isCancelled()) {
            return;
        }
        if (failure != null) {
            LOGGER.warn("[SessionMemoryManager] Session memory background task failed: {}", failure.getMessage());
        }
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return failure;
    }

    /**
     * Callback data supplied by an agent lifecycle hook.
     *
     * <p>Mirrors Python's {@code AgentCallbackContext} in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    public interface AgentCallbackContextPort {
        SessionMemorySupport.SessionStatePort session();

        ModelContext context();

        InputsPort inputs();
    }

    /**
     * Agent input view used to inherit the leading system prompt.
     *
     * <p>Mirrors Python's {@code ctx.inputs.messages} in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    public interface InputsPort {
        List<BaseMessage> messages();
    }

    /**
     * Workspace root provider for session-memory files.
     *
     * <p>Mirrors Python's {@code workspace.root_path} in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    public interface WorkspacePort {
        Path rootPath();
    }

    /**
     * Tracks background task owner objects for cleanup parity.
     *
     * <p>Mirrors Python's {@code _task_owners} values in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    private record TaskOwner(SessionMemorySupport.SessionStatePort session, ModelContext context) {
    }
}
