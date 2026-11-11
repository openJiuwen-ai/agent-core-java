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

import java.io.Serializable;
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
public class UsageMetadata implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

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

    /**
     * Time to the first chunk that carries model output, in <b>seconds</b>.
     *
     * <p>Measured from request dispatch to the first stream chunk holding
     * content, reasoning, or a tool-call fragment. Role-only deltas, heartbeats,
     * usage-only and finish-only frames do not count — see
     * {@code AssistantMessageChunk#carriesOutputToken()}.</p>
     *
     * <p>This is the classic TTFT, <b>not</b> the standard
     * {@code gen_ai.response.time_to_first_chunk}, which measures the first
     * chunk of any kind and can therefore be several seconds smaller on a
     * reasoning model. The tracer writes it to the project key
     * {@code openjiuwen.gen_ai.response.time_to_first_token_ms}; the standard
     * key is left unset because Java cannot observe the raw first frame
     * without changes in the model clients.</p>
     *
     * <p>Distinct from {@link #firstTokenTime}, which is a "first token
     * instant" timestamp string (same family as {@link #requestStartTime});
     * this field is the elapsed duration. Kept separate so downstream consumers
     * never have to reinterpret one field two ways.</p>
     *
     * <p>{@code null} means "not measured" — non-streaming calls, errors,
     * cancellations, or providers that sent no output chunk. Callers must not
     * substitute {@code 0}, which would be a valid measurement.</p>
     */
    @JsonProperty("ttft_seconds")
    private Double ttftSeconds;

    /**
     * Average time per output token between the first and last output-bearing
     * chunk, in <b>milliseconds</b>.
     *
     * <p>Divided by {@code outputTokens - 1}, not by the chunk count: a single
     * chunk may carry several tokens, so chunk count is not a token count.
     * {@code null} when fewer than two output tokens were produced, or when
     * timing data is unavailable.</p>
     */
    @JsonProperty("inter_token_latency_ms")
    private Double interTokenLatencyMs;

    /**
     * Reasoning phase duration in <b>milliseconds</b>, first to last chunk
     * carrying {@code reasoning_content}.
     *
     * <p>{@code null} when no reasoning fragment was streamed, so consumers can
     * tell "not a reasoning call" from "reasoning took no measurable time".</p>
     */
    @JsonProperty("reasoning_duration_ms")
    private Double reasoningDurationMs;

    /**
     * Number of retries the client attempted for this call.
     *
     * <p>Counted from retry events, so {@code 0} means "no retry occurred" and
     * is a real measurement rather than a missing value. Stream-only: the
     * counter is fed by the retry listener installed on the streaming path.</p>
     */
    @JsonProperty("retry_count")
    private Integer retryCount;

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
        if (ttftSeconds != null) {
            result.put("ttft_seconds", ttftSeconds);
        }
        if (interTokenLatencyMs != null) {
            result.put("inter_token_latency_ms", interTokenLatencyMs);
        }
        if (reasoningDurationMs != null) {
            result.put("reasoning_duration_ms", reasoningDurationMs);
        }
        if (retryCount != null) {
            result.put("retry_count", retryCount);
        }
        return result;
    }

    public Map<String, Object> model_dump() {
        return modelDump();
    }
}
