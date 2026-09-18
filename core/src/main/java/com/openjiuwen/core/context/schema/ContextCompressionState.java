/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.context.ContextStats;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code ContextCompressionState} in
 * {@code openjiuwen/core/context_engine/schema/context_state.py}.
 */
public class ContextCompressionState {
    public static final String CONTEXT_COMPRESSION_STATE_TYPE = "context.compression_state";

    private String type = CONTEXT_COMPRESSION_STATE_TYPE;

    @JsonProperty(value = "operation_id", required = true)
    private String operationId;

    @JsonProperty(required = true)
    private ContextCompressionStatus status;

    @JsonProperty(required = true)
    private ContextCompressionPhase phase;

    private String processor = "";
    private String model = "";

    @JsonProperty(required = true)
    private ContextCompressionMetric before;

    private ContextCompressionMetric after;
    private ContextStats statistic = new ContextStats();
    private ContextCompressionSaved saved;

    @JsonProperty("compression_usage")
    private ContextCompressionUsage compressionUsage;

    @JsonProperty("duration_ms")
    private Integer durationMs;

    @JsonProperty("context_max")
    private Integer contextMax;

    private String summary = "";

    @JsonProperty("compact_summary")
    private String compactSummary = "";

    private String error;

    public ContextCompressionState() {
    }

    public ContextCompressionState(String operationId, ContextCompressionStatus status,
                                   ContextCompressionPhase phase, ContextCompressionMetric before) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.status = Objects.requireNonNull(status, "status");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.before = Objects.requireNonNull(before, "before");
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
    }

    public ContextCompressionStatus getStatus() {
        return status;
    }

    public void setStatus(ContextCompressionStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public ContextCompressionPhase getPhase() {
        return phase;
    }

    public void setPhase(ContextCompressionPhase phase) {
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    public String getProcessor() {
        return processor;
    }

    public void setProcessor(String processor) {
        this.processor = processor == null ? "" : processor;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model == null ? "" : model;
    }

    public ContextCompressionMetric getBefore() {
        return before;
    }

    public void setBefore(ContextCompressionMetric before) {
        this.before = Objects.requireNonNull(before, "before");
    }

    public ContextCompressionMetric getAfter() {
        return after;
    }

    public void setAfter(ContextCompressionMetric after) {
        this.after = after;
    }

    public ContextStats getStatistic() {
        return statistic;
    }

    public void setStatistic(ContextStats statistic) {
        this.statistic = statistic == null ? new ContextStats() : statistic;
    }

    public ContextCompressionSaved getSaved() {
        return saved;
    }

    public void setSaved(ContextCompressionSaved saved) {
        this.saved = saved;
    }

    public ContextCompressionUsage getCompressionUsage() {
        return compressionUsage;
    }

    public void setCompressionUsage(ContextCompressionUsage compressionUsage) {
        this.compressionUsage = compressionUsage;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getContextMax() {
        return contextMax;
    }

    public void setContextMax(Integer contextMax) {
        this.contextMax = contextMax;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary == null ? "" : summary;
    }

    public String getCompactSummary() {
        return compactSummary;
    }

    public void setCompactSummary(String compactSummary) {
        this.compactSummary = compactSummary == null ? "" : compactSummary;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Map<String, Object> modelDump() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("operation_id", operationId);
        result.put("status", status == null ? null : status.getValue());
        result.put("phase", phase == null ? null : phase.getValue());
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

    public Map<String, Object> model_dump() {
        return modelDump();
    }

    private static Map<String, Object> statsMap(ContextStats stats) {
        ContextStats safeStats = stats == null ? new ContextStats() : stats;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_messages", safeStats.getTotalMessages());
        result.put("total_tokens", safeStats.getTotalTokens());
        result.put("total_dialogues", safeStats.getTotalDialogues());
        result.put("system_messages", safeStats.getSystemMessages());
        result.put("user_messages", safeStats.getUserMessages());
        result.put("assistant_messages", safeStats.getAssistantMessages());
        result.put("tool_messages", safeStats.getToolMessages());
        result.put("tools", safeStats.getTools());
        result.put("system_message_tokens", safeStats.getSystemMessageTokens());
        result.put("user_message_tokens", safeStats.getUserMessageTokens());
        result.put("assistant_message_tokens", safeStats.getAssistantMessageTokens());
        result.put("tool_message_tokens", safeStats.getToolMessageTokens());
        result.put("tool_tokens", safeStats.getToolTokens());
        return result;
    }
}
