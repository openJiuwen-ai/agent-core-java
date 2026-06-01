/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.schema.OffloadMixin;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extends {@link MessageOffloader} to use an LLM for generating
 * summarized replacement content instead of simple trimming.
 * <p>
 * Mirrors Python's {@code MessageSummaryOffloader} from
 * {@code processor/offloader/message_summary_offloader.py}.
 */
public class MessageSummaryOffloader extends MessageOffloader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_OFFLOAD_SUMMARY_PROMPT = """
            You are a "high-density summarizer".
            Your task is to shrink the overly long message below into 2–4 concise sentences that:
            Contain ≤ 15 % of the original token count;
            Keep all critical facts, figures, conclusions, requests or decisions verbatim;
            Remove greetings, repetition, filler, examples, jokes, and ornamental language;
            Speak in neutral, third-person style;
            Do NOT explain, comment, or add extra information—output the summary only.
            Begin:
            """;

    private final Model model;
    private final MessageSummaryOffloaderConfig summaryConfig;

    public MessageSummaryOffloader(MessageSummaryOffloaderConfig config) {
        // Convert to parent config for parent constructor
        super(toOffloaderConfig(config));
        this.summaryConfig = config;
        this.model = config.getModelClient() != null ? new Model(config.getModelClient(), config.getModel()) : null;
        // Re-validate now that summaryConfig is assigned
        validateConfig();
    }

    @Override
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
        contextMessages.addAll(messagesToAdd);
        return messagesToAdd.stream().anyMatch(message -> shouldOffloadSummaryMessage(message, context, contextMessages));
    }

    @Override
    public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
        contextMessages.addAll(messagesToAdd);
        List<BaseMessage> processedMessages = new ArrayList<>(messagesToAdd);
        ContextEvent event = ContextEvent.builder().eventType(processorType()).build();
        int baseIndex = context.size();

        for (int index = 0; index < messagesToAdd.size(); index++) {
            BaseMessage message = messagesToAdd.get(index);
            if (!shouldOffloadSummaryMessage(message, context, contextMessages)) {
                continue;
            }
            BaseMessage offloaded = offloadMessage(message, context);
            if (offloaded != null) {
                processedMessages.set(index, offloaded);
                event.getMessagesToModify().add(baseIndex + index);
            }
        }
        if (event.getMessagesToModify().isEmpty()) {
            return ProcessResult.ofMessages(null, messagesToAdd);
        }
        return ProcessResult.ofMessages(event, processedMessages);
    }

    @Override
    protected BaseMessage offloadMessage(BaseMessage message, ModelContext context) {
        try {
            String prompt = summaryConfig.getCustomizedSummaryPrompt() != null
                    ? summaryConfig.getCustomizedSummaryPrompt()
                    : DEFAULT_OFFLOAD_SUMMARY_PROMPT;

            AssistantMessage response = model.invoke(
                    List.of(
                            new SystemMessage(prompt),
                            new UserMessage(message.getContentAsString())),
                    null, null, null, null, null, null, null, null, null);

            String summarizedContent = response.getContentAsString();
            Map<String, Object> extraFields = extractExtraFields(message);
            return offloadMessages(message.getRole(), summarizedContent, List.of(message), context,
                    null, "in_memory", extraFields);
        } catch (Exception e) {
            Loggers.CONTEXT_ENGINE.warning("Summary offload failed: " + e.getMessage());
            // Fall back to simple trim
            return super.offloadMessage(message, context);
        }
    }

    private boolean shouldOffloadSummaryMessage(
            BaseMessage message,
            ModelContext context,
            List<BaseMessage> contextMessages) {
        if (!summaryConfig.getOffloadMessageType().contains(message.getRole())) {
            return false;
        }
        if (message instanceof OffloadMixin) {
            return false;
        }
        if (isProtectedToolMessage(message, contextMessages)) {
            return false;
        }
        return messageSize(message, context) > summaryConfig.getLargeMessageThreshold();
    }

    private int messageSize(BaseMessage message, ModelContext context) {
        TokenCounter tokenCounter = context.tokenCounter();
        if (tokenCounter != null) {
            return tokenCounter.countMessages(List.of(message));
        }
        Object content = message.getContent();
        if (content instanceof String text) {
            return text.length() / 3;
        }
        try {
            return MAPPER.writeValueAsString(content).length() / 3;
        } catch (JsonProcessingException e) {
            return String.valueOf(content).length() / 3;
        }
    }

    @Override
    protected void validateConfig() {
        if (summaryConfig == null) {
            return; // Called from parent constructor before summaryConfig is assigned
        }
        if (summaryConfig.getMessagesToKeep() != null
                && summaryConfig.getMessagesThreshold() != null
                && summaryConfig.getMessagesToKeep() >= summaryConfig.getMessagesThreshold()) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg", "messages_to_keep " + summaryConfig.getMessagesToKeep()
                            + " cannot larger than messages_threshold " + summaryConfig.getMessagesThreshold());
        }
    }

    /**
     * Convert MessageSummaryOffloaderConfig to MessageOffloaderConfig for the parent class.
     */
    private static MessageOffloaderConfig toOffloaderConfig(MessageSummaryOffloaderConfig config) {
        return MessageOffloaderConfig.builder()
                .messagesThreshold(config.getMessagesThreshold())
                .tokensThreshold(config.getTokensThreshold())
                .largeMessageThreshold(config.getLargeMessageThreshold())
                .offloadMessageType(config.getOffloadMessageType())
                .protectedToolNames(config.getProtectedToolNames())
                .messagesToKeep(config.getMessagesToKeep())
                .keepLastRound(config.isKeepLastRound())
                .build();
    }
}
