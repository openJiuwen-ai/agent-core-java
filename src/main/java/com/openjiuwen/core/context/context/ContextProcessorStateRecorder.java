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
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Records and emits context compression state transitions.
 * <p>
 * Mirrors Python's {@code ContextProcessorStateRecorder}.
 * 
 * @since 0.1.7
 */
public class ContextProcessorStateRecorder {
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneId.systemDefault());

    private final String sessionId;
    private final String contextId;
    private final Supplier<Session> sessionSupplier;
    private final TokenCounter tokenCounter;
    private final int historyLimit;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<Map<String, Object>> history = new ArrayList<>();

    /**
     * ContextProcessorStateRecorder.
     * 
     * @param sessionId sessionId
     * @param contextId contextId
     * @param sessionSupplier sessionSupplier
     * @param tokenCounter tokenCounter
     * @param historyLimit historyLimit
     * @since 0.1.7
     */
    public ContextProcessorStateRecorder(String sessionId, String contextId, Supplier<Session> sessionSupplier,
            TokenCounter tokenCounter, int historyLimit) {
        this.sessionId = sessionId;
        this.contextId = contextId;
        this.sessionSupplier = sessionSupplier;
        this.tokenCounter = tokenCounter;
        this.historyLimit = historyLimit;
    }

    /**
     * ContextProcessorStateRecorder.
     * 
     * @param sessionId sessionId
     * @param contextId contextId
     * @param sessionSupplier sessionSupplier
     * @param tokenCounter tokenCounter
     * @since 0.1.7
     */
    public ContextProcessorStateRecorder(String sessionId, String contextId, Supplier<Session> sessionSupplier,
            TokenCounter tokenCounter) {
        this(sessionId, contextId, sessionSupplier, tokenCounter, 100);
    }

    /**
     * history.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, Object>> history() {
        return List.copyOf(history);
    }

    /**
     * loadHistory.
     * 
     * @param entries entries
     * @since 0.1.7
     */
    public void loadHistory(List<Map<String, Object>> entries) {
        history.clear();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        int start = Math.max(0, entries.size() - historyLimit);
        history.addAll(entries.subList(start, entries.size()));
    }

    /**
     * emit.
     * 
     * @param context context
     * @param state state
     * @since 0.1.7
     */
    public void emit(SessionModelContext context, ContextCompressionState state) {
        record(state);
        Loggers.CONTEXT_ENGINE.info(
                "context compression state: status={} phase={} processor={} session_id={} context_id={}"
                        + " op={} before={} after={} saved={}",
                state.getStatus(), state.getPhase(), state.getProcessor(), sessionId, contextId, state.getOperationId(),
                formatMetricForLog(state.getBefore()), formatMetricForLog(state.getAfter()), state.getSaved());
        Session session = sessionSupplier.get();
        if (session == null) {
            Loggers.CONTEXT_ENGINE
                    .debug("context compression state stream skipped: no session writer session_id={} context_id={}"
                            + " op={}", sessionId, contextId, state.getOperationId());
            return;
        }
        try {
            session.writeStream(new OutputSchema(ContextCompressionState.CONTEXT_COMPRESSION_STATE_TYPE, 0, state));
        } catch (RuntimeException exception) {
            Loggers.CONTEXT_ENGINE.warning(
                    "failed to emit context compression state to session stream: session_id={} context_id={}"
                            + " op={} status={} error={}",
                    sessionId, contextId, state.getOperationId(), state.getStatus(), exception.getMessage());
        }
    }

    /**
     * buildState.
     * 
     * @param input input
     * @return the result
     * @since 0.1.7
     */
    public ContextCompressionState buildState(ContextProcessorStateInput input) {
        ContextCompressionMetric before = buildMetric(input.beforeMessages(), input.contextMax(), input.startedAt());
        ContextCompressionMetric after = input.afterMessages() != null
                ? buildMetric(input.afterMessages(), input.contextMax(), input.endedAt())
                : null;
        ContextCompressionSaved saved = buildSaved(before, after);
        List<BaseMessage> statisticMessages =
            input.afterMessages() != null ? input.afterMessages() : input.beforeMessages();

        return ContextCompressionState.builder().operationId(input.operationId()).status(input.status())
                .phase(input.phase()).processor(input.processor() != null ? input.processor().processorType() : "")
                .model(resolveModelName(input.processor(), input.trigger(), input.isForce())).before(before)
                .after(after).statistic(buildStatistic(statisticMessages)).saved(saved)
                .durationMs(input.endedAt() != null ? (int) ((input.endedAt() - input.startedAt()) * 1000) : null)
                .contextMax(input.contextMax())
                .summary(buildSummary(input.status(), before, after, saved, input.reason(), input.messagesToModify()))
                .error(input.error()).build();
    }

    /**
     * record.
     * 
     * @param state state
     * @since 0.1.7
     */
    private void record(ContextCompressionState state) {
        history.add(Map.of("type", state.getType(), "operation_id", state.getOperationId(), "status", state.getStatus(),
                "phase", state.getPhase(), "processor", state.getProcessor(), "summary", state.getSummary()));
        if (history.size() > historyLimit) {
            history.subList(0, history.size() - historyLimit).clear();
        }
    }

    /**
     * buildMetric.
     * 
     * @param messages messages
     * @param contextMax contextMax
     * @param observedAt observedAt
     * @return the result
     * @since 0.1.7
     */
    private ContextCompressionMetric buildMetric(List<BaseMessage> messages, Integer contextMax, Double observedAt) {
        List<BaseMessage> safeMessages = messages != null ? messages : List.of();
        int tokens = measureMessages(safeMessages);
        return ContextCompressionMetric.builder().time(formatTime(observedAt)).messages(safeMessages.size())
                .tokens(tokens).contextPercent(contextPercent(tokens, contextMax)).build();
    }

    /**
     * measureMessages.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    private int measureMessages(List<BaseMessage> messages) {
        if (tokenCounter != null) {
            try {
                return tokenCounter.countMessages(messages);
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                return approximateTokens(messages);
            }
        }
        return approximateTokens(messages);
    }

    /**
     * approximateTokens.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    private int approximateTokens(List<BaseMessage> messages) {
        int totalChars = 0;
        for (BaseMessage message : messages) {
            totalChars += String.valueOf(message.getContent() != null ? message.getContent() : "").length();
        }
        return (int) Math.ceil(totalChars / 4.0);
    }

    /**
     * buildStatistic.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    private ContextStats buildStatistic(List<BaseMessage> messages) {
        ContextStats stat = new ContextStats();
        List<BaseMessage> safeMessages = messages != null ? messages : List.of();
        for (BaseMessage message : safeMessages) {
            stat.setTotalMessages(stat.getTotalMessages() + 1);
            int tokens = countMessageForStatistic(message);
            switch (message.getRole()) {
                case "assistant" -> {
                    stat.setAssistantMessages(stat.getAssistantMessages() + 1);
                    stat.setAssistantMessageTokens(stat.getAssistantMessageTokens() + tokens);
                }
                case "user" -> {
                    stat.setUserMessages(stat.getUserMessages() + 1);
                    stat.setUserMessageTokens(stat.getUserMessageTokens() + tokens);
                }
                case "system" -> {
                    stat.setSystemMessages(stat.getSystemMessages() + 1);
                    stat.setSystemMessageTokens(stat.getSystemMessageTokens() + tokens);
                }
                case "tool" -> {
                    stat.setToolMessages(stat.getToolMessages() + 1);
                    stat.setToolMessageTokens(stat.getToolMessageTokens() + tokens);
                }
                default -> { // no-op
                }
            }
        }
        stat.setTotalTokens(stat.getAssistantMessageTokens() + stat.getUserMessageTokens()
                + stat.getSystemMessageTokens() + stat.getToolMessageTokens());
        stat.setTotalDialogues(ContextUtils.findAllDialogueRound(safeMessages).size());
        return stat;
    }

    /**
     * countMessageForStatistic.
     * 
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    private int countMessageForStatistic(BaseMessage message) {
        if (tokenCounter == null) {
            return 0;
        }
        try {
            return tokenCounter.count(String.valueOf(message.getContent() != null ? message.getContent() : ""));
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return 0;
        }
    }

    /**
     * buildSaved.
     * 
     * @param before before
     * @param after after
     * @return the result
     * @since 0.1.7
     */
    private static ContextCompressionSaved buildSaved(ContextCompressionMetric before, ContextCompressionMetric after) {
        if (after == null) {
            return nullValue();
        }
        int savedMessages = before.getMessages() - after.getMessages();
        int savedTokens = before.getTokens() - after.getTokens();
        float savedPercent =
            before.getTokens() > 0 ? Math.round((savedTokens * 1000.0f / before.getTokens())) / 10.0f : 0.0f;
        return ContextCompressionSaved.builder().messages(savedMessages).tokens(savedTokens).percent(savedPercent)
                .build();
    }

    /**
     * formatMetricForLog.
     * 
     * @param metric metric
     * @return the result
     * @since 0.1.7
     */
    private static String formatMetricForLog(ContextCompressionMetric metric) {
        if (metric == null) {
            return nullValue();
        }
        return "messages=" + metric.getMessages() + " tokens=" + metric.getTokens() + " percent="
                + metric.getContextPercent() + " time=" + metric.getTime();
    }

    /**
     * contextPercent.
     * 
     * @param tokens tokens
     * @param contextMax contextMax
     * @return the result
     * @since 0.1.7
     */
    private static Integer contextPercent(int tokens, Integer contextMax) {
        if (contextMax == null || contextMax <= 0) {
            return nullValue();
        }
        return Math.max(0, Math.min(100, Math.round(tokens * 100.0f / contextMax)));
    }

    /**
     * compactNumber.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String compactNumber(Integer value) {
        if (value == null) {
            return "unknown";
        }
        int absValue = Math.abs(value);
        if (absValue >= 1_000_000) {
            return stripTrailingZero(value / 1_000_000.0) + "m";
        }
        if (absValue >= 1_000) {
            return stripTrailingZero(value / 1_000.0) + "k";
        }
        return String.valueOf(value);
    }

    /**
     * stripTrailingZero.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stripTrailingZero(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /**
     * formatTime.
     * 
     * @param timestamp timestamp
     * @return the result
     * @since 0.1.7
     */
    private static String formatTime(Double timestamp) {
        if (timestamp == null) {
            return nullValue();
        }
        long millis = (long) (timestamp * 1000);
        return TIME_FORMATTER.format(Instant.ofEpochMilli(millis));
    }

    /**
     * buildSummary.
     * 
     * @param status status
     * @param before before
     * @param after after
     * @param saved saved
     * @param reason reason
     * @param messagesToModify messagesToModify
     * @return the result
     * @since 0.1.7
     */
    private static String buildSummary(String status, ContextCompressionMetric before, ContextCompressionMetric after,
            ContextCompressionSaved saved, String reason, List<Integer> messagesToModify) {
        if ("started".equals(status)) {
            return "Compressing " + before.getMessages() + " messages, ~" + compactNumber(before.getTokens())
                    + " tokens";
        }
        if ("failed".equals(status)) {
            return "Context processor failed; context remains ~" + compactNumber(before.getTokens()) + " tokens";
        }
        if (after == null || saved == null) {
            return "Context processor skipped: " + reason;
        }
        if ("noop".equals(status)) {
            return "Context unchanged at ~" + compactNumber(after.getTokens()) + " tokens " + "(saved "
                    + String.format(java.util.Locale.ROOT, "%.1f", saved.getPercent()) + "%)";
        }
        String modified = messagesToModify != null && !messagesToModify.isEmpty()
                ? ", modified " + messagesToModify.size() + " messages"
                : "";
        return "Compressed " + before.getMessages() + " -> " + after.getMessages() + " messages, " + "~"
                + compactNumber(before.getTokens()) + " -> ~" + compactNumber(after.getTokens()) + " tokens"
                + ", saved ~" + compactNumber(saved.getTokens()) + " tokens ("
                + String.format(java.util.Locale.ROOT, "%.1f", saved.getPercent()) + "%)" + modified;
    }

    /**
     * resolveModelName.
     * 
     * @param processor processor
     * @param trigger trigger
     * @param isForced isForced
     * @return the result
     * @since 0.1.7
     */
    private static String resolveModelName(ContextProcessor processor, String trigger, boolean isForced) {
        if (processor == null) {
            return "";
        }
        Object config = processor.getConfig();
        if (config == null) {
            return "";
        }
        String modelFromNested = readStringProperty(readProperty(config, "model"), "modelName", "model");
        if (!modelFromNested.isBlank()) {
            return modelFromNested;
        }
        String model = readStringProperty(config, "modelName", "model");
        return model != null ? model : "";
    }

    /**
     * readProperty.
     * 
     * @param target target
     * @param propertyName propertyName
     * @return the result
     * @since 0.1.7
     */
    private static Object readProperty(Object target, String propertyName) {
        if (target == null) {
            return nullValue();
        }
        try {
            return target.getClass().getMethod("get" + capitalize(propertyName)).invoke(target);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return nullValue();
        }
    }

    /**
     * readStringProperty.
     * 
     * @param target target
     * @param propertyNames propertyNames
     * @return the result
     * @since 0.1.7
     */
    private static String readStringProperty(Object target, String... propertyNames) {
        for (String propertyName : propertyNames) {
            Object value = readProperty(target, propertyName);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    /**
     * capitalize.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * nullValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static <T> T nullValue() {
        return null;
    }
}
