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
 * 
 * @since 0.1.7
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
     * UsageMetadata.
     * 
     * @since 0.1.7
     */
    public UsageMetadata() {
    }

    /**
     * UsageMetadata.
     * 
     * @param code code
     * @param errMsg errMsg
     * @param prompt prompt
     * @param taskId taskId
     * @param modelName modelName
     * @param totalLatency totalLatency
     * @param firstTokenTime firstTokenTime
     * @param requestStartTime requestStartTime
     * @param inputTokens inputTokens
     * @param outputTokens outputTokens
     * @param totalTokens totalTokens
     * @param cacheTokens cacheTokens
     * @since 0.1.7
     */
    public UsageMetadata(int code, String errMsg, String prompt, String taskId, String modelName, double totalLatency,
            String firstTokenTime, String requestStartTime, int inputTokens, int outputTokens, int totalTokens,
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
     * getCode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getCode() {
        return code;
    }

    /**
     * setCode.
     * 
     * @param code code
     * @since 0.1.7
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * getErrMsg.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getErrMsg() {
        return errMsg;
    }

    /**
     * setErrMsg.
     * 
     * @param errMsg errMsg
     * @since 0.1.7
     */
    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    /**
     * getPrompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getPrompt() {
        return prompt;
    }

    /**
     * setPrompt.
     * 
     * @param prompt prompt
     * @since 0.1.7
     */
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    /**
     * getTaskId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * setTaskId.
     * 
     * @param taskId taskId
     * @since 0.1.7
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /**
     * getModelName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * setModelName.
     * 
     * @param modelName modelName
     * @since 0.1.7
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    /**
     * getTotalLatency.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getTotalLatency() {
        return totalLatency;
    }

    /**
     * setTotalLatency.
     * 
     * @param totalLatency totalLatency
     * @since 0.1.7
     */
    public void setTotalLatency(double totalLatency) {
        this.totalLatency = totalLatency;
    }

    /**
     * getFirstTokenTime.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getFirstTokenTime() {
        return firstTokenTime;
    }

    /**
     * setFirstTokenTime.
     * 
     * @param firstTokenTime firstTokenTime
     * @since 0.1.7
     */
    public void setFirstTokenTime(String firstTokenTime) {
        this.firstTokenTime = firstTokenTime;
    }

    /**
     * getRequestStartTime.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getRequestStartTime() {
        return requestStartTime;
    }

    /**
     * setRequestStartTime.
     * 
     * @param requestStartTime requestStartTime
     * @since 0.1.7
     */
    public void setRequestStartTime(String requestStartTime) {
        this.requestStartTime = requestStartTime;
    }

    /**
     * getInputTokens.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getInputTokens() {
        return inputTokens;
    }

    /**
     * setInputTokens.
     * 
     * @param inputTokens inputTokens
     * @since 0.1.7
     */
    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }

    /**
     * getOutputTokens.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getOutputTokens() {
        return outputTokens;
    }

    /**
     * setOutputTokens.
     * 
     * @param outputTokens outputTokens
     * @since 0.1.7
     */
    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }

    /**
     * getTotalTokens.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getTotalTokens() {
        return totalTokens;
    }

    /**
     * setTotalTokens.
     * 
     * @param totalTokens totalTokens
     * @since 0.1.7
     */
    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    /**
     * getCacheTokens.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getCacheTokens() {
        return cacheTokens;
    }

    /**
     * setCacheTokens.
     * 
     * @param cacheTokens cacheTokens
     * @since 0.1.7
     */
    public void setCacheTokens(int cacheTokens) {
        this.cacheTokens = cacheTokens;
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static UsageMetadataBuilder builder() {
        return new UsageMetadataBuilder();
    }

    /**
     * UsageMetadataBuilder.
     * 
     * @since 0.1.7
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
         * code.
         * 
         * @param code code
         * @return the result
         * @since 0.1.7
         */
        public UsageMetadataBuilder code(int code) {
            this.code = code;
            return this;
        }

        /**
         * errMsg.
         * 
         * @param errMsg errMsg
         * @return the result
         * @since 0.1.7
         */
        public UsageMetadataBuilder errMsg(String errMsg) {
            this.errMsg = errMsg;
            return this;
        }

        /**
         * prompt.
         * 
         * @param prompt prompt
         * @return the result
         * @since 0.1.7
         */
        public UsageMetadataBuilder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        /**
         * taskId.
         * 
         * @param taskId taskId
         * @return the result
         * @since 0.1.7
         */
        public UsageMetadataBuilder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        /**
         * modelName.
         * 
         * @param modelName modelName
         * @return the result
         * @since 0.1.7
         */
        public UsageMetadataBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * totalLatency.
         * 
         * @param totalLatency totalLatency
         * @return the result
         * @since 0.1.7
         */
        public UsageMetadataBuilder totalLatency(double totalLatency) {
            this.totalLatency = totalLatency;
            return this;
        }

        /**
         * firstTokenTime.
         * 
         * @param firstTokenTime firstTokenTime
         * @return the result
         * @since 0.1.7
         */
        public UsageMetadataBuilder firstTokenTime(String firstTokenTime) {
            this.firstTokenTime = firstTokenTime;
            return this;
        }

        /**
         * requestStartTime.
         * 
         * @param requestStartTime requestStartTime
         * @return the result
         * @since 0.1.7
         */
        public UsageMetadataBuilder requestStartTime(String requestStartTime) {
            this.requestStartTime = requestStartTime;
            return this;
        }

        /**
         * inputTokens.
         * 
         * @param inputTokens inputTokens
         * @return the result
         * @since 0.1.7
         */
        public UsageMetadataBuilder inputTokens(int inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        /**
         * outputTokens.
         * 
         * @param outputTokens outputTokens
         * @return the result
         * @since 0.1.7
         */
        public UsageMetadataBuilder outputTokens(int outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        /**
         * totalTokens.
         * 
         * @param totalTokens totalTokens
         * @return the result
         * @since 0.1.7
         */
        public UsageMetadataBuilder totalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        /**
         * cacheTokens.
         * 
         * @param cacheTokens cacheTokens
         * @return the result
         * @since 0.1.7
         */
        public UsageMetadataBuilder cacheTokens(int cacheTokens) {
            this.cacheTokens = cacheTokens;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public UsageMetadata build() {
            return new UsageMetadata(code, errMsg, prompt, taskId, modelName, totalLatency, firstTokenTime,
                    requestStartTime, inputTokens, outputTokens, totalTokens, cacheTokens);
        }
    }
}
