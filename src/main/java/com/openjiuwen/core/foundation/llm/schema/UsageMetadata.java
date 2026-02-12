// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * LLM调用的使用元数据，记录token使用信息和延迟等。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/message.py - UsageMetadata
 */
public class UsageMetadata {
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

    public UsageMetadata() {
    }

    // Getters and Setters
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

    /**
     * 转换为Map格式
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
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
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UsageMetadata that = (UsageMetadata) o;
        return code == that.code &&
                inputTokens == that.inputTokens &&
                outputTokens == that.outputTokens &&
                totalTokens == that.totalTokens &&
                cacheTokens == that.cacheTokens &&
                Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, modelName, inputTokens, outputTokens, totalTokens, cacheTokens);
    }
}

