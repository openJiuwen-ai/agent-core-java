/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.schema.OffloadMixin;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Offloads large messages by trimming their content and storing the originals
 * in the offload buffer.
 * <p>
 * Mirrors Python's {@code MessageOffloader} from
 * {@code processor/offloader/message_offloader.py}.
 */
public class MessageOffloader extends ContextProcessor {

    private static final String OMIT_STRING = "...";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Auto-generated for codecheck compliance.
     */
    public MessageOffloader(MessageOffloaderConfig config) {
        super(config);
        validateConfig();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        MessageOffloaderConfig config = getConfig();
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        allMessages.addAll(messagesToAdd);
        int messageSize = allMessages.size();

        // Skip if total length is below the keep-floor
        if (config.getMessagesToKeep() != null && messageSize <= config.getMessagesToKeep()) {
            return false;
        }

        // Trigger when message count exceeds hard ceiling
        if (config.getMessagesThreshold() != null && messageSize > config.getMessagesThreshold()) {
            if (!hasOffloadCandidate(allMessages, context)) {
                return false;
            }
            Loggers.CONTEXT_ENGINE.info("[" + processorType() + " triggered] context messages num "
                    + messageSize + " exceeds threshold of " + config.getMessagesThreshold());
            return true;
        }

        // Fall back to token budget
        TokenCounter tokenCounter = context.tokenCounter();
        int tokens = 0;
        if (tokenCounter != null) {
            int contextToken = tokenCounter.countMessages(context.getMessages());
            int addToken = tokenCounter.countMessages(messagesToAdd);
            tokens = contextToken + addToken;
        }
        if (tokens > config.getTokensThreshold()) {
            if (!hasOffloadCandidate(allMessages, context)) {
                return false;
            }
            Loggers.CONTEXT_ENGINE.info("[" + processorType() + " triggered] context tokens "
                    + tokens + " exceeds threshold of " + config.getTokensThreshold());
            return true;
        }
        return false;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
        contextMessages.addAll(messagesToAdd);
        int contextSize = context.size();

        OffloadResult result = offloadLargeMessages(contextMessages, context);
        List<BaseMessage> processedMessages = result.messages;

        List<BaseMessage> newContextMessages = new ArrayList<>(processedMessages.subList(0, contextSize));
        List<BaseMessage> newMessagesToAdd = new ArrayList<>(
                processedMessages.subList(contextSize, processedMessages.size()));

        context.setMessages(newContextMessages);
        return ProcessResult.ofMessages(result.event, newMessagesToAdd);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void loadState(Map<String, Object> state) {
        // stateless
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> saveState() {
        return new HashMap<>();
    }

    // ==================== Protected for subclass override ====================

    /**
     * Offload a single message. Can be overridden by subclasses (e.g., MessageSummaryOffloader).
     */
    protected BaseMessage offloadMessage(BaseMessage message, ModelContext context) {
        MessageOffloaderConfig config = getConfig();
        String content = message.getContentAsString();
        String trimmedContent = content.substring(0, Math.min(content.length(), config.getTrimSize())) + OMIT_STRING;
        OffloadTarget offloadTarget = newOffloadTarget(context);
        Map<String, Object> extraFields = extractExtraFields(message);
        return offloadMessages(
                message.getRole(),
                trimmedContent,
                List.of(message),
                context,
                offloadTarget.handle(),
                offloadTarget.path() != null ? "filesystem" : "in_memory",
                offloadTarget.path(),
                extraFields
        );
    }

    /**
     * Extract extra fields from a message for preservation during offload.
     * Mirrors Python's {@code message.model_dump()} with role/content removed.
     */
    protected static Map<String, Object> extractExtraFields(BaseMessage message) {
        Map<String, Object> extraFields = new HashMap<>();
        if (message.getName() != null) {
            extraFields.put("name", message.getName());
        }
        if (message instanceof ToolMessage toolMsg && toolMsg.getToolCallId() != null) {
            extraFields.put("tool_call_id", toolMsg.getToolCallId());
        }
        if (message instanceof AssistantMessage assistantMsg) {
            if (assistantMsg.getToolCalls() != null) {
                extraFields.put("tool_calls", assistantMsg.getToolCalls());
            }
            if (assistantMsg.getUsageMetadata() != null) {
                extraFields.put("usage_metadata", assistantMsg.getUsageMetadata());
            }
            if (assistantMsg.getFinishReason() != null) {
                extraFields.put("finish_reason", assistantMsg.getFinishReason());
            }
            if (assistantMsg.getParserContent() != null) {
                extraFields.put("parser_content", assistantMsg.getParserContent());
            }
            if (assistantMsg.getReasoningContent() != null) {
                extraFields.put("reasoning_content", assistantMsg.getReasoningContent());
            }
        }
        return extraFields;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected void validateConfig() {
        MessageOffloaderConfig config = getConfig();
        config.validate();
        if (config.getTrimSize() >= config.getLargeMessageThreshold()) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg", "trim_size " + config.getTrimSize()
                            + " cannot larger than large_message_threshold " + config.getLargeMessageThreshold());
        }
        if (config.getMessagesToKeep() != null
                && config.getMessagesThreshold() != null
                && config.getMessagesToKeep() >= config.getMessagesThreshold()) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg", "messages_to_keep " + config.getMessagesToKeep()
                            + " cannot larger than messages_threshold " + config.getMessagesThreshold());
        }
    }

    // ==================== Private Helpers ====================

    private record OffloadResult(ContextEvent event, List<BaseMessage> messages) {
    }

    protected record OffloadTarget(String handle, String path) {
    }

    private OffloadResult offloadLargeMessages(List<BaseMessage> messages, ModelContext context) {
        List<BaseMessage> processedMessages = new ArrayList<>(messages);
        int offloadRange = getOffloadRange(messages);

        ContextEvent event = ContextEvent.builder().eventType(processorType()).build();

        for (int idx = offloadRange - 1; idx >= 0; idx--) {
            BaseMessage msg = processedMessages.get(idx);
            if (!shouldOffloadMessage(msg, processedMessages, context)) {
                continue;
            }

            BaseMessage offloadMsg = offloadMessage(msg, context);
            if (offloadMsg != null) {
                processedMessages = ContextUtils.replaceMessages(
                        processedMessages, List.of(offloadMsg), idx, idx);
                event.getMessagesToModify().add(idx);
            }
        }

        return new OffloadResult(event, processedMessages);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected int getOffloadRange(List<BaseMessage> messages) {
        MessageOffloaderConfig config = getConfig();
        Integer lastAiMsgIndex = null;
        if (config.isKeepLastRound()) {
            lastAiMsgIndex = ContextUtils.findLastAiMessageWithoutToolCall(messages).orElse(null);
        }
        int keepIndex = config.getMessagesToKeep() == null
                ? messages.size()
                : messages.size() - config.getMessagesToKeep();
        return lastAiMsgIndex == null ? keepIndex : Math.min(lastAiMsgIndex, keepIndex);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected boolean hasOffloadCandidate(List<BaseMessage> messages, ModelContext context) {
        int offloadRange = getOffloadRange(messages);
        for (int idx = offloadRange - 1; idx >= 0; idx--) {
            if (shouldOffloadMessage(messages.get(idx), messages, context)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected boolean shouldOffloadMessage(
            BaseMessage message,
            List<BaseMessage> contextMessages,
            ModelContext context) {
        MessageOffloaderConfig config = getConfig();
        if (!config.getOffloadMessageType().contains(message.getRole())) {
            return false;
        }
        String content = message.getContentAsString();
        if (content == null || content.length() <= config.getLargeMessageThreshold()) {
            return false;
        }
        if (message instanceof OffloadMixin) {
            return false;
        }
        return !isProtectedToolMessage(message, contextMessages);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected boolean isProtectedToolMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        if (!(message instanceof ToolMessage)) {
            return false;
        }
        MessageOffloaderConfig config = getConfig();
        List<String> protectedToolNames = config.getProtectedToolNames();
        if (protectedToolNames == null || protectedToolNames.isEmpty()) {
            return false;
        }
        ToolCall toolCall = ContextUtils.resolveToolCallFromMessage(message, contextMessages);
        if (toolCall == null) {
            return false;
        }
        String toolName = ContextUtils.extractToolName(toolCall);
        Map<String, Object> toolArgs = extractToolArgs(toolCall);
        for (String protectedTool : protectedToolNames) {
            if (protectedTool == null || protectedTool.isBlank()) {
                continue;
            }
            int separatorIndex = protectedTool.indexOf(':');
            if (separatorIndex >= 0) {
                String protectedName = protectedTool.substring(0, separatorIndex);
                String protectedPattern = protectedTool.substring(separatorIndex + 1);
                if (protectedName.equals(toolName) && matchPattern(toolArgs, protectedPattern)) {
                    return true;
                }
                continue;
            }
            if (protectedTool.equals(toolName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected OffloadTarget newOffloadTarget(ModelContext context) {
        String offloadHandle = UUID.randomUUID().toString().replace("-", "");
        String workspaceDir = context.workspaceDir();
        if (workspaceDir == null || workspaceDir.isBlank()) {
            return new OffloadTarget(offloadHandle, null);
        }
        String fileName = processorType() + "_" + offloadHandle + ".json";
        String offloadPath = java.nio.file.Path.of(
                workspaceDir,
                "context",
                context.sessionId() + "_context",
                "offload",
                fileName
        ).toString();
        return new OffloadTarget(offloadHandle, offloadPath);
    }

    private static Map<String, Object> extractToolArgs(ToolCall toolCall) {
        if (toolCall == null || toolCall.getArguments() == null || toolCall.getArguments().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(toolCall.getArguments(), new TypeReference<>() {});
        } catch (JsonProcessingException ignored) {
            return Collections.emptyMap();
        }
    }

    private static boolean matchPattern(Map<String, Object> args, String pattern) {
        if (args == null || args.isEmpty() || pattern == null || pattern.isBlank()) {
            return false;
        }
        for (Object value : args.values()) {
            if (value instanceof String stringValue && globMatches(stringValue, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean globMatches(String value, String pattern) {
        try {
            return FileSystems.getDefault().getPathMatcher("glob:" + pattern)
                    .matches(java.nio.file.Path.of(value));
        } catch (InvalidPathException ignored) {
            return false;
        }
    }
}
