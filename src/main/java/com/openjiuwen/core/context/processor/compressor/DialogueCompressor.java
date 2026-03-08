/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.JsonOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Compresses completed dialogue rounds (user question → tool calls → assistant answer)
 * to keep context within budget.
 * <p>
 * Mirrors Python's {@code DialogueCompressor} from
 * {@code processor/compressor/dialogue_compressor.py}.
 */
public class DialogueCompressor extends ContextProcessor {

    private static final String DEFAULT_COMPRESSION_PROMPT = """
            You are a "tool-call compressor that relies solely on the original text". You have no knowledge base, cannot use common-sense reasoning, and cannot infer or complete; you can only process the given text.
            
            Your task: Extract and compress the shortest information segment that fully answers the user's task requirements from the tool calls and tool responses.
            
            Rules:
            - Retain only task-relevant specific information points; delete all filler, examples, or duplicates.
            - Preserve business data structures (categories, differences, feature points) in natural language.
            - Do not omit any sub-question content.
            - Prefix the compressed content with: "Through <tool_name> tool, obtained: <compressed_text>".
            
            Output valid JSON:
            ```json
            {
                "summary": "<compressed_text>"
            }
            ```
            """;

    private final String compressedPrompt;
    private final int tokenThreshold;
    private final Integer messageNumThreshold;
    private final Integer messagesToKeep;
    private final Model model;

    public DialogueCompressor(DialogueCompressorConfig config) {
        super(config);
        this.compressedPrompt = config.getCustomizedCompressionPrompt() != null
                ? config.getCustomizedCompressionPrompt()
                : DEFAULT_COMPRESSION_PROMPT;
        this.tokenThreshold = config.getTokensThreshold();
        this.messageNumThreshold = config.getMessagesThreshold();
        this.messagesToKeep = config.getMessagesToKeep();
        this.model = config.getModelClient() != null ? new Model(config.getModelClient(), config.getModel()) : null;
    }

    @Override
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        DialogueCompressorConfig config = getConfig();
        int messageSize = context.size() + messagesToAdd.size();

        if (messageNumThreshold != null && messageSize > messageNumThreshold) {
            Loggers.CONTEXT_ENGINE.info("[" + processorType() + " triggered] context messages num "
                    + messageSize + " exceeds threshold of " + config.getMessagesThreshold());
            return true;
        }

        if (messagesToKeep != null && messageSize < messagesToKeep) {
            return false;
        }

        TokenCounter tokenCounter = context.tokenCounter();
        int tokens = 0;
        if (tokenCounter != null) {
            int contextTokens = tokenCounter.countMessages(context.getMessages());
            int addTokens = tokenCounter.countMessages(messagesToAdd);
            tokens = contextTokens + addTokens;
        }
        if (tokens > tokenThreshold) {
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

        int compressedIdx = getCompressIdx(contextMessages);
        if (compressedIdx == -1) {
            return ProcessResult.ofMessages(null, messagesToAdd);
        }

        List<int[]> msgPairs = getCompressPairs(contextMessages.subList(0, compressedIdx));
        if (msgPairs.isEmpty()) {
            return ProcessResult.ofMessages(null, messagesToAdd);
        }

        ContextEvent event = ContextEvent.builder().eventType(processorType()).build();

        // Process pairs in reverse order to maintain correct indices
        for (int i = msgPairs.size() - 1; i >= 0; i--) {
            int[] pair = msgPairs.get(i);
            int startIdx = pair[0] + 1;
            int endIdx = pair[1];

            List<BaseMessage> dialogues = new ArrayList<>();
            for (int j = startIdx; j <= endIdx; j++) {
                dialogues.add(contextMessages.get(j));
            }

            BaseMessage compressed = compress(dialogues, context);
            if (compressed != null) {
                event.getMessagesToModify().addAll(
                        IntStream.range(startIdx, endIdx).boxed().toList());
                contextMessages = ContextUtils.replaceMessages(
                        contextMessages, List.of(compressed), startIdx, endIdx);
            }
        }

        context.setMessages(contextMessages);
        return ProcessResult.ofMessages(null, Collections.emptyList());
    }

    @Override
    public void loadState(Map<String, Object> state) {
        // stateless
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of();
    }

    // ==================== Private Helpers ====================

    private int getCompressIdx(List<BaseMessage> messages) {
        DialogueCompressorConfig config = getConfig();
        Integer lastAiMsgIndex = null;
        if (config.isKeepLastRound()) {
            lastAiMsgIndex = ContextUtils.findLastAiMessageWithoutToolCall(messages).orElse(null);
        }
        int keepIndex = messagesToKeep == null
                ? messages.size()
                : messages.size() - messagesToKeep;
        return lastAiMsgIndex == null
                ? keepIndex
                : Math.min(lastAiMsgIndex, keepIndex);
    }

    /**
     * Find contiguous user→assistant pairs (where assistant has no tool_calls)
     * with intervening tool messages between them.
     *
     * @return list of [userIdx, assistantIdx] pairs
     */
    static List<int[]> getCompressPairs(List<BaseMessage> messages) {
        int currentUser = -1;
        List<int[]> result = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            BaseMessage msg = messages.get(i);
            if (msg instanceof UserMessage) {
                currentUser = i;
            } else if (msg instanceof AssistantMessage am
                    && (am.getToolCalls() == null || am.getToolCalls().isEmpty())
                    && currentUser != -1) {
                if (i - currentUser > 1) {
                    result.add(new int[]{currentUser, i});
                    currentUser = -1;
                }
            }
        }
        return result;
    }

    private BaseMessage compress(List<BaseMessage> messagesToCompress, ModelContext context) {
        try {
            List<BaseMessage> messages = new ArrayList<>();
            messages.add(new SystemMessage(compressedPrompt));
            messages.addAll(messagesToCompress);

            AssistantMessage response = model.invoke(
                    messages, null, null, null, null, null, null,
                    new JsonOutputParser(), null, null);

            Object parserContent = response.getParserContent();
            if (parserContent instanceof Map<?, ?> summaryMap) {
                Object summaryObj = summaryMap.get("summary");
                String summary = summaryObj != null ? String.valueOf(summaryObj) : "";
                if (!summary.isEmpty()) {
                    return offloadMessages("assistant", summary, messagesToCompress, context);
                }
            }
            return null;
        } catch (Exception e) {
            Loggers.CONTEXT_ENGINE.warning("Dialogue compression failed: " + e.getMessage());
            return null;
        }
    }
}
