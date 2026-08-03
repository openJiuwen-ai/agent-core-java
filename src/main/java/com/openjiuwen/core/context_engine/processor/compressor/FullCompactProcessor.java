/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.compressor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.context.SessionMemorySupport;
import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.context_engine.processor.ContextEvent;
import com.openjiuwen.core.context_engine.processor.ContextProcessor;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full conversation fallback compactor aligned with Claude Code full compact flow.
 *
 * <p>Mirrors Python's {@code FullCompactProcessor} in
 * {@code openjiuwen/core/context_engine/processor/compressor/full_compact_processor.py}.</p>
 */
public class FullCompactProcessor extends ContextProcessor {
    public static final String FULL_COMPACT_BOUNDARY_MARKER = "[FULL_COMPACT_BOUNDARY]";
    public static final String FULL_COMPACT_STATE_MARKER = "[FULL_COMPACT_STATE]";
    public static final String SESSION_MEMORY_BOUNDARY_MARKER = "[SESSION_MEMORY_BOUNDARY]";
    public static final String FULL_COMPACT_SYNTHETIC_USER_MARKER =
            "[earlier conversation truncated for compaction retry]";
    public static final String FULL_COMPACT_SUMMARY_INTRO =
            "This session is being continued from a previous conversation that "
                    + "ran out of context. The summary below covers the earlier portion "
                    + "of the conversation.";
    public static final String FULL_COMPACT_RECENT_MESSAGES_NOTICE = "Recent messages are preserved verbatim.";
    public static final String SESSION_MEMORY_SUMMARY_INTRO =
            "Earlier conversation has been replaced with the session memory file. "
                    + "Use it as the canonical summary of prior work.";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Pattern ANALYSIS_BLOCK = Pattern.compile("<analysis>[\\s\\S]*?</analysis>");
    private static final Pattern SUMMARY_BLOCK = Pattern.compile("<summary>([\\s\\S]*?)</summary>");
    private static final String BASE_COMPACT_PROMPT = """
            CRITICAL: Respond with TEXT ONLY. Do NOT call any tools.
            
            - Do NOT use Read, Bash, Grep, Glob, Edit, Write, or ANY other tool.
            - You already have all the context you need in the conversation above.
            - Tool calls will be REJECTED and will waste your only turn - you will fail the task.
            - Your entire response must be plain text: an <analysis> block followed by a <summary> block.
            
            Your task is to create a detailed summary of the conversation so far, paying close attention to the
            user's explicit requests and your previous actions. Provide an <analysis> block followed by a
            <summary> block.
            """;

    static {
        ContextEngine.registerProcessor("FullCompactProcessor", FullCompactProcessor.class);
    }

    private final FullCompactProcessorConfig config;
    private final int triggerTotalTokens;
    private final int compressionCallMaxTokens;
    private final int messagesToKeep;
    private final boolean keepToolMessagePairs;
    private final int stateSnapshotMaxChars;
    private final String marker;
    private final String stateMarker;
    private final String syntheticUserMarker;
    private final String summaryIntro;
    private final String recentMessagesNotice;
    private final boolean sessionMemoryEnabled;
    private final String sessionMemoryMarker;
    private final String sessionMemoryIntro;
    private final FullCompactStateReinjector stateReinjector;
    private final Model model;

    public FullCompactProcessor(Object config) {
        this(asConfig(config));
    }

    public FullCompactProcessor(FullCompactProcessorConfig config) {
        this(config, config != null && config.getModelClient() != null
                ? new Model(config.getModelClient(), config.getModel())
                : null);
    }

    FullCompactProcessor(FullCompactProcessorConfig config, Model model) {
        super(config == null ? new FullCompactProcessorConfig() : config);
        this.config = config == null ? new FullCompactProcessorConfig() : config;
        this.triggerTotalTokens = this.config.getTriggerTotalTokens();
        this.compressionCallMaxTokens = this.config.getCompressionCallMaxTokens();
        this.messagesToKeep = this.config.getMessagesToKeep();
        this.keepToolMessagePairs = this.config.isKeepToolMessagePairs();
        this.stateSnapshotMaxChars = this.config.getStateSnapshotMaxChars();
        this.marker = this.config.getMarker();
        this.stateMarker = this.config.getStateMarker();
        this.syntheticUserMarker = this.config.getSyntheticUserMarker();
        this.summaryIntro = this.config.getSummaryIntro();
        this.recentMessagesNotice = this.config.getRecentMessagesNotice();
        this.sessionMemoryEnabled = this.config.isSessionMemoryEnabled();
        this.sessionMemoryMarker = this.config.getSessionMemoryMarker();
        this.sessionMemoryIntro = this.config.getSessionMemoryIntro();
        this.model = model;
        this.stateReinjector = new FullCompactStateReinjector();
        this.stateReinjector.registerBuilder("skills", "SKILLS", this::buildSkillReinjectedContent);
        this.stateReinjector.registerBuilder("task_status", "TASK_STATUS", this::buildTaskStatusReinjectedContent);
        this.stateReinjector.registerBuilder("plan_mode", "PLAN_MODE", this::buildPlanModeReinjectedContent);
    }

    public FullCompactProcessorConfig getConfig() {
        return config;
    }

    public String getStateMarker() {
        return stateMarker;
    }

    public FullCompactProcessorConfig getAdvancedConfig() {
        return config;
    }

    @Override
    public CompletionStage<Boolean> triggerAddMessages(SessionModelContext context, List<BaseMessage> messagesToAdd,
                                                       Map<String, Object> kwargs) {
        List<BaseMessage> candidateMessages = new ArrayList<>(context.getMessages());
        candidateMessages.addAll(messagesToAdd == null ? List.of() : messagesToAdd);
        if (!apiRound(candidateMessages)) {
            return CompletableFuture.completedFuture(false);
        }
        int candidateTokens = countContextWindowTokens(List.of(), candidateMessages, context);
        return CompletableFuture.completedFuture(candidateTokens > triggerTotalTokens);
    }

    @Override
    public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                            List<BaseMessage> messagesToAdd,
                                                                            boolean force,
                                                                            Map<String, Object> kwargs) {
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        allMessages.addAll(messagesToAdd == null ? List.of() : messagesToAdd);
        resetCompressionUsage();
        ReplacementResult result = buildReplacementMessages(context, allMessages);
        if (result == null || result.messages() == null) {
            return CompletableFuture.completedFuture(
                    new SessionModelContext.ProcessResult(null, messagesToAdd == null ? List.of() : messagesToAdd,
                            null));
        }
        context.setMessages(result.messages(), true);
        ContextEvent event = new ContextEvent(
                processorType(),
                indexRange(0, allMessages.size() - 1),
                result.compactSummary(),
                currentCompressionUsage());
        if (result.sessionMemoryMessage() == null) {
            invalidateSessionMemoryAnchor(context);
        }
        return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(event, List.of(), null));
    }

    ReplacementResult buildReplacementMessages(SessionModelContext context, List<BaseMessage> allMessages) {
        int boundaryIndex = findLastCompactionBoundaryIndex(allMessages);
        SplitMessages split = splitMessagesAtCompactionBoundary(allMessages, boundaryIndex);
        if (split.activeMessages().isEmpty()) {
            return null;
        }

        SessionMemoryReplacement sessionMemory = buildSessionMemoryMessages(
                context, split.prefix(), split.activeMessages(), boundaryIndex >= 0);
        if (sessionMemory != null && sessionMemory.messages() != null) {
            int sessionMemoryTokens = countContextWindowTokens(List.of(), sessionMemory.messages(), context);
            if (sessionMemoryTokens <= triggerTotalTokens) {
                return new ReplacementResult(sessionMemory.messages(),
                        CompressorUtils.messageToText(sessionMemory.sessionMemoryMessage()),
                        sessionMemory.sessionMemoryMessage());
            }
        }

        FullCompactReplacement fullCompact = buildFullCompactMessages(context, split.prefix(), split.activeMessages());
        if (fullCompact == null) {
            return null;
        }
        return new ReplacementResult(fullCompact.messages(), fullCompact.compactSummary(), null);
    }

    FullCompactReplacement buildFullCompactMessages(SessionModelContext context, List<BaseMessage> prefix,
                                                    List<BaseMessage> activeMessages) {
        List<BaseMessage> compactSource = prepareMessagesForPrompt(stripMediaMessages(activeMessages));
        if (compactSource.isEmpty()) {
            return null;
        }
        List<BaseMessage> compactInput = truncateForPromptBudget(compactSource, context);
        if (compactInput.isEmpty()) {
            return null;
        }
        String summary = generateSummary(compactInput, context);
        if (summary.isBlank()) {
            return null;
        }
        List<BaseMessage> messagesToKeep = selectMessagesToKeep(activeMessages);
        UserMessage summaryMessage = new UserMessage(buildSummaryMessage(summary, !messagesToKeep.isEmpty()));
        SystemMessage boundary = new SystemMessage(marker + "\nConversation compacted");

        List<BaseMessage> newContextMessages = new ArrayList<>(prefix == null ? List.of() : prefix);
        newContextMessages.add(boundary);
        newContextMessages.add(summaryMessage);
        newContextMessages.addAll(messagesToKeep);
        newContextMessages.addAll(buildReinjectedStateMessages(context, activeMessages, messagesToKeep, summaryMessage,
                boundary, List.of("plan", "plan_mode", "skills", "task_status")));
        return new FullCompactReplacement(newContextMessages, summary);
    }

    SessionMemoryReplacement buildSessionMemoryMessages(SessionModelContext context, List<BaseMessage> prefix,
                                                        List<BaseMessage> activeMessages,
                                                        boolean hasCompactionBoundary) {
        if (!sessionMemoryEnabled) {
            return null;
        }
        Map<String, Object> runtime = loadSessionMemoryRuntime(context);
        String sessionMemoryText = loadSessionMemoryText(context, runtime);
        if (sessionMemoryText.isBlank()) {
            return null;
        }
        List<BaseMessage> preservedMessages = selectMessagesAfterSessionMemory(activeMessages, runtime,
                hasCompactionBoundary);
        if (preservedMessages == null) {
            return null;
        }
        SystemMessage boundary = new SystemMessage(
                sessionMemoryMarker + "\nEarlier conversation replaced with session memory");
        UserMessage sessionMemoryMessage = new UserMessage(
                buildSessionMemoryMessage(sessionMemoryText, !preservedMessages.isEmpty()));
        List<BaseMessage> candidateMessages = new ArrayList<>(prefix == null ? List.of() : prefix);
        candidateMessages.add(boundary);
        candidateMessages.add(sessionMemoryMessage);
        candidateMessages.addAll(preservedMessages);
        candidateMessages.addAll(buildReinjectedStateMessages(context, activeMessages, preservedMessages,
                sessionMemoryMessage, boundary, List.of("plan")));
        return new SessionMemoryReplacement(candidateMessages, sessionMemoryMessage);
    }

    List<BaseMessage> selectMessagesAfterSessionMemory(List<BaseMessage> activeMessages,
                                                       Map<String, Object> sessionMemoryRuntime,
                                                       boolean hasCompactionBoundary) {
        String notesUptoMessageId = stringValue(sessionMemoryRuntime.get("notes_upto_message_id"));
        int summarizedMessageIndex = SessionMemorySupport.findMessageIndexByContextMessageId(
                activeMessages, notesUptoMessageId);
        if (summarizedMessageIndex >= 0) {
            BaseMessage summarizedMessage = activeMessages.get(summarizedMessageIndex);
            if (isSessionMemorySummaryMessage(summarizedMessage)) {
                return new ArrayList<>(activeMessages.subList(summarizedMessageIndex + 1, activeMessages.size()));
            }
            int completedEnd = SessionMemorySupport.findLastCompletedApiRoundEnd(
                    activeMessages.subList(0, summarizedMessageIndex + 1));
            if (completedEnd <= 0) {
                return null;
            }
            return new ArrayList<>(activeMessages.subList(completedEnd, activeMessages.size()));
        }
        return null;
    }

    List<BaseMessage> buildReinjectedStateMessages(SessionModelContext context, List<BaseMessage> sourceMessages,
                                                   List<BaseMessage> messagesToKeep, UserMessage summaryMessage,
                                                   SystemMessage boundaryMessage, List<String> builderNames) {
        List<BaseMessage> candidateMessages = prepareMessagesForPrompt(sourceMessages);
        if (candidateMessages.isEmpty()) {
            return List.of();
        }
        Set<String> activeBuilderNames = builderNames == null ? null : new LinkedHashSet<>(builderNames);
        List<BaseMessage> stateMessages = new ArrayList<>();
        for (FullCompactStateReinjector.BuilderSpec spec : stateReinjector.iterBuilders()) {
            if (activeBuilderNames != null && !activeBuilderNames.contains(spec.name())) {
                continue;
            }
            Object content = spec.builder().apply(new FullCompactStateReinjector.BuildContext(this, context,
                    candidateMessages, messagesToKeep == null ? List.of() : messagesToKeep));
            if (content instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof BaseMessage message) {
                        stateMessages.add(message);
                    }
                }
                continue;
            }
            if (content != null && !String.valueOf(content).isBlank()) {
                stateMessages.add(makeStateMessage(spec.label(), String.valueOf(content)));
            }
        }
        return stateMessages;
    }

    public String truncateStateText(String text) {
        String safeText = text == null ? "" : text;
        if (safeText.length() <= stateSnapshotMaxChars) {
            return safeText;
        }
        return buildHeadTailTruncatedText(safeText, stateSnapshotMaxChars);
    }

    @Override
    public void loadState(Map<String, Object> state) {
        // Python implementation is currently stateless.
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of();
    }

    private Object buildSkillReinjectedContent(FullCompactStateReinjector.BuildContext input) {
        Set<String> keepSignatures = new HashSet<>();
        for (BaseMessage message : input.messagesToKeep()) {
            keepSignatures.add(CompressorUtils.messageSignature(message));
        }
        List<List<BaseMessage>> selectedRounds = new ArrayList<>();
        Set<List<String>> seenRoundSignatures = new HashSet<>();
        List<List<BaseMessage>> groups = CompressorUtils.groupCompletedApiRoundMessages(input.messages());
        for (int index = groups.size() - 1; index >= 0; index--) {
            List<BaseMessage> roundMessages = groups.get(index);
            List<String> roundSignatures = roundMessages.stream().map(CompressorUtils::messageSignature).toList();
            if (seenRoundSignatures.contains(roundSignatures)) {
                continue;
            }
            boolean overlapsKeep = roundSignatures.stream().anyMatch(keepSignatures::contains);
            if (overlapsKeep || !CompressorUtils.roundContainsSkillRead(roundMessages)) {
                continue;
            }
            selectedRounds.add(new ArrayList<>(roundMessages));
            seenRoundSignatures.add(roundSignatures);
            if (selectedRounds.size() >= config.getReinjectRecentSkills()) {
                break;
            }
        }
        List<BaseMessage> reinjectedMessages = new ArrayList<>();
        for (int index = selectedRounds.size() - 1; index >= 0; index--) {
            List<String> lines = new ArrayList<>();
            for (BaseMessage message : selectedRounds.get(index)) {
                lines.add("role=" + message.getRole() + ", content=" + CompressorUtils.messageToText(message));
            }
            reinjectedMessages.add(new UserMessage(stateMarker + "\n[SKILLS]\n"
                    + truncateStateText(String.join("\n", lines))));
        }
        return reinjectedMessages;
    }

    @SuppressWarnings("unchecked")
    private Object buildTaskStatusReinjectedContent(FullCompactStateReinjector.BuildContext input) {
        Map<String, Object> state = loadWholeSessionState(input.context());
        Object taskStateObject = state.get("task_state");
        Map<String, Object> taskState = taskStateObject instanceof Map<?, ?> rawMap
                ? toStringObjectMap(rawMap)
                : Map.of();
        int iteration = intValue(taskState.get("iteration"));
        Object pendingObject = taskState.get("pending_follow_ups");
        int pendingFollowUps = pendingObject instanceof List<?> list ? list.size() : 0;
        Object stopStateObject = taskState.get("stop_condition_state");
        Map<String, Object> stopState = stopStateObject instanceof Map<?, ?> rawMap
                ? toStringObjectMap(rawMap)
                : Map.of();
        String stopReason = stringValue(stopState.get("stop_reason"));
        if (iteration == 0 && pendingFollowUps == 0 && stopReason == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add("Current task-loop status for this session:");
        lines.add("- Completed outer-loop rounds: " + iteration + ".");
        lines.add("- Pending follow-up queries: " + pendingFollowUps + ".");
        if (stopReason != null) {
            lines.add("- Last recorded stop reason: " + stopReason + ".");
        }
        return truncateStateText(String.join("\n", lines));
    }

    private Object buildPlanModeReinjectedContent(FullCompactStateReinjector.BuildContext input) {
        Map<String, Object> state = loadWholeSessionState(input.context());
        Object planModeObject = state.get("plan_mode");
        if (!(planModeObject instanceof Map<?, ?> rawMap)) {
            return "";
        }
        Map<String, Object> planMode = toStringObjectMap(rawMap);
        String mode = stringValueOrDefault(planMode.get("mode"), "auto");
        String prePlanMode = stringValueOrDefault(planMode.get("pre_plan_mode"), "");
        String planSlug = stringValueOrDefault(planMode.get("plan_slug"), "");
        List<String> lines = new ArrayList<>();
        lines.add("Current plan-mode status for this session:");
        lines.add("- Active mode: " + mode + ".");
        if (!prePlanMode.isBlank()) {
            lines.add("- Previous mode before entering plan mode: " + prePlanMode + ".");
        }
        if (!planSlug.isBlank()) {
            lines.add("- Active plan identifier: " + planSlug + ".");
        }
        return truncateStateText(String.join("\n", lines));
    }

    private List<BaseMessage> truncateForPromptBudget(List<BaseMessage> messages, SessionModelContext context) {
        List<List<BaseMessage>> groups = CompressorUtils.groupCompletedApiRoundMessages(messages);
        while (!groups.isEmpty()) {
            List<BaseMessage> candidate = flatten(groups);
            if (countPromptTokens(candidate, context) <= compressionCallMaxTokens) {
                return candidate;
            }
            if (groups.size() == 1) {
                return truncateMessagesFromHead(candidate, context);
            }
            groups = new ArrayList<>(groups.subList(1, groups.size()));
            if (!groups.isEmpty() && !groups.get(0).isEmpty() && groups.get(0).get(0) instanceof AssistantMessage) {
                List<BaseMessage> adjusted = new ArrayList<>();
                adjusted.add(new UserMessage(syntheticUserMarker));
                adjusted.addAll(groups.get(0));
                groups.set(0, adjusted);
            }
        }
        return buildMinimalCompactInput(messages);
    }

    private List<BaseMessage> truncateMessagesFromHead(List<BaseMessage> messages, SessionModelContext context) {
        List<BaseMessage> candidate = new ArrayList<>(messages == null ? List.of() : messages);
        while (!candidate.isEmpty()) {
            if (countPromptTokens(candidate, context) <= compressionCallMaxTokens) {
                return candidate;
            }
            if (isSyntheticMarkerMessage(candidate.get(0))) {
                if (candidate.size() == 1) {
                    return buildMinimalCompactInput(messages);
                }
                candidate = new ArrayList<>(candidate.subList(Math.min(2, candidate.size()), candidate.size()));
            } else {
                candidate = new ArrayList<>(candidate.subList(1, candidate.size()));
            }
            if (!candidate.isEmpty() && candidate.get(0) instanceof AssistantMessage) {
                List<BaseMessage> adjusted = new ArrayList<>();
                adjusted.add(new UserMessage(syntheticUserMarker));
                adjusted.addAll(candidate);
                candidate = adjusted;
            }
        }
        return buildMinimalCompactInput(messages);
    }

    private List<BaseMessage> buildMinimalCompactInput(List<BaseMessage> messages) {
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        if (safeMessages.isEmpty()) {
            return List.of();
        }
        BaseMessage tail = safeMessages.get(safeMessages.size() - 1);
        if (tail instanceof AssistantMessage) {
            return List.of(new UserMessage(syntheticUserMarker), tail);
        }
        return List.of(tail);
    }

    private List<BaseMessage> selectMessagesToKeep(List<BaseMessage> messages) {
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        if (messagesToKeep <= 0 || safeMessages.isEmpty()) {
            return List.of();
        }
        int startIndex = Math.max(safeMessages.size() - messagesToKeep, 0);
        if (keepToolMessagePairs) {
            startIndex = adjustStartIndexForToolPairs(safeMessages, startIndex);
        }
        return new ArrayList<>(safeMessages.subList(startIndex, safeMessages.size()));
    }

    private int adjustStartIndexForToolPairs(List<BaseMessage> messages, int startIndex) {
        if (startIndex <= 0 || startIndex >= messages.size()) {
            return startIndex;
        }
        int adjusted = startIndex;
        Set<String> neededToolIds = new LinkedHashSet<>();
        for (BaseMessage message : messages.subList(startIndex, messages.size())) {
            if (message instanceof ToolMessage toolMessage
                    && toolMessage.getToolCallId() != null
                    && !toolMessage.getToolCallId().isBlank()) {
                neededToolIds.add(toolMessage.getToolCallId());
            }
        }
        if (neededToolIds.isEmpty()) {
            return adjusted;
        }
        Set<String> presentToolCalls = new HashSet<>();
        for (BaseMessage message : messages.subList(startIndex, messages.size())) {
            if (message instanceof AssistantMessage assistantMessage && assistantMessage.getToolCalls() != null) {
                for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                    if (toolCall.getId() != null && !toolCall.getId().isBlank()) {
                        presentToolCalls.add(toolCall.getId());
                    }
                }
            }
        }
        Set<String> missingToolCalls = new LinkedHashSet<>(neededToolIds);
        missingToolCalls.removeAll(presentToolCalls);
        if (missingToolCalls.isEmpty()) {
            return adjusted;
        }
        for (int index = startIndex - 1; index >= 0; index--) {
            BaseMessage message = messages.get(index);
            if (!(message instanceof AssistantMessage assistantMessage) || assistantMessage.getToolCalls() == null) {
                continue;
            }
            boolean matched = false;
            for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                if (missingToolCalls.remove(toolCall.getId())) {
                    matched = true;
                }
            }
            if (matched) {
                adjusted = index;
            }
            if (missingToolCalls.isEmpty()) {
                break;
            }
        }
        return adjusted;
    }

    private String generateSummary(List<BaseMessage> messages, SessionModelContext context) {
        if (model == null) {
            return buildFallbackSummary(messages);
        }
        List<BaseMessage> promptMessages = List.of(
                new SystemMessage(BASE_COMPACT_PROMPT),
                new UserMessage(serializeMessages(messages)));
        try {
            AssistantMessage response = model.invoke(promptMessages).toCompletableFuture().join();
            recordCompressionUsage(response);
            String content = response == null ? "" : response.getContentAsString().strip();
            if (content.isBlank()) {
                return buildFallbackSummary(messages);
            }
            return formatSummary(content);
        } catch (RuntimeException ex) {
            return buildFallbackSummary(messages);
        }
    }

    private int countPromptTokens(List<BaseMessage> messages, SessionModelContext context) {
        List<BaseMessage> promptMessages = List.of(
                new SystemMessage(BASE_COMPACT_PROMPT),
                new UserMessage(serializeMessages(messages)));
        return countContextWindowTokens(List.of(), promptMessages, context);
    }

    private int countContextWindowTokens(List<BaseMessage> systemMessages, List<BaseMessage> contextMessages,
                                         SessionModelContext context) {
        List<BaseMessage> allMessages = new ArrayList<>(systemMessages == null ? List.of() : systemMessages);
        allMessages.addAll(contextMessages == null ? List.of() : contextMessages);
        if (context != null && context.tokenCounter() != null) {
            try {
                return context.tokenCounter().countTokens(allMessages);
            } catch (RuntimeException ignored) {
                // Python falls back to the local estimator when token counting fails.
            }
        }
        int total = 0;
        for (BaseMessage message : allMessages) {
            total += CompressorUtils.estimateContentTokens(message == null ? "" : message.getContent());
        }
        return total;
    }

    private String buildFallbackSummary(List<BaseMessage> messages) {
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        List<String> lines = new ArrayList<>();
        int start = Math.max(safeMessages.size() - 20, 0);
        int displayIndex = Math.max(safeMessages.size() - 19, 1);
        for (int index = start; index < safeMessages.size(); index++) {
            BaseMessage message = safeMessages.get(index);
            lines.add("[" + (displayIndex++) + "] " + message.getRole() + ": "
                    + CompressorUtils.messageToText(message));
        }
        return "Summary:\n" + String.join("\n", lines);
    }

    private String formatSummary(String content) {
        String stripped = ANALYSIS_BLOCK.matcher(content == null ? "" : content).replaceAll("").strip();
        Matcher matcher = SUMMARY_BLOCK.matcher(stripped);
        if (matcher.find()) {
            return "Summary:\n" + matcher.group(1).strip();
        }
        return stripped;
    }

    private String serializeMessages(List<BaseMessage> messages) {
        List<String> parts = new ArrayList<>();
        for (BaseMessage message : messages == null ? List.<BaseMessage>of() : messages) {
            parts.add(serializeMessage(message));
        }
        return String.join("\n", parts);
    }

    private String serializeMessage(BaseMessage message) {
        List<String> parts = new ArrayList<>();
        parts.add("role=" + (message == null ? "" : message.getRole()));
        if (message instanceof AssistantMessage assistantMessage && assistantMessage.getToolCalls() != null
                && !assistantMessage.getToolCalls().isEmpty()) {
            try {
                List<Map<String, Object>> serializedToolCalls = new ArrayList<>();
                for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                    Map<String, Object> toolCallMap = new LinkedHashMap<>();
                    toolCallMap.put("id", toolCall.getId());
                    toolCallMap.put("name", toolCall.getName());
                    toolCallMap.put("arguments", toolCall.getArguments());
                    toolCallMap.put("type", toolCall.getType());
                    serializedToolCalls.add(toolCallMap);
                }
                parts.add("tool_calls=" + JSON_MAPPER.writeValueAsString(serializedToolCalls));
            } catch (JsonProcessingException ignored) {
                parts.add("tool_calls=[]");
            }
        }
        if (message instanceof ToolMessage toolMessage) {
            parts.add("tool_call_id=" + toolMessage.getToolCallId());
        }
        parts.add("content=" + CompressorUtils.messageToText(message));
        return String.join(" | ", parts);
    }

    private SplitMessages splitMessagesAtCompactionBoundary(List<BaseMessage> messages, int boundaryIndex) {
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        List<BaseMessage> prefix = boundaryIndex > 0
                ? new ArrayList<>(safeMessages.subList(0, boundaryIndex))
                : new ArrayList<>();
        List<BaseMessage> activeMessages = boundaryIndex >= 0
                ? new ArrayList<>(safeMessages.subList(boundaryIndex + 1, safeMessages.size()))
                : new ArrayList<>(safeMessages);
        return new SplitMessages(prefix, activeMessages);
    }

    private List<BaseMessage> stripMediaMessages(List<BaseMessage> messages) {
        return new ArrayList<>(messages == null ? List.of() : messages);
    }

    private List<BaseMessage> prepareMessagesForPrompt(List<BaseMessage> messages) {
        List<BaseMessage> result = new ArrayList<>();
        for (BaseMessage message : messages == null ? List.<BaseMessage>of() : messages) {
            if (isBoundaryMessage(message) || isStateMessage(message) || isSessionMemoryBoundaryMessage(message)) {
                continue;
            }
            result.add(message);
        }
        return result;
    }

    private String buildSessionMemoryMessage(String sessionMemoryText, boolean hasPreservedMessages) {
        List<String> parts = new ArrayList<>();
        parts.add(sessionMemoryIntro);
        parts.add("");
        parts.add(sessionMemoryText == null ? "" : sessionMemoryText.strip());
        if (hasPreservedMessages) {
            parts.add("");
            parts.add(recentMessagesNotice);
        }
        return String.join("\n", parts);
    }

    private String buildSummaryMessage(String summary, boolean hasPreservedMessages) {
        List<String> parts = new ArrayList<>();
        parts.add(summaryIntro);
        parts.add("");
        parts.add(summary == null ? "" : summary);
        if (hasPreservedMessages) {
            parts.add("");
            parts.add(recentMessagesNotice);
        }
        return String.join("\n", parts);
    }

    private Map<String, Object> loadSessionMemoryRuntime(SessionModelContext context) {
        SessionMemorySupport.SessionStatePort session = sessionStatePort(context == null ? null : context.getSessionRef());
        return SessionMemorySupport.getSessionMemoryRuntime(session);
    }

    private String loadSessionMemoryText(SessionModelContext context, Map<String, Object> runtime) {
        Path path = resolveSessionMemoryPath(runtime);
        if (path == null) {
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8).strip();
        } catch (IOException ignored) {
            return "";
        }
    }

    private Path resolveSessionMemoryPath(Map<String, Object> runtime) {
        String memoryPath = stringValue(runtime == null ? null : runtime.get("memory_path"));
        if (memoryPath == null || memoryPath.isBlank()) {
            return null;
        }
        try {
            return Path.of(memoryPath);
        } catch (InvalidPathException ex) {
            return null;
        }
    }

    private void invalidateSessionMemoryAnchor(SessionModelContext context) {
        SessionMemorySupport.invalidateSessionMemoryAnchor(
                sessionStatePort(context == null ? null : context.getSessionRef()));
    }

    private SessionMemorySupport.SessionStatePort sessionStatePort(Object sessionRef) {
        if (sessionRef instanceof SessionMemorySupport.SessionStatePort port) {
            return port;
        }
        if (sessionRef == null) {
            return null;
        }
        return new ReflectiveSessionStatePort(sessionRef);
    }

    private Map<String, Object> loadWholeSessionState(SessionModelContext context) {
        Object sessionRef = context == null ? null : context.getSessionRef();
        if (sessionRef == null) {
            return Map.of();
        }
        try {
            Method method = sessionRef.getClass().getMethod("getState");
            Object value = method.invoke(sessionRef);
            if (value instanceof Map<?, ?> rawMap) {
                return toStringObjectMap(rawMap);
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to the session-memory scoped state.
        }
        if (sessionRef instanceof SessionMemorySupport.SessionStatePort port) {
            Object value = port.getState("__session_memory__");
            if (value instanceof Map<?, ?> rawMap) {
                return toStringObjectMap(rawMap);
            }
        }
        return Map.of();
    }

    private UserMessage makeStateMessage(String label, String content) {
        return new UserMessage(stateMarker + "\n[" + label + "]\n" + truncateStateText(content));
    }

    private int findLastCompactionBoundaryIndex(List<BaseMessage> messages) {
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        for (int index = safeMessages.size() - 1; index >= 0; index--) {
            if (isBoundaryMessage(safeMessages.get(index)) || isSessionMemoryBoundaryMessage(safeMessages.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private boolean isBoundaryMessage(BaseMessage message) {
        return message instanceof SystemMessage && message.getContent() instanceof String text
                && text.startsWith(marker);
    }

    private boolean isStateMessage(BaseMessage message) {
        return message instanceof UserMessage && message.getContent() instanceof String text
                && text.startsWith(stateMarker);
    }

    private boolean isSessionMemoryBoundaryMessage(BaseMessage message) {
        return message instanceof SystemMessage && message.getContent() instanceof String text
                && text.startsWith(sessionMemoryMarker);
    }

    private boolean isSessionMemorySummaryMessage(BaseMessage message) {
        return message instanceof UserMessage && message.getContent() instanceof String text
                && text.startsWith(sessionMemoryIntro);
    }

    private boolean isSyntheticMarkerMessage(BaseMessage message) {
        return message instanceof UserMessage && syntheticUserMarker.equals(message.getContent());
    }

    private String buildHeadTailTruncatedText(String text, int keptChars) {
        if (keptChars <= 0) {
            return "...[TRUNCATED]...";
        }
        int headChars = Math.max((int) (keptChars * 0.2d), 0);
        int tailChars = Math.max(keptChars - headChars, 0);
        String head = text.substring(0, Math.min(headChars, text.length()));
        String tail = tailChars > 0 && text.length() > tailChars ? text.substring(text.length() - tailChars) : "";
        if (!head.isBlank() && !tail.isBlank()) {
            return head + "\n...[TRUNCATED]...\n" + tail;
        }
        return !head.isBlank() ? head : (!tail.isBlank() ? tail : "...[TRUNCATED]...");
    }

    private List<BaseMessage> flatten(List<List<BaseMessage>> groups) {
        List<BaseMessage> result = new ArrayList<>();
        for (List<BaseMessage> group : groups) {
            result.addAll(group);
        }
        return result;
    }

    private List<Integer> indexRange(int startInclusive, int endInclusive) {
        List<Integer> indices = new ArrayList<>();
        for (int index = startInclusive; index <= endInclusive; index++) {
            indices.add(index);
        }
        return indices;
    }

    private static FullCompactProcessorConfig asConfig(Object config) {
        if (config == null) {
            return new FullCompactProcessorConfig();
        }
        if (config instanceof FullCompactProcessorConfig fullCompactConfig) {
            return fullCompactConfig;
        }
        throw new IllegalArgumentException("FullCompactProcessor requires FullCompactProcessorConfig");
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }

    private static String stringValueOrDefault(Object value, String fallback) {
        String text = stringValue(value);
        return text == null ? fallback : text;
    }

    record SplitMessages(List<BaseMessage> prefix, List<BaseMessage> activeMessages) {
    }

    record ReplacementResult(List<BaseMessage> messages, String compactSummary, UserMessage sessionMemoryMessage) {
    }

    record FullCompactReplacement(List<BaseMessage> messages, String compactSummary) {
    }

    record SessionMemoryReplacement(List<BaseMessage> messages, UserMessage sessionMemoryMessage) {
    }

    private static final class ReflectiveSessionStatePort implements SessionMemorySupport.SessionStatePort {
        private final Object target;

        private ReflectiveSessionStatePort(Object target) {
            this.target = target;
        }

        @Override
        public Object getState(String key) {
            return invoke("getState", key, "get_state");
        }

        @Override
        public void updateState(Map<String, Object> update) {
            invoke("updateState", update, "update_state");
        }

        @Override
        public String getSessionId() {
            Object value = invoke("getSessionId", null, "get_session_id");
            return value instanceof String text ? text : "";
        }

        private Object invoke(String javaName, Object argument, String pythonStyleName) {
            for (String methodName : List.of(javaName, pythonStyleName)) {
                try {
                    Method method = argument == null
                            ? target.getClass().getMethod(methodName)
                            : target.getClass().getMethod(methodName, argument.getClass());
                    return argument == null ? method.invoke(target) : method.invoke(target, argument);
                } catch (NoSuchMethodException ignored) {
                    // Try the next method spelling.
                } catch (IllegalAccessException | InvocationTargetException ignored) {
                    return null;
                }
            }
            return null;
        }
    }
}
