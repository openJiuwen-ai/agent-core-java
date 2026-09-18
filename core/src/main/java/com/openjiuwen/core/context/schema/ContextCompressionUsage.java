/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code ContextCompressionUsage} in
 * {@code openjiuwen/core/context_engine/schema/context_state.py}.
 */
public class ContextCompressionUsage {
    private int calls;

    @JsonProperty("input_tokens")
    private int inputTokens;

    @JsonProperty("output_tokens")
    private int outputTokens;

    @JsonProperty("total_tokens")
    private int totalTokens;

    @JsonProperty("cache_tokens")
    private int cacheTokens;

    @JsonProperty("input_cost")
    private double inputCost;

    @JsonProperty("output_cost")
    private double outputCost;

    @JsonProperty("total_cost")
    private double totalCost;

    @JsonProperty("model_name")
    private String modelName = "";

    private List<Map<String, Object>> details = new ArrayList<>();

    public ContextCompressionUsage() {
    }

    public ContextCompressionUsage(int calls, int inputTokens, int outputTokens, int totalTokens,
                                   int cacheTokens, double inputCost, double outputCost, double totalCost,
                                   String modelName, List<Map<String, Object>> details) {
        this.calls = calls;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.cacheTokens = cacheTokens;
        this.inputCost = inputCost;
        this.outputCost = outputCost;
        this.totalCost = totalCost;
        this.modelName = modelName == null ? "" : modelName;
        setDetails(details);
    }

    public static ContextCompressionUsage fromMap(Map<String, Object> usage) {
        if (usage == null) {
            return null;
        }
        return new ContextCompressionUsage(
                asInt(usage.get("calls")),
                asInt(usage.get("input_tokens")),
                asInt(usage.get("output_tokens")),
                asInt(usage.get("total_tokens")),
                asInt(usage.get("cache_tokens")),
                asDouble(usage.get("input_cost")),
                asDouble(usage.get("output_cost")),
                asDouble(usage.get("total_cost")),
                asString(usage.get("model_name")),
                asDetails(usage.get("details"))
        );
    }

    public int getCalls() {
        return calls;
    }

    public void setCalls(int calls) {
        this.calls = calls;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public int getCacheTokens() {
        return cacheTokens;
    }

    public void setCacheTokens(int cacheTokens) {
        this.cacheTokens = cacheTokens;
    }

    public double getInputCost() {
        return inputCost;
    }

    public void setInputCost(double inputCost) {
        this.inputCost = inputCost;
    }

    public double getOutputCost() {
        return outputCost;
    }

    public void setOutputCost(double outputCost) {
        this.outputCost = outputCost;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName == null ? "" : modelName;
    }

    public List<Map<String, Object>> getDetails() {
        return details;
    }

    public void setDetails(List<Map<String, Object>> details) {
        this.details = details == null ? new ArrayList<>() : new ArrayList<>(details);
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

    public Map<String, Object> model_dump() {
        return modelDump();
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : "";
    }

    private static List<Map<String, Object>> asDetails(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> detail = new LinkedHashMap<>();
                rawMap.forEach((key, entryValue) -> detail.put(String.valueOf(key), entryValue));
                result.add(detail);
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContextCompressionUsage that)) {
            return false;
        }
        return calls == that.calls
                && inputTokens == that.inputTokens
                && outputTokens == that.outputTokens
                && totalTokens == that.totalTokens
                && cacheTokens == that.cacheTokens
                && Double.compare(inputCost, that.inputCost) == 0
                && Double.compare(outputCost, that.outputCost) == 0
                && Double.compare(totalCost, that.totalCost) == 0
                && Objects.equals(modelName, that.modelName)
                && Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(calls, inputTokens, outputTokens, totalTokens, cacheTokens, inputCost, outputCost,
                totalCost, modelName, details);
    }
}
