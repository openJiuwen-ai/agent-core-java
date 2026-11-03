/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Module-level helpers for session-memory runtime state, prompt rendering, and message-round selection.
 *
 * <p>Mirrors Python's module functions and constants in
 * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
 */
public final class SessionMemorySupport {
    public static final String SESSION_MEMORY_STATE_KEY = "__session_memory__";
    public static final String CONTEXT_MESSAGE_ID_KEY = "context_message_id";

    public static final String DEFAULT_SESSION_MEMORY_TEMPLATE = """
            # Session Title
            _A short and distinctive 5-10 word descriptive title for the session. Super info dense, no filler_

            # Current State
            _What is actively being worked on right now? Pending tasks not yet completed. Immediate next steps._

            # Task specification
            _What did the user ask to build? Any design decisions or other explanatory context_

            # Files and Functions
            _What are the important files? In short, what do they contain and why are they relevant?_

            # Workflow
            _What bash commands are usually run and in what order? How to interpret their output if not obvious?_

            # Errors & Corrections
            _Errors encountered and how they were fixed.
            What did the user correct? What approaches failed and should not be tried again?_

            # Codebase and System Documentation
            _What are the important system components? How do they work/fit together?_

            # Learnings
            _What has worked well? What has not? What to avoid? Do not duplicate items from other sections_

            # Key results
            _If the user asked a specific output such as an answer to a question,
            a table, or other document, repeat the exact result here_

            # Worklog
            _Step by step, what was attempted, done? Very terse summary for each step_
            """;

    public static final String DEFAULT_SESSION_MEMORY_PROMPT = """
            IMPORTANT: This message and these instructions are NOT part of the actual user conversation. Do NOT include any references to "note-taking", "session notes extraction", or these update instructions in the notes content.

            Based on the user conversation above
            (EXCLUDING this note-taking instruction message as well as system prompt,
            or any past session summaries), update the session notes file.

            The file {{notesPath}} has already been read for you. Here are its current contents:
            <current_notes_content>
            {{currentNotes}}
            </current_notes_content>

            Your ONLY task is to use the edit_file to update the notes file, then stop.
            You can make multiple edits (update every section as needed) - make all
            edit_file calls in parallel in a single message. Do not call any other tools.

            CRITICAL RULES FOR EDITING:
            - The file must maintain its exact structure with all sections, headers, and italic descriptions intact
            -- NEVER modify, delete, or add section headers (the lines starting with '#' like # Task specification)
            -- NEVER modify or delete the italic _section description_ lines
            (these are the lines in italics immediately following each header -
            they start and end with underscores)
            -- The italic _section descriptions_ are TEMPLATE INSTRUCTIONS
            that must be preserved exactly as-is - they guide what content belongs
            in each section
            -- ONLY update the actual content that appears BELOW the italic
            _section descriptions_ within each existing section
            -- Do NOT add any new sections, summaries, or information outside the existing structure
            - Do NOT reference this note-taking process or instructions anywhere in the notes
            - It's OK to skip updating a section if there are no substantial new insights
            to add. Do not add filler content like "No info yet", just leave sections
            blank/unedited if appropriate.
            - Write DETAILED, INFO-DENSE content for each section - include specifics
            like file paths, function names, error messages, exact commands,
            technical details, etc.
            - For "Key results", include the complete, exact output the user requested (e.g., full table, full answer, etc.)
            - Do not include information that's already in the CLAUDE.md files included in the context
            - Keep each section under ~${MAX_SECTION_LENGTH} tokens/words - if a
            section is approaching this limit, condense it by cycling out less
            important details while preserving the most critical information
            - Focus on actionable, specific information that would help someone
            understand or recreate the work discussed in the conversation
            - IMPORTANT: Always update "Current State" to reflect the most recent work -
            this is critical for continuity after compaction

            Use the edit_file with file_path: {{notesPath}}

            STRUCTURE PRESERVATION REMINDER:
            Each section has TWO parts that must be preserved exactly as they appear
            in the current file:
            1. The section header (line starting with #)
            2. The italic description line
            (the _italicized text_ immediately after each header -
            this is a template instruction)

            You ONLY update the actual content that comes AFTER these two preserved lines.
            The italic description lines starting and ending with underscores are part of
            the template structure, NOT content to be edited or removed.

            REMEMBER: Use the edit_file in parallel and stop. Do not continue after
            the edits. Only include insights from the actual user conversation,
            never from these note-taking instructions. Do not delete or change
            section headers or italic _section descriptions_.`
            """;

    public static final String DIRECT_SESSION_MEMORY_PROMPT = """
            IMPORTANT: This message and these instructions are NOT part of the actual user conversation. Do NOT include any references to "note-taking", "session notes extraction", or these update instructions in the notes content.

            Based on the user conversation above
            (EXCLUDING this note-taking instruction message as well as system prompt,
            or any past session summaries), update the session notes file.

            The file {{notesPath}} has already been read for you. Here are its current contents:
            <current_notes_content>
            {{currentNotes}}
            </current_notes_content>

            Your ONLY task is to return the COMPLETE updated notes file content, then stop. Do not call any tools.

            CRITICAL RULES FOR EDITING:
            - The file must maintain its exact structure with all sections, headers, and italic descriptions intact
            -- NEVER modify, delete, or add section headers (the lines starting with '#' like # Task specification)
            -- NEVER modify or delete the italic _section description_ lines
            (these are the lines in italics immediately following each header -
            they start and end with underscores)
            -- The italic _section descriptions_ are TEMPLATE INSTRUCTIONS
            that must be preserved exactly as-is - they guide what content belongs
            in each section
            -- ONLY update the actual content that appears BELOW the italic
            _section descriptions_ within each existing section
            -- Do NOT add any new sections, summaries, or information outside the existing structure
            - Do NOT reference this note-taking process or instructions anywhere in the notes
            - It's OK to skip updating a section if there are no substantial new insights
            to add. Do not add filler content like "No info yet", just leave sections
            blank/unedited if appropriate.
            - Write DETAILED, INFO-DENSE content for each section - include specifics
            like file paths, function names, error messages, exact commands,
            technical details, etc.
            - For "Key results", include the complete, exact output the user requested (e.g., full table, full answer, etc.)
            - Do not include information that's already in the CLAUDE.md files included in the context
            - Keep each section under ~${MAX_SECTION_LENGTH} tokens/words - if a
            section is approaching this limit, condense it by cycling out less
            important details while preserving the most critical information
            - Focus on actionable, specific information that would help someone
            understand or recreate the work discussed in the conversation
            - IMPORTANT: Always update "Current State" to reflect the most recent work -
            this is critical for continuity after compaction
            - Output plain markdown only
            - Do NOT wrap the result in code fences

            STRUCTURE PRESERVATION REMINDER:
            Each section has TWO parts that must be preserved exactly as they appear
            in the current file:
            1. The section header (line starting with #)
            2. The italic description line
            (the _italicized text_ immediately after each header -
            this is a template instruction)

            You ONLY update the actual content that comes AFTER these two preserved lines.
            The italic description lines starting and ending with underscores are part of
            the template structure, NOT content to be edited or removed.
            """;

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionMemorySupport.class);

    private SessionMemorySupport() {
    }

    public static Map<String, Object> buildSessionMemoryRuntime() {
        return buildSessionMemoryRuntime("", "", false, 0, 0, 0, null, false);
    }

    public static Map<String, Object> buildSessionMemoryRuntime(String memoryPath, String pendingMemoryPath,
                                                                boolean initialized, int tokensAtLastUpdate,
                                                                int toolCallsAtLastUpdate,
                                                                int lastSummarizedMessageCount,
                                                                String notesUptoMessageId,
                                                                boolean extracting) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("memory_path", memoryPath == null ? "" : memoryPath);
        state.put("pending_memory_path", pendingMemoryPath == null ? "" : pendingMemoryPath);
        state.put("initialized", initialized);
        state.put("is_extracting", extracting);
        state.put("tokens_at_last_update", tokensAtLastUpdate);
        state.put("tool_calls_at_last_update", toolCallsAtLastUpdate);
        state.put("last_summarized_message_count", lastSummarizedMessageCount);
        state.put("notes_upto_message_id", notesUptoMessageId);
        return state;
    }

    public static Map<String, Object> getSessionMemoryRuntime(SessionStatePort session) {
        if (session == null) {
            LOGGER.info("Session memory runtime is empty");
            return buildSessionMemoryRuntime();
        }
        Object state = session.getState(SESSION_MEMORY_STATE_KEY);
        if (!(state instanceof Map<?, ?> rawState)) {
            LOGGER.info("Session memory runtime is not dict, will return init memory state dict");
            return buildSessionMemoryRuntime();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawState.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    public static void updateSessionMemoryRuntime(SessionStatePort session, Map<String, Object> state) {
        if (session == null) {
            return;
        }
        Map<String, Object> merged = getSessionMemoryRuntime(session);
        if (state != null) {
            merged.putAll(state);
        }
        Map<String, Object> update = new LinkedHashMap<>();
        update.put(SESSION_MEMORY_STATE_KEY, merged);
        session.updateState(update);
    }

    public static void invalidateSessionMemoryAnchor(SessionStatePort session) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("tokens_at_last_update", 0);
        state.put("last_summarized_message_count", 0);
        state.put("notes_upto_message_id", null);
        updateSessionMemoryRuntime(session, state);
    }

    public static String getContextMessageId(BaseMessage message) {
        if (message == null || message.getMetadata() == null) {
            return null;
        }
        Object value = message.getMetadata().get(CONTEXT_MESSAGE_ID_KEY);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    public static int findMessageIndexByContextMessageId(List<BaseMessage> messages, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return -1;
        }
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        for (int index = 0; index < safeMessages.size(); index++) {
            if (messageId.equals(getContextMessageId(safeMessages.get(index)))) {
                return index;
            }
        }
        return -1;
    }

    public static int findLastCompletedApiRoundEnd(List<BaseMessage> messages) {
        List<ApiRound> completedRounds = groupCompletedApiRounds(messages);
        if (completedRounds.isEmpty()) {
            return 0;
        }
        return completedRounds.get(completedRounds.size() - 1).end();
    }

    public static List<ApiRound> groupCompletedApiRounds(List<BaseMessage> messages) {
        List<ApiRound> rounds = new ArrayList<>();
        Integer currentStart = null;
        Set<String> pendingToolCallIds = null;
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;

        for (int index = 0; index < safeMessages.size(); index++) {
            BaseMessage message = safeMessages.get(index);
            if (currentStart == null) {
                currentStart = index;
            } else if (message instanceof UserMessage && pendingToolCallIds == null) {
                currentStart = index;
            }

            if (message instanceof AssistantMessage assistantMessage) {
                List<ToolCall> toolCalls = assistantMessage.getToolCalls();
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    pendingToolCallIds = new LinkedHashSet<>();
                    for (ToolCall toolCall : toolCalls) {
                        if (toolCall.getId() != null && !toolCall.getId().isBlank()) {
                            pendingToolCallIds.add(toolCall.getId());
                        }
                    }
                    if (pendingToolCallIds.isEmpty()) {
                        rounds.add(new ApiRound(currentStart, index + 1));
                        currentStart = null;
                    }
                    continue;
                }
                rounds.add(new ApiRound(currentStart, index + 1));
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
                    rounds.add(new ApiRound(currentStart, index + 1));
                    currentStart = null;
                    pendingToolCallIds = null;
                }
            }
        }
        return rounds;
    }

    public static String buildSessionMemoryPrompt(String notesPath, String currentNotes) {
        return DEFAULT_SESSION_MEMORY_PROMPT
                .replace("{{notesPath}}", notesPath == null ? "" : notesPath)
                .replace("{{currentNotes}}", currentNotes == null ? "" : currentNotes);
    }

    public static String buildDirectSessionMemoryPrompt(String notesPath, String currentNotes) {
        return DIRECT_SESSION_MEMORY_PROMPT
                .replace("{{notesPath}}", notesPath == null ? "" : notesPath)
                .replace("{{currentNotes}}", currentNotes == null ? "" : currentNotes);
    }

    public static String buildSystemPromptText(List<BaseMessage> messages) {
        if (messages == null || messages.isEmpty() || !(messages.get(0) instanceof SystemMessage systemMessage)) {
            return "";
        }
        return systemMessage.getContentAsString();
    }

    static String normalizeDirectResponseContent(Object content) {
        String normalized = content instanceof String text ? text : String.valueOf(content == null ? "" : content);
        normalized = normalized.strip();
        if (normalized.startsWith("```")) {
            List<String> lines = normalized.lines().toList();
            if (lines.size() >= 3 && "```".equals(lines.get(lines.size() - 1).strip())) {
                normalized = String.join("\n", lines.subList(1, lines.size() - 1)).strip();
            }
        }
        return normalized;
    }

    static int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    static boolean booleanValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return defaultValue;
    }

    static String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }

    /**
     * Completed API round bounds using Python's half-open index convention.
     *
     * <p>Mirrors Python's {@code tuple[int, int]} returned by
     * {@code group_completed_api_rounds} in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    public record ApiRound(int start, int end) {
    }

    /**
     * Narrow session-state port for Python session objects exposing {@code get_state}/{@code update_state}.
     *
     * <p>Mirrors Python's dynamic session protocol in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    public interface SessionStatePort {
        Object getState(String key);

        void updateState(Map<String, Object> update);

        default String getSessionId() {
            return "";
        }
    }
}
