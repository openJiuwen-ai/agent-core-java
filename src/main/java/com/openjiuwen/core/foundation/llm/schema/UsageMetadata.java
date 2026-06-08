/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code UsageMetadata} in
 * {@code openjiuwen/core/foundation/llm/schema/message.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageMetadata {

    @Builder.Default
    private int code = 0;

    @Builder.Default
    @JsonProperty("err_msg")
    private String errMsg = "";

    @Builder.Default
    private String prompt = "";

    @Builder.Default
    @JsonProperty("task_id")
    private String taskId = "";

    @Builder.Default
    @JsonProperty("model_name")
    private String modelName = "";

    @Builder.Default
    @JsonProperty("total_latency")
    private double totalLatency = 0.0d;

    @Builder.Default
    @JsonProperty("first_token_time")
    private String firstTokenTime = "";

    @Builder.Default
    @JsonProperty("request_start_time")
    private String requestStartTime = "";

    @Builder.Default
    @JsonProperty("input_tokens")
    private int inputTokens = 0;

    @Builder.Default
    @JsonProperty("output_tokens")
    private int outputTokens = 0;

    @Builder.Default
    @JsonProperty("total_tokens")
    private int totalTokens = 0;

    @Builder.Default
    @JsonProperty("cache_tokens")
    private int cacheTokens = 0;

    @Builder.Default
    @JsonProperty("input_cost")
    private double inputCost = 0.0d;

    @Builder.Default
    @JsonProperty("output_cost")
    private double outputCost = 0.0d;

    @Builder.Default
    @JsonProperty("total_cost")
    private double totalCost = 0.0d;

    public Map<String, Object> modelDump() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("err_msg", errMsg);
        result.put("prompt", prompt);
        result.put("task_id", taskId);
        result.put("model_name", modelName);
        result.put("total_latency", totalLatency);
        result.put("first_token_time", firstTokenTime);
        result.put("request_start_time", requestStartTime);
        result.put("input_tokens", inputTokens);
        result.put("output_tokens", outputTokens);
        result.put("total_tokens", totalTokens);
        result.put("cache_tokens", cacheTokens);
        result.put("input_cost", inputCost);
        result.put("output_cost", outputCost);
        result.put("total_cost", totalCost);
        return result;
    }

    public Map<String, Object> model_dump() {
        return modelDump();
    }
}
