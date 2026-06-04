/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Offloads large messages by trimming their content and storing the originals
 * in the offload buffer.
 * <p>
 * Mirrors Python's {@code MessageOffloader} from
 * {@code processor/offloader/message_offloader.py}.
 */
public class MessageOffloader extends ContextProcessor {

    private static final String OMIT_STRING = "...";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public MessageOffloader(MessageOffloaderConfig config) {
        super(config);
        validateConfig();
    }

    @Override
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
            if (!hasOffloadCandidate(allMessages)) {
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
            if (!hasOffloadCandidate(allMessages)) {
                return false;
            }
            Loggers.CONTEXT_ENGINE.info("[" + processorType() + " triggered] context tokens "
                    + tokens + " exceeds threshold of " + config.getTokensThreshold());
            return true;
        }
        return false;
    }

    @Override
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

    @Override
    public void loadState(Map<String, Object> state) {
        // stateless
    }

    @Override
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

        Map<String, Object> extraFields = extractExtraFields(message);
        return offloadMessages(message.getRole(), trimmedContent, List.of(message), context,
                null, "in_memory", extraFields);
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

    protected void validateConfig() {
        MessageOffloaderConfig config = getConfig();
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

    private OffloadResult offloadLargeMessages(List<BaseMessage> messages, ModelContext context) {
        List<BaseMessage> processedMessages = new ArrayList<>(messages);
        int offloadRange = getOffloadRange(messages);

        ContextEvent event = ContextEvent.builder().eventType(processorType()).build();

        for (int idx = offloadRange - 1; idx >= 0; idx--) {
            BaseMessage msg = processedMessages.get(idx);
            if (!shouldOffloadMessage(msg, processedMessages)) {
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

    private int getOffloadRange(List<BaseMessage> messages) {
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

    private boolean hasOffloadCandidate(List<BaseMessage> messages) {
        int offloadRange = getOffloadRange(messages);
        for (int idx = offloadRange - 1; idx >= 0; idx--) {
            if (shouldOffloadMessage(messages.get(idx), messages)) {
                return true;
            }
        }
        return false;
    }

    protected boolean shouldOffloadMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        MessageOffloaderConfig config = getConfig();
        if (!config.getOffloadMessageType().contains(message.getRole())) {
            return false;
        }
        if (!(message.getContent() instanceof String content)) {
            return false;
        }
        if (content.length() <= config.getLargeMessageThreshold()) {
            return false;
        }
        if (message instanceof OffloadMixin) {
            return false;
        }
        return !isProtectedToolMessage(message, contextMessages);
    }

    protected boolean isProtectedToolMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        if (!(message instanceof ToolMessage)) {
            return false;
        }
        Object toolCall = resolveToolCallFromMessage(message, contextMessages);
        if (toolCall == null) {
            return false;
        }

        String toolName = extractToolName(toolCall);
        if (toolName == null) {
            return false;
        }
        Map<String, Object> toolArgs = extractToolArgs(toolCall);
        MessageOffloaderConfig config = getConfig();
        for (String protectedTool : config.getProtectedToolNames()) {
            if (protectedTool == null || protectedTool.isEmpty()) {
                continue;
            }
            int separator = protectedTool.indexOf(':');
            if (separator >= 0) {
                String protectedName = protectedTool.substring(0, separator);
                String protectedPattern = protectedTool.substring(separator + 1);
                if (toolName.equals(protectedName) && matchPattern(toolArgs, protectedPattern)) {
                    return true;
                }
            } else if (toolName.equals(protectedTool)) {
                return true;
            }
        }
        return false;
    }

    protected Object resolveToolCallFromMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        if (!(message instanceof ToolMessage toolMessage)) {
            return null;
        }
        String toolCallId = toolMessage.getToolCallId();
        if (toolCallId == null || toolCallId.isEmpty()) {
            return null;
        }
        for (int i = contextMessages.size() - 1; i >= 0; i--) {
            BaseMessage contextMessage = contextMessages.get(i);
            if (!(contextMessage instanceof AssistantMessage assistant)
                    || assistant.getToolCalls() == null) {
                continue;
            }
            for (Object toolCall : assistant.getToolCalls()) {
                if (toolCallMatchesId(toolCall, toolCallId)) {
                    return toolCall;
                }
            }
        }
        return null;
    }

    private static boolean toolCallMatchesId(Object toolCall, String toolCallId) {
        if (toolCall instanceof ToolCall typedToolCall) {
            return toolCallId.equals(typedToolCall.getId());
        }
        if (toolCall instanceof Map<?, ?> map) {
            return toolCallId.equals(map.get("id"));
        }
        Object id = readValue(toolCall, "id");
        return toolCallId.equals(id);
    }

    private static String extractToolName(Object toolCall) {
        if (toolCall instanceof ToolCall typedToolCall) {
            return blankToNull(typedToolCall.getName());
        }
        Object function = readValue(toolCall, "function");
        if (function != null) {
            Object nestedName = readValue(function, "name");
            if (nestedName instanceof String s && !s.isEmpty()) {
                return s;
            }
        }
        Object name = readValue(toolCall, "name");
        return name instanceof String s && !s.isEmpty() ? s : null;
    }

    private static Map<String, Object> extractToolArgs(Object toolCall) {
        if (toolCall instanceof ToolCall typedToolCall) {
            return normalizeToolArgs(typedToolCall.getArguments());
        }
        Object function = readValue(toolCall, "function");
        if (function != null) {
            Object nestedArgs = readValue(function, "arguments");
            if (nestedArgs != null) {
                return normalizeToolArgs(nestedArgs);
            }
        }
        return normalizeToolArgs(readValue(toolCall, "arguments"));
    }

    private static Map<String, Object> normalizeToolArgs(Object rawArgs) {
        if (rawArgs instanceof Map<?, ?> rawMap) {
            Map<String, Object> args = new HashMap<>();
            rawMap.forEach((key, value) -> args.put(String.valueOf(key), value));
            return args;
        }
        if (rawArgs instanceof String argsString && !argsString.isBlank()) {
            try {
                return OBJECT_MAPPER.readValue(argsString, new TypeReference<>() {
                });
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private static Object readValue(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(fieldName);
        }
        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        try {
            Method getter = target.getClass().getMethod(getterName);
            return getter.invoke(target);
        } catch (Exception ignored) {
        }
        try {
            Field field = target.getClass().getField(fieldName);
            return field.get(target);
        } catch (Exception ignored) {
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean matchPattern(Map<String, Object> args, String pattern) {
        for (Object value : args.values()) {
            if (value instanceof String s && globMatches(s, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean globMatches(String value, String pattern) {
        return Pattern.compile(toRegex(pattern)).matcher(value).matches();
    }

    private static String toRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> {
                    if ("\\.[]{}()+-^$|".indexOf(ch) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(ch);
                }
            }
        }
        return regex.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
