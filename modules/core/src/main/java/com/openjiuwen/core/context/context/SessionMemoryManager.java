/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Session-memory related helpers used by context compression flows.
 * <p>
 * Ports Python's session memory runtime, completed-round truncation, and threshold scheduling semantics.
 * 
 * @since 0.1.7
 */
public final class SessionMemoryManager {
    /**
     * SESSION_MEMORY_STATE_KEY.
     * 
     * @since 0.1.7
     */
    public static final String SESSION_MEMORY_STATE_KEY = "__session_memory__";

    private final SessionMemoryConfig config;

    /**
     * SessionMemoryManager.
     * 
     * @since 0.1.7
     */
    public SessionMemoryManager() {
        this(new SessionMemoryConfig());
    }

    /**
     * SessionMemoryManager.
     * 
     * @param config config
     * @since 0.1.7
     */
    public SessionMemoryManager(SessionMemoryConfig config) {
        this.config = config == null ? new SessionMemoryConfig() : config;
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SessionMemoryConfig getConfig() {
        return config;
    }

    /**
     * buildSessionMemoryRuntime.
     * 
     * @param memoryPath memoryPath
     * @param pendingMemoryPath pendingMemoryPath
     * @param isRuntimeInitialized isRuntimeInitialized
     * @param tokensAtLastUpdate tokensAtLastUpdate
     * @param toolCallsAtLastUpdate toolCallsAtLastUpdate
     * @param lastSummarizedMessageCount lastSummarizedMessageCount
     * @param notesUptoMessageId notesUptoMessageId
     * @param isExtracting isExtracting
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> buildSessionMemoryRuntime(String memoryPath, String pendingMemoryPath,
            boolean isRuntimeInitialized, int tokensAtLastUpdate, int toolCallsAtLastUpdate,
            int lastSummarizedMessageCount, String notesUptoMessageId, boolean isExtracting) {
        Map<String, Object> runtime = new HashMap<>();
        runtime.put("memory_path", memoryPath != null ? memoryPath : "");
        runtime.put("pending_memory_path", pendingMemoryPath != null ? pendingMemoryPath : "");
        runtime.put("initialized", isRuntimeInitialized);
        runtime.put("is_extracting", isExtracting);
        runtime.put("tokens_at_last_update", tokensAtLastUpdate);
        runtime.put("tool_calls_at_last_update", toolCallsAtLastUpdate);
        runtime.put("last_summarized_message_count", lastSummarizedMessageCount);
        runtime.put("notes_upto_message_id", notesUptoMessageId);
        return runtime;
    }

    /**
     * getSessionMemoryRuntime.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getSessionMemoryRuntime(Session session) {
        if (session == null) {
            return buildSessionMemoryRuntime("", "", false, 0, 0, 0, null, false);
        }
        Object rawState = session.getState(SESSION_MEMORY_STATE_KEY);
        if (!(rawState instanceof Map<?, ?> map)) {
            return buildSessionMemoryRuntime("", "", false, 0, 0, 0, null, false);
        }
        return new HashMap<>((Map<String, Object>) map);
    }

    /**
     * updateSessionMemoryRuntime.
     * 
     * @param session session
     * @param state state
     * @since 0.1.7
     */
    public static void updateSessionMemoryRuntime(Session session, Map<String, Object> state) {
        if (session == null || state == null) {
            return;
        }
        Map<String, Object> merged = getSessionMemoryRuntime(session);
        merged.putAll(state);
        session.updateState(Map.of(SESSION_MEMORY_STATE_KEY, merged));
    }

    /**
     * invalidateSessionMemoryAnchor.
     * 
     * @param session session
     * @since 0.1.7
     */
    public static void invalidateSessionMemoryAnchor(Session session) {
        if (session == null) {
            return;
        }
        Map<String, Object> reset = new HashMap<>();
        reset.put("tokens_at_last_update", 0);
        reset.put("last_summarized_message_count", 0);
        reset.put("notes_upto_message_id", null);
        updateSessionMemoryRuntime(session, reset);
    }

    /**
     * getContextMessageId.
     * 
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static String getContextMessageId(BaseMessage message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> metadata = message.getMetadata();
        if (metadata != null) {
            Object value = metadata.get(ContextUtils.CONTEXT_MESSAGE_ID_KEY);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /**
     * findMessageIndexByContextMessageId.
     * 
     * @param messages messages
     * @param messageId messageId
     * @return the result
     * @since 0.1.7
     */
    public static int findMessageIndexByContextMessageId(List<BaseMessage> messages, String messageId) {
        if (messageId == null || messageId.isBlank() || messages == null) {
            return -1;
        }
        for (int index = 0; index < messages.size(); index++) {
            if (messageId.equals(getContextMessageId(messages.get(index)))) {
                return index;
            }
        }
        return -1;
    }

    /**
     * findLastCompletedApiRoundEnd.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    public static int findLastCompletedApiRoundEnd(List<BaseMessage> messages) {
        List<int[]> completedRounds = groupCompletedApiRounds(messages);
        if (completedRounds.isEmpty()) {
            return 0;
        }
        return completedRounds.get(completedRounds.size() - 1)[1];
    }

    /**
     * truncateContextWindowToCompletedApiRound.
     * 
     * @param contextWindow contextWindow
     * @return the result
     * @since 0.1.7
     */
    public static ContextWindow truncateContextWindowToCompletedApiRound(ContextWindow contextWindow) {
        List<BaseMessage> contextMessages = contextWindow != null && contextWindow.getContextMessages() != null
                ? contextWindow.getContextMessages()
                : List.of();
        int completedEnd = findLastCompletedApiRoundEnd(contextMessages);
        List<BaseMessage> completedMessages =
            completedEnd <= 0 ? List.of() : new ArrayList<>(contextMessages.subList(0, completedEnd));
        return ContextWindow.builder()
                .systemMessages(contextWindow != null && contextWindow.getSystemMessages() != null
                        ? new ArrayList<>(contextWindow.getSystemMessages())
                        : List.of())
                .contextMessages(completedMessages)
                .tools(contextWindow != null && contextWindow.getTools() != null
                        ? new ArrayList<>(contextWindow.getTools())
                        : List.of())
                .build();
    }

    /**
     * collectContextWindow.
     * 
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    public static ContextWindow collectContextWindow(ModelContext context) {
        if (context == null) {
            return ContextWindow.builder().systemMessages(List.of()).contextMessages(List.of()).tools(List.of())
                    .build();
        }
        return ContextWindow.builder().systemMessages(List.of()).contextMessages(new ArrayList<>(context.getMessages()))
                .tools(List.of()).build();
    }

    /**
     * shouldUpdate.
     * 
     * @param session session
     * @param context context
     * @param contextWindow contextWindow
     * @return the result
     * @since 0.1.7
     */
    public boolean shouldUpdate(Session session, ModelContext context, ContextWindow contextWindow) {
        List<BaseMessage> messages = contextWindow != null && contextWindow.getContextMessages() != null
                ? contextWindow.getContextMessages()
                : List.of();
        if (session == null || context == null || messages.isEmpty()) {
            return false;
        }
        Map<String, Object> runtime = normalizedRuntime(session);
        int currentTokens = countTokens(context, contextWindow);
        if (!Boolean.TRUE.equals(runtime.get("initialized"))) {
            if (currentTokens >= config.getTriggerTokens()) {
                runtime.put("initialized", true);
                updateSessionMemoryRuntime(session, runtime);
                return true;
            }
            return false;
        }

        int totalToolCalls = countToolCalls(messages);
        boolean isBaselineReset = false;
        int tokensAtLastUpdate = intValue(runtime.get("tokens_at_last_update"));
        if (currentTokens < tokensAtLastUpdate) {
            runtime.put("tokens_at_last_update", 0);
            tokensAtLastUpdate = 0;
            isBaselineReset = true;
        }
        int toolCallsAtLastUpdate = intValue(runtime.get("tool_calls_at_last_update"));
        if (totalToolCalls < toolCallsAtLastUpdate) {
            runtime.put("tool_calls_at_last_update", 0);
            toolCallsAtLastUpdate = 0;
            isBaselineReset = true;
        }
        if (isBaselineReset) {
            updateSessionMemoryRuntime(session, runtime);
        }

        int tokensSinceLast = currentTokens - tokensAtLastUpdate;
        if (tokensSinceLast < config.getTriggerAddTokens()) {
            return false;
        }
        int toolCallsSinceLast = totalToolCalls - toolCallsAtLastUpdate;
        return toolCallsSinceLast >= config.getToolMin();
    }

    /**
     * maybeScheduleUpdate.
     * 
     * @param session session
     * @param context context
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public boolean maybeScheduleUpdate(Session session, ModelContext context, Object workspace) {
        if (workspace == null || session == null) {
            return false;
        }
        ContextWindow contextWindow = truncateContextWindowToCompletedApiRound(collectContextWindow(context));
        String sessionId = session.getSessionId();
        String memoryPath = sessionMemoryPath(workspace, sessionId);
        String pendingPath = pendingSessionMemoryPath(memoryPath);
        updateSessionMemoryRuntime(session,
                Map.of("session_id", sessionId, "memory_path", memoryPath, "pending_memory_path", pendingPath));
        if (!shouldUpdate(session, context, contextWindow)) {
            return false;
        }
        Map<String, Object> runtime = normalizedRuntime(session);
        runtime.put("is_extracting", true);
        updateSessionMemoryRuntime(session, runtime);
        return true;
    }

    /**
     * groupCompletedApiRounds.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    public static List<int[]> groupCompletedApiRounds(List<BaseMessage> messages) {
        List<int[]> rounds = new ArrayList<>();
        if (messages == null || messages.isEmpty()) {
            return rounds;
        }

        Integer currentStart = null;
        java.util.Set<String> pendingToolCallIds = null;

        for (int index = 0; index < messages.size(); index++) {
            BaseMessage message = messages.get(index);

            if (currentStart == null) {
                currentStart = index;
            } else if (message instanceof UserMessage && pendingToolCallIds == null) {
                currentStart = index;
            }

            if (message instanceof AssistantMessage assistantMessage) {
                List<ToolCall> toolCalls =
                    assistantMessage.getToolCalls() != null ? assistantMessage.getToolCalls() : List.of();
                if (!toolCalls.isEmpty()) {
                    pendingToolCallIds = new java.util.HashSet<>();
                    for (ToolCall toolCall : toolCalls) {
                        if (toolCall.getId() != null && !toolCall.getId().isBlank()) {
                            pendingToolCallIds.add(toolCall.getId());
                        }
                    }
                    if (pendingToolCallIds.isEmpty()) {
                        rounds.add(new int[]{currentStart, index + 1});
                        currentStart = null;
                    }
                    continue;
                }
                rounds.add(new int[]{currentStart, index + 1});
                currentStart = null;
                pendingToolCallIds = null;
                continue;
            }

            if (message instanceof ToolMessage toolMessage && pendingToolCallIds != null) {
                String toolCallId = toolMessage.getToolCallId();
                if (toolCallId != null) {
                    pendingToolCallIds.remove(toolCallId);
                }
                if (pendingToolCallIds.isEmpty()) {
                    rounds.add(new int[]{currentStart, index + 1});
                    currentStart = null;
                    pendingToolCallIds = null;
                }
            }
        }

        return rounds;
    }

    /**
     * countToolCalls.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    public static int countToolCalls(List<BaseMessage> messages) {
        int total = 0;
        if (messages == null) {
            return total;
        }
        for (BaseMessage message : messages) {
            if (message instanceof AssistantMessage assistantMessage) {
                total += assistantMessage.getToolCalls() == null ? 0 : assistantMessage.getToolCalls().size();
            }
        }
        return total;
    }

    /**
     * countTokens.
     * 
     * @param context context
     * @param contextWindow contextWindow
     * @return the result
     * @since 0.1.7
     */
    public static int countTokens(ModelContext context, ContextWindow contextWindow) {
        List<BaseMessage> messages = new ArrayList<>();
        if (contextWindow != null) {
            if (contextWindow.getSystemMessages() != null) {
                messages.addAll(contextWindow.getSystemMessages());
            }
            if (contextWindow.getContextMessages() != null) {
                messages.addAll(contextWindow.getContextMessages());
            }
        }
        if (context != null && context.tokenCounter() != null) {
            try {
                return context.tokenCounter().countMessages(messages);
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // Fall back to approximate token counting if the configured counter rejects the message shape.
            }
        }
        int total = 0;
        for (BaseMessage message : messages) {
            total += ContextUtils.estimateMessageTokens(message);
        }
        return total;
    }

    /**
     * normalizedRuntime.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> normalizedRuntime(Session session) {
        Map<String, Object> state = getSessionMemoryRuntime(session);
        return buildSessionMemoryRuntime(stringValue(state.get("memory_path")),
                stringValue(state.get("pending_memory_path")), Boolean.TRUE.equals(state.get("initialized")),
                intValue(state.get("tokens_at_last_update")), intValue(state.get("tool_calls_at_last_update")),
                intValue(state.get("last_summarized_message_count")), stringValue(state.get("notes_upto_message_id")),
                Boolean.TRUE.equals(state.get("is_extracting")));
    }

    /**
     * intValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                // Non-numeric runtime values use the Python-compatible zero default.
            }
        }
        return 0;
    }

    /**
     * stringValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * sessionMemoryPath.
     * 
     * @param workspace workspace
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    private static String sessionMemoryPath(Object workspace, String sessionId) {
        String root = workspaceRoot(workspace);
        return java.nio.file.Path.of(root).resolve("context").resolve(sessionId + "_context").resolve("session_memory")
                .resolve("session_context.md").toString();
    }

    /**
     * pendingSessionMemoryPath.
     * 
     * @param memoryPath memoryPath
     * @return the result
     * @since 0.1.7
     */
    private static String pendingSessionMemoryPath(String memoryPath) {
        java.nio.file.Path path = java.nio.file.Path.of(memoryPath);
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String pendingName =
            dot >= 0 ? fileName.substring(0, dot) + ".pending" + fileName.substring(dot) : fileName + ".pending";
        return path.getParent().resolve(pendingName).toString();
    }

    /**
     * workspaceRoot.
     * 
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    private static String workspaceRoot(Object workspace) {
        try {
            Object value = workspace.getClass().getMethod("getRootPath").invoke(workspace);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Try the alternate workspace accessor below.
        }
        try {
            Object value = workspace.getClass().getMethod("root").invoke(workspace);
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Fall back to the current directory when the workspace does not expose a root path.
        }
        return ".";
    }
}
