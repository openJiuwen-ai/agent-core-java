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

import java.io.Serial;
import java.io.Serializable;

/**
 * Usage metadata returned by LLM responses.
 * <p>
 * Mirrors Python's {@code UsageMetadata} model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageMetadata implements Serializable {

    @Serial
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
    private double totalLatency = 0.0;

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
}
