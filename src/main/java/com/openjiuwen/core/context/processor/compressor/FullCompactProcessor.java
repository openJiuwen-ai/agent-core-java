/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.*;

/**
 * Fallback compactor that summarises the full conversation history when the
 * estimated token count exceeds the configured threshold.
 * <p>
 * The full compaction flow:
 * <ol>
 *   <li>Estimate total tokens in the context window.</li>
 *   <li>If above threshold, keep the last N messages verbatim and replace
 *       the older portion with a summary (LLM-generated or session-memory based).</li>
 *   <li>Reinject state snapshots (skills, task status, plan mode) into the compacted output.</li>
 * </ol>
 * <p>
 * <b>Note:</b> The LLM-dependent summary generation is deferred to a future iteration.
 * This skeleton provides the trigger logic, configuration, and state-reinjection registry.
 * <p>
 * Mirrors Python's {@code FullCompactProcessor} from
 * {@code context_engine/processor/compressor/full_compact_processor.py}.
 */
public class FullCompactProcessor extends ContextProcessor {

    private final FullCompactStateReinjector stateReinjector;

    public FullCompactProcessor(FullCompactProcessorConfig config) {
        super(config);
        this.stateReinjector = new FullCompactStateReinjector();
    }

    private FullCompactProcessorConfig cfg() {
        return getConfig();
    }

    /**
     * Access the state reinjector for external builder registration.
     */
    public FullCompactStateReinjector getStateReinjector() {
        return stateReinjector;
    }

    // ------------------------------------------------------------------
    // Trigger
    // ------------------------------------------------------------------

    @Override
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        List<BaseMessage> allMessages = new ArrayList<>(context.getMessages());
        allMessages.addAll(messagesToAdd);
        int totalTokens = estimateTotalTokens(allMessages, context);
        if (totalTokens > cfg().getTriggerTotalTokens()) {
            return true;
        }
        return false;
    }

    @Override
    public boolean triggerGetContextWindow(ModelContext context, ContextWindow contextWindow) {
        List<BaseMessage> contextMessages = contextWindow.getContextMessages();
        if (contextMessages == null || contextMessages.isEmpty()) {
            return false;
        }
        int totalTokens = estimateTotalTokens(contextMessages, context);
        return totalTokens > cfg().getTriggerTotalTokens();
    }

    // ------------------------------------------------------------------
    // On-add processing (skeleton)
    // ------------------------------------------------------------------

    @Override
    public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        // Full compaction of messages requires LLM summary generation.
        // Return as-is for now — the trigger will prevent repeated invocations
        // until the full implementation is available.
        return ProcessResult.ofMessages(null, messagesToAdd);
    }

    @Override
    public ProcessResult onGetContextWindow(ModelContext context, ContextWindow contextWindow) {
        // Full compaction of context window requires LLM summary generation.
        // Return as-is for now.
        return ProcessResult.ofContextWindow(null, contextWindow);
    }

    // ------------------------------------------------------------------
    // State (stateless for skeleton)
    // ------------------------------------------------------------------

    @Override
    public void loadState(Map<String, Object> state) {
        // stateless
    }

    @Override
    public Map<String, Object> saveState() {
        return new HashMap<>();
    }

    // ==================== Private helpers ====================

    private int estimateTotalTokens(List<BaseMessage> messages, ModelContext context) {
        TokenCounter tokenCounter = context.tokenCounter();
        if (tokenCounter != null) {
            try {
                return tokenCounter.countMessages(messages);
            } catch (Exception e) {
                // fall through to estimation
            }
        }
        int total = 0;
        for (BaseMessage msg : messages) {
            total += ContextUtils.estimateTokens(msg.getContentAsString());
        }
        return total;
    }

    /**
     * Truncate state text to the configured max characters.
     * Used by reinjection builders.
     */
    public String truncateStateText(String text) {
        if (text == null) {
            return "";
        }
        int max = cfg().getStateSnapshotMaxChars();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n... (truncated)";
    }
}
