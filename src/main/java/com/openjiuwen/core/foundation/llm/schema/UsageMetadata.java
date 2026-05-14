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

    public static UsageMetadataBuilder builder() {
        return new UsageMetadataBuilder();
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public double getTotalLatency() {
        return totalLatency;
    }

    public void setTotalLatency(double totalLatency) {
        this.totalLatency = totalLatency;
    }

    public String getFirstTokenTime() {
        return firstTokenTime;
    }

    public void setFirstTokenTime(String firstTokenTime) {
        this.firstTokenTime = firstTokenTime;
    }

    public String getRequestStartTime() {
        return requestStartTime;
    }

    public void setRequestStartTime(String requestStartTime) {
        this.requestStartTime = requestStartTime;
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

    public static final class UsageMetadataBuilder {
        private int code = 0;
        private String errMsg = "";
        private String prompt = "";
        private String taskId = "";
        private String modelName = "";
        private double totalLatency = 0.0;
        private String firstTokenTime = "";
        private String requestStartTime = "";
        private int inputTokens = 0;
        private int outputTokens = 0;
        private int totalTokens = 0;
        private int cacheTokens = 0;

        public UsageMetadataBuilder code(int code) { this.code = code; return this; }
        public UsageMetadataBuilder errMsg(String errMsg) { this.errMsg = errMsg; return this; }
        public UsageMetadataBuilder prompt(String prompt) { this.prompt = prompt; return this; }
        public UsageMetadataBuilder taskId(String taskId) { this.taskId = taskId; return this; }
        public UsageMetadataBuilder modelName(String modelName) { this.modelName = modelName; return this; }
        public UsageMetadataBuilder totalLatency(double totalLatency) { this.totalLatency = totalLatency; return this; }
        public UsageMetadataBuilder firstTokenTime(String firstTokenTime) { this.firstTokenTime = firstTokenTime; return this; }
        public UsageMetadataBuilder requestStartTime(String requestStartTime) { this.requestStartTime = requestStartTime; return this; }
        public UsageMetadataBuilder inputTokens(int inputTokens) { this.inputTokens = inputTokens; return this; }
        public UsageMetadataBuilder outputTokens(int outputTokens) { this.outputTokens = outputTokens; return this; }
        public UsageMetadataBuilder totalTokens(int totalTokens) { this.totalTokens = totalTokens; return this; }
        public UsageMetadataBuilder cacheTokens(int cacheTokens) { this.cacheTokens = cacheTokens; return this; }

        public UsageMetadata build() {
            UsageMetadata metadata = new UsageMetadata();
            metadata.code = this.code;
            metadata.errMsg = this.errMsg;
            metadata.prompt = this.prompt;
            metadata.taskId = this.taskId;
            metadata.modelName = this.modelName;
            metadata.totalLatency = this.totalLatency;
            metadata.firstTokenTime = this.firstTokenTime;
            metadata.requestStartTime = this.requestStartTime;
            metadata.inputTokens = this.inputTokens;
            metadata.outputTokens = this.outputTokens;
            metadata.totalTokens = this.totalTokens;
            metadata.cacheTokens = this.cacheTokens;
            return metadata;
        }
    }
}
