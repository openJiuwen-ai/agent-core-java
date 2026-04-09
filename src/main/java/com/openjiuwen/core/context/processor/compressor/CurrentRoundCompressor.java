  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
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
 * Compresses messages within the current dialogue round to stay within
 * token or message-count budgets.
 * <p>
 * Mirrors Python's {@code CurrentRoundCompressor} from
 * {@code processor/compressor/current_round_compressor.py}.
 */
public class CurrentRoundCompressor extends ContextProcessor {

    private static final String DEFAULT_COMPRESSION_PROMPT = """
            You are a Context-Refinement Assistant.
            Your task: compress the following message(s) into **≤ 30% of original length** while preserving all essential facts, decisions, constraints, and tool execution results needed for future replies.
            
            ---
            
            📌 Input Format Explanation:
            Each message follows the format: "role:<role_type>, content:<actual_content>"
            - role=assistant: The AI's response/answer
            - role=tool: The result of a tool execution
            
            Your task is to:
            1. Identify the actual content within each message (ignore the "role:" prefix)
            2. Compress and summarize the content(s) only
            3. For tool messages: Explicitly state what tool was called and what result was obtained
            
            ---
            
            📌 Compression Rules (mandatory):
            - Use natural language; preserve business data structures (categories, differences, features)
            - Keep **all task-relevant specific points** (requirements, constraints, parameters, decisions)
            - For tool calls: MUST preserve the tool name, input parameters, and the result/output
            - For multiple messages: Maintain causal relationships and chronological logic
            - Preserve any numerical values, IDs, configuration parameters, and business rules
            
            ---
            
            📌 Output Requirements:
            - Preserve key information, conclusions, decisions, and answers
            - For tool executions: Always include "Tool: <tool_name> → Result: <result_summary>"
            - If single message: Preserve key information, conclusions, and specific details
            - If multiple messages: Summarize the overall context, main decisions made, and any constraints established
            
            ---
            
            📌 Strict output format:
            - Valid JSON wrapped in ```json``` code block:
            ```json
            {
                "summary": "<refined_text>"
            }
            ```
            - The <refined_text> should be in natural language, NOT include any JSON syntax or markdown
            - Output MUST be exactly in the format above, no extra text outside the JSON
            """;

    private final String compressedPrompt;
    private final int tokenThreshold;
    private final Integer messageNumThreshold;
    private final Integer messagesToKeep;
    private final boolean singleMultiConfig;
    private final int largeMessageThreshold;
    private final Model model;

    public CurrentRoundCompressor(CurrentRoundCompressorConfig config) {
        super(config);
        this.compressedPrompt = config.getCustomizedCompressionPrompt() != null
                ? config.getCustomizedCompressionPrompt()
                : DEFAULT_COMPRESSION_PROMPT;
        this.tokenThreshold = config.getTokensThreshold();
        this.messageNumThreshold = config.getMessagesThreshold();
        this.messagesToKeep = config.getMessagesToKeep();
        this.singleMultiConfig = config.isSingleMultiCompression();
        this.largeMessageThreshold = config.getLargeMessageThreshold();
        this.model = config.getModelClient() != null ? new Model(config.getModelClient(), config.getModel()) : null;
    }

    @Override
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        CurrentRoundCompressorConfig config = getConfig();
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

        int lastUserIdx = getCompressIdx(contextMessages);
        if (lastUserIdx == -1) {
            return ProcessResult.ofMessages(null, messagesToAdd);
        }

        List<BaseMessage> messages = messagesToKeep != null
                ? contextMessages.subList(0, contextMessages.size() - messagesToKeep)
                : contextMessages;
        int endIdx = messages.size() - 1;

        ContextEvent event = ContextEvent.builder().eventType(processorType()).build();

        if (singleMultiConfig) {
            List<BaseMessage> compressed = multiCompress(contextMessages, lastUserIdx, endIdx, context);
            if (compressed != null) {
                event.setMessagesToModify(new ArrayList<>(
                        IntStream.range(lastUserIdx, endIdx).boxed().toList()));
                context.setMessages(compressed);
                return ProcessResult.ofMessages(event, Collections.emptyList());
            }
            return ProcessResult.ofMessages(null, messagesToAdd);
        } else {
            try {
                List<BaseMessage> compressed = singleCompress(contextMessages, lastUserIdx, endIdx, context);
                if (compressed != null) {
                    event.setMessagesToModify(new ArrayList<>(
                            IntStream.range(lastUserIdx, endIdx).boxed().toList()));
                    context.setMessages(compressed);
                    return ProcessResult.ofMessages(event, Collections.emptyList());
                }
                return ProcessResult.ofMessages(null, messagesToAdd);
            } catch (Exception e) {
                throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                        "error_msg", "compress messages failed: " + e.getMessage());
            }
        }
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
        int compressedIdx = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                compressedIdx = i;
                break;
            }
        }
        if (compressedIdx == messages.size() - 1) {
            return -1;
        }
        if (compressedIdx < 0) {
            return -1;
        }

        int keepIndex = messagesToKeep == null
                ? messages.size()
                : messages.size() - messagesToKeep;

        if (compressedIdx >= keepIndex) {
            return -1;
        }
        return compressedIdx;
    }

    private List<BaseMessage> multiCompress(
            List<BaseMessage> contextMessages,
            int lastUserIdx,
            int endIdx,
            ModelContext context) {

        int startIdx = lastUserIdx + 1;
        if (endIdx >= startIdx) {
            BaseMessage lastMsg = contextMessages.get(endIdx);
            if (lastMsg instanceof AssistantMessage am && am.getToolCalls() != null && !am.getToolCalls().isEmpty()) {
                endIdx = endIdx - 1;
            }
            if (endIdx < startIdx) {
                return null;
            }
        }

        List<BaseMessage> messagesToCompress = new ArrayList<>(
                contextMessages.subList(startIdx, endIdx + 1));
        BaseMessage compressed = compress(messagesToCompress, context);
        if (compressed != null) {
            return ContextUtils.replaceMessages(contextMessages, List.of(compressed), startIdx, endIdx);
        }
        return null;
    }

    private List<BaseMessage> singleCompress(
            List<BaseMessage> contextMessages,
            int lastUserIdx,
            int endIdx,
            ModelContext context) {

        int startIdx = lastUserIdx + 1;
        TokenCounter tokenCounter = context.tokenCounter();
        List<BaseMessage> result = new ArrayList<>(contextMessages);

        for (int idx = startIdx; idx <= endIdx; idx++) {
            BaseMessage msg = result.get(idx);
            if (msg instanceof AssistantMessage am && am.getToolCalls() != null && !am.getToolCalls().isEmpty()) {
                continue;
            }
            int contextToken = tokenCounter != null ? tokenCounter.countMessages(List.of(msg)) : 0;
            if (contextToken > largeMessageThreshold) {
                BaseMessage compressed = compress(List.of(msg), context);
                if (compressed != null) {
                    result = ContextUtils.replaceMessages(result, List.of(compressed), idx, idx);
                }
            }
        }
        return result;
    }

    private BaseMessage compress(List<BaseMessage> messagesToCompress, ModelContext context) {
        try {
            List<BaseMessage> processedMessages = new ArrayList<>();
            processedMessages.add(new SystemMessage(compressedPrompt));
            for (BaseMessage msg : messagesToCompress) {
                processedMessages.add(new UserMessage("role:" + msg.getRole()
                        + ", content:" + msg.getContentAsString()));
            }

            AssistantMessage response = model.invoke(
                    processedMessages, null, null, null, null, null, null,
                    new JsonOutputParser(), null, null);

            Object parserContent = response.getParserContent();
            if (parserContent instanceof Map<?, ?> summaryMap) {
                Object summaryObj = summaryMap.get("summary");
                String summary = summaryObj != null ? String.valueOf(summaryObj) : "";
                if (!summary.isEmpty()) {
                    return offloadMessages("user", summary, messagesToCompress, context);
                }
            }
            return null;
        } catch (Exception e) {
            Loggers.CONTEXT_ENGINE.warning("Compression failed: " + e.getMessage());
            return null;
        }
    }
}
