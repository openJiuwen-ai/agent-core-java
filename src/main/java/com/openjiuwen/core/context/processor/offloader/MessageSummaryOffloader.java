/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.context.processor.offloader;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

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
                .messagesToKeep(config.getMessagesToKeep())
                .keepLastRound(config.isKeepLastRound())
                .build();
    }
}
