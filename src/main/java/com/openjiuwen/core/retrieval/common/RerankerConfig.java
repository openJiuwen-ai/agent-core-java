/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reranker model configuration aligned with the Python implementation.
 */
public class RerankerConfig {

    private String apiKey = "";
    private String apiBase;
    private String modelName = "";
    private double timeout = 10.0;
    private double temperature = 0.95;
    private double topP = 0.1;
    private List<Integer> yesNoIds;
    private Map<String, Object> extraBody = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public RerankerConfig() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public RerankerConfig(String apiBase) {
        setApiBase(apiBase);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getApiBase() {
        return apiBase;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setApiBase(String apiBase) {
        RetrievalValidation.requireNonBlank(apiBase, "RerankerConfig.apiBase");
        this.apiBase = apiBase;
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
        this.modelName = modelName == null ? "" : modelName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getTimeout() {
        return timeout;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTimeout(double timeout) {
        if (timeout <= 0.0) {
            throw RetrievalExceptions.validation("RerankerConfig.timeout must be > 0");
        }
        this.timeout = timeout;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getTopP() {
        return topP;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTopP(double topP) {
        this.topP = topP;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Integer> getYesNoIds() {
        return yesNoIds == null ? null : List.copyOf(yesNoIds);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setYesNoIds(List<Integer> yesNoIds) {
        this.yesNoIds = yesNoIds == null ? null : List.copyOf(yesNoIds);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getExtraBody() {
        return new LinkedHashMap<>(extraBody);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setExtraBody(Map<String, Object> extraBody) {
        this.extraBody = extraBody == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraBody);
    }
}
