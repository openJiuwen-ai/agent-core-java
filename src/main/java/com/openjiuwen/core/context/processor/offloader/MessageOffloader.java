  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.context.processor.offloader;

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
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Offloads large messages by trimming their content and storing the originals
 * in the offload buffer.
 * <p>
 * Mirrors Python's {@code MessageOffloader} from
 * {@code processor/offloader/message_offloader.py}.
 */
public class MessageOffloader extends ContextProcessor {

    private static final String OMIT_STRING = "...";

    public MessageOffloader(MessageOffloaderConfig config) {
        super(config);
        validateConfig();
    }

    @Override
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        MessageOffloaderConfig config = getConfig();
        int messageSize = context.size() + messagesToAdd.size();

        // Skip if total length is below the keep-floor
        if (config.getMessagesToKeep() != null && messageSize <= config.getMessagesToKeep()) {
            return false;
        }

        // Trigger when message count exceeds hard ceiling
        if (config.getMessagesThreshold() != null && messageSize > config.getMessagesThreshold()) {
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
        MessageOffloaderConfig config = getConfig();
        List<BaseMessage> processedMessages = new ArrayList<>(messages);

        Integer lastAiMsgIndex = null;
        if (config.isKeepLastRound()) {
            lastAiMsgIndex = ContextUtils.findLastAiMessageWithoutToolCall(messages).orElse(null);
        }
        int keepIndex = config.getMessagesToKeep() == null
                ? messages.size()
                : messages.size() - config.getMessagesToKeep();
        int offloadRange = lastAiMsgIndex == null
                ? keepIndex
                : Math.min(lastAiMsgIndex, keepIndex);

        ContextEvent event = ContextEvent.builder().eventType(processorType()).build();

        for (int idx = offloadRange - 1; idx >= 0; idx--) {
            BaseMessage msg = processedMessages.get(idx);

            // Skip if not eligible
            if (!config.getOffloadMessageType().contains(msg.getRole())) {
                continue;
            }
            String content = msg.getContentAsString();
            if (content == null || content.length() <= config.getLargeMessageThreshold()) {
                continue;
            }
            if (msg instanceof OffloadMixin) {
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
}
