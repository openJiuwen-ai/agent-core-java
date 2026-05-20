/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;

/**
 * Usage metadata returned by LLM responses.
 * <p>
 * Mirrors Python's {@code UsageMetadata} model.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageMetadata implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int code = 0;

    @JsonProperty("err_msg")
    private String errMsg = "";

    private String prompt = "";

    @JsonProperty("task_id")
    private String taskId = "";

    @JsonProperty("model_name")
    private String modelName = "";

    @JsonProperty("total_latency")
    private double totalLatency = 0.0;

    @JsonProperty("first_token_time")
    private String firstTokenTime = "";

    @JsonProperty("request_start_time")
    private String requestStartTime = "";

    @JsonProperty("input_tokens")
    private int inputTokens = 0;

    @JsonProperty("output_tokens")
    private int outputTokens = 0;

    @JsonProperty("total_tokens")
    private int totalTokens = 0;

    @JsonProperty("cache_tokens")
    private int cacheTokens = 0;

    /**
     * Auto-generated for codecheck compliance.
     */
    public UsageMetadata() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public UsageMetadata(int code,
                         String errMsg,
                         String prompt,
                         String taskId,
                         String modelName,
                         double totalLatency,
                         String firstTokenTime,
                         String requestStartTime,
                         int inputTokens,
                         int outputTokens,
                         int totalTokens,
                         int cacheTokens) {
        this.code = code;
        this.errMsg = errMsg;
        this.prompt = prompt;
        this.taskId = taskId;
        this.modelName = modelName;
        this.totalLatency = totalLatency;
        this.firstTokenTime = firstTokenTime;
        this.requestStartTime = requestStartTime;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.cacheTokens = cacheTokens;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getCode() {
        return code;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getErrMsg() {
        return errMsg;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getPrompt() {
        return prompt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getTotalLatency() {
        return totalLatency;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTotalLatency(double totalLatency) {
        this.totalLatency = totalLatency;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getFirstTokenTime() {
        return firstTokenTime;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setFirstTokenTime(String firstTokenTime) {
        this.firstTokenTime = firstTokenTime;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getRequestStartTime() {
        return requestStartTime;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setRequestStartTime(String requestStartTime) {
        this.requestStartTime = requestStartTime;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getInputTokens() {
        return inputTokens;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getOutputTokens() {
        return outputTokens;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getTotalTokens() {
        return totalTokens;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getCacheTokens() {
        return cacheTokens;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCacheTokens(int cacheTokens) {
        this.cacheTokens = cacheTokens;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static UsageMetadataBuilder builder() {
        return new UsageMetadataBuilder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class UsageMetadataBuilder {
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

        /**
         * Auto-generated for codecheck compliance.
         */
        public UsageMetadataBuilder code(int code) {
            this.code = code;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public UsageMetadataBuilder errMsg(String errMsg) {
            this.errMsg = errMsg;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public UsageMetadataBuilder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public UsageMetadataBuilder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public UsageMetadataBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public UsageMetadataBuilder totalLatency(double totalLatency) {
            this.totalLatency = totalLatency;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public UsageMetadataBuilder firstTokenTime(String firstTokenTime) {
            this.firstTokenTime = firstTokenTime;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public UsageMetadataBuilder requestStartTime(String requestStartTime) {
            this.requestStartTime = requestStartTime;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public UsageMetadataBuilder inputTokens(int inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public UsageMetadataBuilder outputTokens(int outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public UsageMetadataBuilder totalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public UsageMetadataBuilder cacheTokens(int cacheTokens) {
            this.cacheTokens = cacheTokens;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public UsageMetadata build() {
            return new UsageMetadata(code, errMsg, prompt, taskId, modelName, totalLatency, firstTokenTime,
                    requestStartTime, inputTokens, outputTokens, totalTokens, cacheTokens);
        }
    }
}
