/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Truncate oversized {@link UserMessage} content in-place when the context
 * window exceeds the configured token budget.
 * <p>
 * This processor performs purely local, character-level truncation. It never
 * invokes an LLM and does not depend on tool calls or multi-round dialogue.
 * When triggered, each eligible {@code UserMessage} (and optionally each
 * {@code SystemMessage}) is replaced with a shorter copy that preserves the
 * first {@code preserveHeadChars} characters and the last
 * {@code preserveTailChars} characters of the original content, separated by
 * a marker describing how many characters were removed.
 *
 * @since 0.1.7
 */
public class PromptTruncationProcessor extends ContextProcessor {
    private static final int NO_MODIFICATION = 0;

    private final PromptTruncationProcessorConfig cfg;

    /**
     * PromptTruncationProcessor.
     *
     * @param config config
     * @since 0.1.7
     */
    public PromptTruncationProcessor(PromptTruncationProcessorConfig config) {
        super(config);
        PromptTruncationProcessorConfig effective = config != null ? config
                : PromptTruncationProcessorConfig.builder().build();
        effective.validate();
        this.cfg = effective;
    }

    /**
     * triggerGetContextWindow.
     *
     * @param context context
     * @param contextWindow contextWindow
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean triggerGetContextWindow(ModelContext context, ContextWindow contextWindow) {
        return countContextWindowTokens(contextWindow, context) > cfg.getMaxContextTokens();
    }

    /**
     * onGetContextWindow.
     *
     * @param context context
     * @param contextWindow contextWindow
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ProcessResult onGetContextWindow(ModelContext context, ContextWindow contextWindow) {
        int totalTokens = countContextWindowTokens(contextWindow, context);
        if (totalTokens <= cfg.getMaxContextTokens()) {
            return ProcessResult.ofContextWindow(null, contextWindow);
        }

        List<BaseMessage> originalMessages = contextWindow.getContextMessages();
        List<BaseMessage> updatedMessages = new ArrayList<>(originalMessages.size());
        List<Integer> modifiedIndices = new ArrayList<>();
        for (int index = 0; index < originalMessages.size(); index++) {
            BaseMessage message = originalMessages.get(index);
            BaseMessage truncated = maybeTruncate(message);
            if (truncated != message) {
                updatedMessages.add(truncated);
                modifiedIndices.add(index);
            } else {
                updatedMessages.add(message);
            }
        }

        if (modifiedIndices.isEmpty()) {
            return ProcessResult.ofContextWindow(null, contextWindow);
        }
        contextWindow.setContextMessages(updatedMessages);
        ContextEvent event = ContextEvent.builder()
                .eventType(processorType())
                .messagesToModify(modifiedIndices)
                .build();
        Loggers.CONTEXT_ENGINE.info("[{}] truncated {} user message(s), original_tokens={} budget={}",
                processorType(), modifiedIndices.size(), totalTokens, cfg.getMaxContextTokens());
        return ProcessResult.ofContextWindow(event, contextWindow);
    }

    /**
     * loadState.
     *
     * @param state state
     * @since 0.1.7
     */
    @Override
    public void loadState(Map<String, Object> state) {
        // stateless
    }

    /**
     * saveState.
     *
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> saveState() {
        return Map.of();
    }

    private BaseMessage maybeTruncate(BaseMessage message) {
        if (message instanceof UserMessage userMessage) {
            return truncateUserMessage(userMessage);
        }
        if (cfg.isTruncateSystemMessages() && message instanceof SystemMessage systemMessage) {
            return truncateSystemMessage(systemMessage);
        }
        return message;
    }

    private UserMessage truncateUserMessage(UserMessage original) {
        String content = original.getContentAsString();
        Optional<String> truncated = truncateContent(content);
        if (truncated.isEmpty()) {
            return original;
        }
        UserMessage copy = new UserMessage();
        copy.setRole(original.getRole());
        copy.setContent(truncated.get());
        copy.setName(original.getName());
        copy.setMetadata(copyMetadata(original.getMetadata()));
        return copy;
    }

    private SystemMessage truncateSystemMessage(SystemMessage original) {
        String content = original.getContentAsString();
        Optional<String> truncated = truncateContent(content);
        if (truncated.isEmpty()) {
            return original;
        }
        SystemMessage copy = new SystemMessage();
        copy.setRole(original.getRole());
        copy.setContent(truncated.get());
        copy.setName(original.getName());
        copy.setMetadata(copyMetadata(original.getMetadata()));
        return copy;
    }

    private Optional<String> truncateContent(String content) {
        if (content == null) {
            return Optional.empty();
        }
        int keepHead = cfg.getPreserveHeadChars();
        int keepTail = cfg.getPreserveTailChars();
        if (content.length() <= keepHead + keepTail) {
            return Optional.empty();
        }
        int removed = content.length() - keepHead - keepTail;
        String marker = buildMarker(removed);
        return Optional.of(content.substring(0, keepHead) + marker + content.substring(content.length() - keepTail));
    }

    private String buildMarker(int removed) {
        String template = cfg.getTruncatedMarker();
        int placeholder = template.indexOf("%d");
        if (placeholder < 0) {
            return template;
        }
        return String.format(Locale.ROOT, template, removed);
    }

    private static Map<String, Object> copyMetadata(Map<String, Object> source) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(source);
    }

    private int countContextWindowTokens(ContextWindow window, ModelContext context) {
        TokenCounter tokenCounter = context != null ? context.tokenCounter() : null;
        List<BaseMessage> allMessages = new ArrayList<>();
        if (window.getSystemMessages() != null) {
            allMessages.addAll(window.getSystemMessages());
        }
        if (window.getContextMessages() != null) {
            allMessages.addAll(window.getContextMessages());
        }
        if (tokenCounter != null) {
            try {
                int toolsTokens = window.getTools() != null
                        ? window.getTools().stream().mapToInt(ContextUtils::estimateTokens).sum()
                        : NO_MODIFICATION;
                return tokenCounter.countMessages(allMessages) + toolsTokens;
            } catch (IllegalStateException | IllegalArgumentException exception) {
                Loggers.CONTEXT_ENGINE.warning("[" + processorType() + "] token_counter failed, fallback to estimate: "
                        + exception.getMessage());
            }
        }
        int total = allMessages.stream().mapToInt(ContextUtils::estimateMessageTokens).sum();
        if (window.getTools() != null) {
            total += window.getTools().stream().mapToInt(ContextUtils::estimateTokens).sum();
        }
        return total;
    }
}
