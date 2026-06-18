/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.context;

import com.openjiuwen.core.context_engine.ContextStats;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Builds, records, and emits context compression state snapshots.
 *
 * <p>Mirrors Python's {@code ContextProcessorStateRecorder} in
 * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
 */
public class ContextProcessorStateRecorder {
    public static final String CONTEXT_COMPRESSION_STATE_TYPE = "context.compression_state";

    private final String sessionId;
    private final String contextId;
    private final Supplier<Object> getSessionRef;
    private final TokenCounterPort tokenCounter;
    private final int historyLimit;
    private final CallbackPort callbackPort;
    private List<Map<String, Object>> history = new ArrayList<>();

    public ContextProcessorStateRecorder(String sessionId, String contextId, Supplier<Object> getSessionRef) {
        this(sessionId, contextId, getSessionRef, null, 100, null);
    }

    public ContextProcessorStateRecorder(String sessionId, String contextId, Supplier<Object> getSessionRef,
                                         TokenCounterPort tokenCounter, int historyLimit,
                                         CallbackPort callbackPort) {
        this.sessionId = sessionId;
        this.contextId = contextId;
        this.getSessionRef = getSessionRef == null ? () -> null : getSessionRef;
        this.tokenCounter = tokenCounter;
        this.historyLimit = historyLimit;
        this.callbackPort = callbackPort;
    }

    public List<Map<String, Object>> history() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : history) {
            result.add(new LinkedHashMap<>(item));
        }
        return result;
    }

    public void loadHistory(List<Map<String, Object>> externalHistory) {
        List<Map<String, Object>> source = externalHistory == null ? List.of() : externalHistory;
        int start = Math.max(0, source.size() - historyLimit);
        history = new ArrayList<>();
        for (Map<String, Object> item : source.subList(start, source.size())) {
            history.add(new LinkedHashMap<>(item));
        }
    }

    public void emit(Object context, ContextCompressionState state) {
        record(state);
        try {
            if (callbackPort != null) {
                callbackPort.trigger("CONTEXT_COMPRESSION_STATE", context, getSessionRef.get(), sessionId, contextId,
                        state);
            }
        } catch (RuntimeException ignored) {
            // Python logs callback failures and continues.
        }
        Object session = getSessionRef.get();
        if (session == null) {
            return;
        }
        OutputSchema output = new OutputSchema(CONTEXT_COMPRESSION_STATE_TYPE, 0, state.modelDump());
        try {
            if (session instanceof SessionStreamPort sessionStreamPort) {
                sessionStreamPort.writeStream(output);
                return;
            }
            Method method = session.getClass().getMethod("writeStream", OutputSchema.class);
            method.invoke(session, output);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            // Python logs stream failures and continues.
        }
    }

    public ContextCompressionState buildState(ContextProcessorStateInput stateInput) {
        ContextCompressionMetric before = buildMetric(stateInput.beforeMessages(), stateInput.contextMax(),
                stateInput.startedAt());
        ContextCompressionMetric after = stateInput.afterMessages() == null
                ? null
                : buildMetric(stateInput.afterMessages(), stateInput.contextMax(), stateInput.endedAt());
        ContextCompressionSaved saved = buildSaved(before, after);
        List<BaseMessage> statisticMessages = stateInput.afterMessages() != null
                ? stateInput.afterMessages()
                : stateInput.beforeMessages();
        Integer durationMs = stateInput.endedAt() == null
                ? null
                : (int) ((stateInput.endedAt() - stateInput.startedAt()) * 1000);

        return new ContextCompressionState(
                CONTEXT_COMPRESSION_STATE_TYPE,
                stateInput.operationId(),
                stateInput.status(),
                stateInput.phase(),
                stateInput.processor() == null ? "" : stateInput.processor().processorType(),
                resolveModelName(stateInput.processor(), stateInput.trigger(), stateInput.force()),
                before,
                after,
                buildStatistic(statisticMessages),
                saved,
                ContextCompressionUsage.fromMap(stateInput.compressionUsage()),
                durationMs,
                stateInput.contextMax(),
                buildSummary(new SummaryInput(stateInput.status(), before, after, saved, stateInput.reason(),
                        stateInput.messagesToModify())),
                stateInput.compactSummary() == null ? "" : stateInput.compactSummary(),
                stateInput.error()
        );
    }

    private void record(ContextCompressionState state) {
        history.add(state.modelDump());
        if (history.size() > historyLimit) {
            history = new ArrayList<>(history.subList(history.size() - historyLimit, history.size()));
        }
    }

    private ContextCompressionMetric buildMetric(List<BaseMessage> messages, Integer contextMax, Double observedAt) {
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        int tokens = measureMessages(safeMessages);
        return new ContextCompressionMetric(formatTime(observedAt), safeMessages.size(), tokens,
                contextPercent(tokens, contextMax));
    }

    private int measureMessages(List<BaseMessage> messages) {
        if (tokenCounter != null) {
            try {
                Integer tokens = tokenCounter.countMessages(messages);
                if (tokens != null) {
                    return tokens;
                }
            } catch (RuntimeException ignored) {
            }
        }
        int totalChars = 0;
        for (BaseMessage message : messages) {
            Object content = message.getContent();
            totalChars += String.valueOf(content == null ? "" : content).length();
        }
        return (int) Math.ceil(totalChars / 4.0d);
    }

    private ContextStats buildStatistic(List<BaseMessage> messages) {
        ContextStats stat = new ContextStats();
        for (BaseMessage message : messages == null ? List.<BaseMessage>of() : messages) {
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
                default -> {
                }
            }
        }
        stat.setTotalTokens(stat.getAssistantMessageTokens()
                + stat.getUserMessageTokens()
                + stat.getSystemMessageTokens()
                + stat.getToolMessageTokens());
        stat.setTotalDialogues(ContextUtils.findAllDialogueRound(messages == null ? List.of() : messages).size());
        return stat;
    }

    private int countMessageForStatistic(BaseMessage message) {
        if (tokenCounter == null) {
            return 0;
        }
        try {
            Integer tokens = tokenCounter.count(message.getContent() == null ? "" : message.getContent());
            return tokens == null ? 0 : tokens;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static ContextCompressionSaved buildSaved(ContextCompressionMetric before, ContextCompressionMetric after) {
        if (after == null) {
            return null;
        }
        int savedMessages = before.messages() - after.messages();
        int savedTokens = before.tokens() - after.tokens();
        double savedPercent = before.tokens() > 0 ? Math.round(savedTokens / (double) before.tokens() * 1000.0) / 10.0
                : 0.0d;
        return new ContextCompressionSaved(savedMessages, savedTokens, savedPercent);
    }

    private static Integer contextPercent(int tokens, Integer contextMax) {
        if (contextMax == null || contextMax == 0) {
            return null;
        }
        return Math.max(0, Math.min(100, (int) Math.round(tokens / (double) contextMax * 100.0)));
    }

    private static String compactNumber(Integer value) {
        if (value == null) {
            return "unknown";
        }
        int absoluteValue = Math.abs(value);
        if (absoluteValue >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fm", value / 1_000_000.0d).replace(".0m", "m");
        }
        if (absoluteValue >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0d).replace(".0k", "k");
        }
        return String.valueOf(value);
    }

    private static String formatTime(Double timestamp) {
        if (timestamp == null) {
            return null;
        }
        long millis = Math.round(timestamp * 1000.0d);
        return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private String buildSummary(SummaryInput summaryInput) {
        String status = summaryInput.status();
        ContextCompressionMetric before = summaryInput.before();
        ContextCompressionMetric after = summaryInput.after();
        ContextCompressionSaved saved = summaryInput.saved();
        if ("started".equals(status)) {
            return "Compressing " + before.messages() + " messages, ~" + compactNumber(before.tokens()) + " tokens";
        }
        if ("failed".equals(status)) {
            return "Context processor failed; context remains ~" + compactNumber(before.tokens()) + " tokens";
        }
        if (after == null || saved == null) {
            return "Context processor skipped: " + summaryInput.reason();
        }
        if ("noop".equals(status)) {
            return "Context unchanged at ~" + compactNumber(after.tokens()) + " tokens (saved "
                    + String.format(Locale.ROOT, "%.1f", saved.percent()) + "%)";
        }
        List<Integer> messagesToModify = summaryInput.messagesToModify() == null ? List.of()
                : summaryInput.messagesToModify();
        String modified = messagesToModify.isEmpty() ? "" : ", modified " + messagesToModify.size() + " messages";
        return "Compressed " + before.messages() + " -> " + after.messages() + " messages, ~"
                + compactNumber(before.tokens()) + " -> ~" + compactNumber(after.tokens()) + " tokens, saved ~"
                + compactNumber(saved.tokens()) + " tokens (" + String.format(Locale.ROOT, "%.1f", saved.percent())
                + "%)" + modified;
    }

    private static String resolveModelName(ContextProcessorStateInput.ContextProcessorPort processor, String trigger,
                                           boolean force) {
        if (processor == null) {
            return "";
        }
        Object config = processor.config();
        Object modelConfig = readProperty(config, "model");
        if (modelConfig != null) {
            String fromModelName = readTextProperty(modelConfig, "modelName");
            if (!fromModelName.isBlank()) {
                return fromModelName;
            }
            String fromModel = readTextProperty(modelConfig, "model");
            if (!fromModel.isBlank()) {
                return fromModel;
            }
        }
        String fromConfigModelName = readTextProperty(config, "modelName");
        if (!fromConfigModelName.isBlank()) {
            return fromConfigModelName;
        }
        return readTextProperty(config, "model");
    }

    private static Object readProperty(Object target, String property) {
        if (target == null) {
            return null;
        }
        String getter = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        try {
            Method method = target.getClass().getMethod(getter);
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            return null;
        }
    }

    private static String readTextProperty(Object target, String property) {
        Object value = readProperty(target, property);
        return value instanceof String text ? text : "";
    }

    private static Map<String, Object> statsMap(ContextStats stats) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_messages", stats.getTotalMessages());
        result.put("total_tokens", stats.getTotalTokens());
        result.put("total_dialogues", stats.getTotalDialogues());
        result.put("system_messages", stats.getSystemMessages());
        result.put("user_messages", stats.getUserMessages());
        result.put("assistant_messages", stats.getAssistantMessages());
        result.put("tool_messages", stats.getToolMessages());
        result.put("tools", stats.getTools());
        result.put("system_message_tokens", stats.getSystemMessageTokens());
        result.put("user_message_tokens", stats.getUserMessageTokens());
        result.put("assistant_message_tokens", stats.getAssistantMessageTokens());
        result.put("tool_message_tokens", stats.getToolMessageTokens());
        result.put("tool_tokens", stats.getToolTokens());
        return result;
    }

    /**
     * Summary-building input DTO.
     *
     * <p>Mirrors Python's {@code _SummaryInput} in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    private record SummaryInput(String status, ContextCompressionMetric before, ContextCompressionMetric after,
                                ContextCompressionSaved saved, String reason, List<Integer> messagesToModify) {
    }

    /**
     * Token counter adapter.
     *
     * <p>Mirrors Python's {@code TokenCounter} dependency in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    public interface TokenCounterPort {
        Integer countMessages(List<BaseMessage> messages);

        Integer count(Object content);
    }

    /**
     * Callback adapter for compression-state events.
     *
     * <p>Mirrors Python's {@code lazy_callback_framework.trigger(...)} dependency in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    public interface CallbackPort {
        void trigger(String event, Object context, Object sessionRef, String sessionId, String contextId,
                     ContextCompressionState state);
    }

    /**
     * Session stream adapter.
     *
     * <p>Mirrors Python's {@code session.write_stream(...)} dependency in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    public interface SessionStreamPort {
        void writeStream(OutputSchema outputSchema);
    }

    /**
     * Context compression metric DTO.
     *
     * <p>Mirrors Python's {@code ContextCompressionMetric} use in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    public record ContextCompressionMetric(String time, int messages, int tokens, Integer contextPercent) {
        public Map<String, Object> modelDump() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("time", time);
            result.put("messages", messages);
            result.put("tokens", tokens);
            result.put("context_percent", contextPercent);
            return result;
        }
    }

    /**
     * Context compression saved-metric DTO.
     *
     * <p>Mirrors Python's {@code ContextCompressionSaved} use in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    public record ContextCompressionSaved(int messages, int tokens, double percent) {
        public Map<String, Object> modelDump() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("messages", messages);
            result.put("tokens", tokens);
            result.put("percent", percent);
            return result;
        }
    }

    /**
     * Context compression usage DTO.
     *
     * <p>Mirrors Python's {@code ContextCompressionUsage} use in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    public record ContextCompressionUsage(int calls, int inputTokens, int outputTokens, int totalTokens,
                                          int cacheTokens, double inputCost, double outputCost, double totalCost,
                                          String modelName, List<Map<String, Object>> details) {
        @SuppressWarnings("unchecked")
        public static ContextCompressionUsage fromMap(Map<String, Object> usage) {
            if (usage == null || usage.isEmpty()) {
                return null;
            }
            return new ContextCompressionUsage(
                    intValue(usage.get("calls")),
                    intValue(usage.get("input_tokens")),
                    intValue(usage.get("output_tokens")),
                    intValue(usage.get("total_tokens")),
                    intValue(usage.get("cache_tokens")),
                    doubleValue(usage.get("input_cost")),
                    doubleValue(usage.get("output_cost")),
                    doubleValue(usage.get("total_cost")),
                    usage.get("model_name") instanceof String text ? text : "",
                    usage.get("details") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of()
            );
        }

        public Map<String, Object> modelDump() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("calls", calls);
            result.put("input_tokens", inputTokens);
            result.put("output_tokens", outputTokens);
            result.put("total_tokens", totalTokens);
            result.put("cache_tokens", cacheTokens);
            result.put("input_cost", inputCost);
            result.put("output_cost", outputCost);
            result.put("total_cost", totalCost);
            result.put("model_name", modelName);
            result.put("details", details);
            return result;
        }

        private static int intValue(Object value) {
            return value instanceof Number number ? number.intValue() : 0;
        }

        private static double doubleValue(Object value) {
            return value instanceof Number number ? number.doubleValue() : 0.0d;
        }
    }

    /**
     * Context compression state DTO.
     *
     * <p>Mirrors Python's {@code ContextCompressionState} use in
     * {@code openjiuwen/core/context_engine/context/processor_state_recorder.py}.</p>
     */
    public record ContextCompressionState(String type, String operationId, String status, String phase,
                                          String processor, String model, ContextCompressionMetric before,
                                          ContextCompressionMetric after, ContextStats statistic,
                                          ContextCompressionSaved saved, ContextCompressionUsage compressionUsage,
                                          Integer durationMs, Integer contextMax, String summary,
                                          String compactSummary, String error) {
        public Map<String, Object> modelDump() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", type);
            result.put("operation_id", operationId);
            result.put("status", status);
            result.put("phase", phase);
            result.put("processor", processor);
            result.put("model", model);
            result.put("before", before == null ? null : before.modelDump());
            result.put("after", after == null ? null : after.modelDump());
            result.put("statistic", statsMap(statistic));
            result.put("saved", saved == null ? null : saved.modelDump());
            result.put("compression_usage", compressionUsage == null ? null : compressionUsage.modelDump());
            result.put("duration_ms", durationMs);
            result.put("context_max", contextMax);
            result.put("summary", summary);
            result.put("compact_summary", compactSummary);
            result.put("error", error);
            return result;
        }
    }
}
