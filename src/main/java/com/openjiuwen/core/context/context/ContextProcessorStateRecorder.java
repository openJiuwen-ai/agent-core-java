/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.schema.ContextCompressionMetric;
import com.openjiuwen.core.context.schema.ContextCompressionSaved;
import com.openjiuwen.core.context.schema.ContextCompressionState;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/**
 * Records and emits context-compression state events during processor execution.
 * <p>
 * Tracks before/after message counts and token estimates, computes savings,
 * builds human-readable summaries, and emits events through the callback framework
 * and session stream.
 * <p>
 * Mirrors Python's {@code ContextProcessorStateRecorder} from
 * {@code context_engine/context/processor_state_recorder.py}.
 */
public class ContextProcessorStateRecorder {

    private final String sessionId;
    private final String contextId;
    private final Supplier<Object> getSessionRef;
    private final TokenCounter tokenCounter;
    private final int historyLimit;
    private final List<Map<String, Object>> history = new ArrayList<>();

    public ContextProcessorStateRecorder(
            String sessionId,
            String contextId,
            Supplier<Object> getSessionRef,
            TokenCounter tokenCounter,
            int historyLimit) {
        this.sessionId = sessionId;
        this.contextId = contextId;
        this.getSessionRef = getSessionRef;
        this.tokenCounter = tokenCounter;
        this.historyLimit = historyLimit;
    }

    public ContextProcessorStateRecorder(
            String sessionId,
            String contextId,
            Supplier<Object> getSessionRef) {
        this(sessionId, contextId, getSessionRef, null, 100);
    }

    /**
     * Return a copy of the recorded history.
     */
    public List<Map<String, Object>> history() {
        return new ArrayList<>(history);
    }

    /**
     * Load history from an external source (e.g., checkpoint).
     */
    public void loadHistory(List<Map<String, Object>> externalHistory) {
        history.clear();
        if (externalHistory != null) {
            List<Map<String, Object>> trimmed = externalHistory.subList(
                    Math.max(0, externalHistory.size() - historyLimit), externalHistory.size());
            history.addAll(trimmed);
        }
    }

    /**
     * Record and emit a compression state event.
     * <p>
     * In the Java implementation, this logs the event and records it to history.
     * Callback and stream emission are deferred until those subsystems are fully ported.
     */
    public void emit(ContextCompressionState state) {
        record(state);
        logState(state);
    }

    /**
     * Build a {@link ContextCompressionState} from a raw input snapshot.
     */
    public ContextCompressionState buildState(ContextProcessorStateInput input) {
        ContextCompressionMetric before = buildMetric(
                input.getBeforeMessages(), input.getContextMax(), input.getStartedAt());
        ContextCompressionMetric after = input.getAfterMessages() != null
                ? buildMetric(input.getAfterMessages(), input.getContextMax(), input.getEndedAt())
                : null;
        ContextCompressionSaved saved = buildSaved(before, after);

        List<BaseMessage> statisticMessages = input.getAfterMessages() != null
                ? input.getAfterMessages()
                : input.getBeforeMessages();
        ContextStats statistic = buildStatistic(statisticMessages);

        String processorName = input.getProcessor() != null
                ? input.getProcessor().processorType() : "";
        String modelName = resolveModelName(input.getProcessor());

        Integer durationMs = null;
        if (input.getEndedAt() != null) {
            durationMs = (int) ((input.getEndedAt() - input.getStartedAt()) * 1000);
        }

        String summary = buildSummary(
                input.getStatus(), before, after, saved,
                input.getReason(), input.getMessagesToModify());

        return ContextCompressionState.builder()
                .operationId(input.getOperationId())
                .status(input.getStatus())
                .phase(input.getPhase())
                .processor(processorName)
                .model(modelName)
                .before(before)
                .after(after)
                .statistic(statistic)
                .saved(saved)
                .durationMs(durationMs)
                .contextMax(input.getContextMax())
                .summary(summary)
                .error(input.getError())
                .build();
    }

    // ==================== Private helpers ====================

    private void record(ContextCompressionState state) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("operationId", state.getOperationId());
        entry.put("status", state.getStatus());
        entry.put("processor", state.getProcessor());
        history.add(entry);
        if (history.size() > historyLimit) {
            history.remove(0);
        }
    }

    private void logState(ContextCompressionState state) {
        Loggers.CONTEXT_ENGINE.info(
                "context compression state: status={} phase={} processor={} "
                        + "session_id={} context_id={} op={} before={} after={} saved={}",
                state.getStatus(), state.getPhase(), state.getProcessor(),
                sessionId, contextId, state.getOperationId(),
                formatMetricForLog(state.getBefore()),
                formatMetricForLog(state.getAfter()),
                state.getSaved());
    }

    private ContextCompressionMetric buildMetric(
            List<BaseMessage> messages, Integer contextMax, double observedAt) {
        List<BaseMessage> msgs = messages != null ? messages : Collections.emptyList();
        int tokens = measureMessages(msgs);
        return ContextCompressionMetric.builder()
                .time(formatTime(observedAt))
                .messages(msgs.size())
                .tokens(tokens)
                .contextPercent(contextPercent(tokens, contextMax))
                .build();
    }

    private int measureMessages(List<BaseMessage> messages) {
        if (tokenCounter != null) {
            try {
                return tokenCounter.countMessages(messages);
            } catch (Exception e) {
                Loggers.CONTEXT_ENGINE.debug(
                        "token_counter failed while measuring context processor state: {}", e.getMessage());
            }
        }
        int totalChars = 0;
        for (BaseMessage msg : messages) {
            String content = msg.getContentAsString();
            if (content != null) {
                totalChars += content.length();
            }
        }
        return (int) Math.ceil(totalChars / 4.0);
    }

    private ContextStats buildStatistic(List<BaseMessage> messages) {
        ContextStats stat = new ContextStats();
        if (messages == null) {
            return stat;
        }
        for (BaseMessage msg : messages) {
            int tokens = countMessageForStatistic(msg);
            String role = msg.getRole();
            stat.setTotalMessages(stat.getTotalMessages() + 1);
            if ("assistant".equals(role)) {
                stat.setAssistantMessages(stat.getAssistantMessages() + 1);
                stat.setAssistantMessageTokens(stat.getAssistantMessageTokens() + tokens);
            } else if ("user".equals(role)) {
                stat.setUserMessages(stat.getUserMessages() + 1);
                stat.setUserMessageTokens(stat.getUserMessageTokens() + tokens);
            } else if ("system".equals(role)) {
                stat.setSystemMessages(stat.getSystemMessages() + 1);
                stat.setSystemMessageTokens(stat.getSystemMessageTokens() + tokens);
            } else if ("tool".equals(role)) {
                stat.setToolMessages(stat.getToolMessages() + 1);
                stat.setToolMessageTokens(stat.getToolMessageTokens() + tokens);
            }
        }
        stat.setTotalTokens(
                stat.getAssistantMessageTokens() + stat.getUserMessageTokens()
                        + stat.getSystemMessageTokens() + stat.getToolMessageTokens());
        stat.setTotalDialogues(ContextUtils.findAllDialogueRound(messages).size());
        return stat;
    }

    private int countMessageForStatistic(BaseMessage message) {
        if (tokenCounter == null) {
            return 0;
        }
        try {
            return tokenCounter.count(message.getContentAsString());
        } catch (Exception e) {
            return 0;
        }
    }

    private static ContextCompressionSaved buildSaved(
            ContextCompressionMetric before, ContextCompressionMetric after) {
        if (after == null) {
            return null;
        }
        int savedMessages = before.getMessages() - after.getMessages();
        int savedTokens = before.getTokens() - after.getTokens();
        double savedPercent = 0.0;
        if (before.getTokens() > 0) {
            savedPercent = Math.round(savedTokens * 1000.0 / before.getTokens()) / 10.0;
        }
        return ContextCompressionSaved.builder()
                .messages(savedMessages)
                .tokens(savedTokens)
                .percent(savedPercent)
                .build();
    }

    private static String formatMetricForLog(ContextCompressionMetric metric) {
        if (metric == null) {
            return null;
        }
        return String.format("messages=%d tokens=%d percent=%s time=%s",
                metric.getMessages(), metric.getTokens(),
                metric.getContextPercent(), metric.getTime());
    }

    private static Integer contextPercent(int tokens, Integer contextMax) {
        if (contextMax == null || contextMax == 0) {
            return null;
        }
        return Math.max(0, Math.min(100, (int) Math.round(tokens * 100.0 / contextMax)));
    }

    private static String compactNumber(Integer value) {
        if (value == null) {
            return "unknown";
        }
        int absVal = Math.abs(value);
        if (absVal >= 1_000_000) {
            String formatted = String.format("%.1fm", value / 1_000_000.0);
            return formatted.replace(".0m", "m");
        }
        if (absVal >= 1_000) {
            String formatted = String.format("%.1fk", value / 1_000.0);
            return formatted.replace(".0k", "k");
        }
        return String.valueOf(value);
    }

    private static String formatTime(double timestamp) {
        try {
            Instant instant = Instant.ofEpochSecond((long) timestamp,
                    (long) ((timestamp - (long) timestamp) * 1_000_000_000));
            return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildSummary(String status, ContextCompressionMetric before,
                                ContextCompressionMetric after, ContextCompressionSaved saved,
                                String reason, List<Integer> messagesToModify) {
        if ("started".equals(status)) {
            return String.format("Compressing %d messages, ~%s tokens",
                    before.getMessages(), compactNumber(before.getTokens()));
        }
        if ("failed".equals(status)) {
            return String.format("Context processor failed; context remains ~%s tokens",
                    compactNumber(before.getTokens()));
        }
        if (after == null || saved == null) {
            return String.format("Context processor skipped: %s", reason);
        }
        if ("noop".equals(status)) {
            return String.format("Context unchanged at ~%s tokens (saved %.1f%%)",
                    compactNumber(after.getTokens()), saved.getPercent());
        }
        String modified = messagesToModify != null && !messagesToModify.isEmpty()
                ? String.format(", modified %d messages", messagesToModify.size()) : "";
        return String.format("Compressed %d -> %d messages, ~%s -> ~%s tokens, "
                        + "saved ~%s tokens (%.1f%%)%s",
                before.getMessages(), after.getMessages(),
                compactNumber(before.getTokens()), compactNumber(after.getTokens()),
                compactNumber(saved.getTokens()), saved.getPercent(), modified);
    }

    private static String resolveModelName(ContextProcessor processor) {
        if (processor == null) {
            return "";
        }
        Object config = processor.getConfig();
        // Try common attribute paths for model name
        try {
            Object modelConfig = config.getClass().getMethod("getModel").invoke(config);
            if (modelConfig != null) {
                for (String key : new String[]{"getModelName", "getModel"}) {
                    try {
                        Object value = modelConfig.getClass().getMethod(key).invoke(modelConfig);
                        if (value instanceof String s && !s.isEmpty()) {
                            return s;
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        for (String key : new String[]{"getModelName", "getModel"}) {
            try {
                Object value = config.getClass().getMethod(key).invoke(config);
                if (value instanceof String s && !s.isEmpty()) {
                    return s;
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }
}
