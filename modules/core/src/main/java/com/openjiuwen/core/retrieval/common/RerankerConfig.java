/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reranker model configuration aligned with the Python implementation.
 * 
 * @since 0.1.7
 */
public class RerankerConfig {
    private String apiKey = "";
    private String apiBase;
    private String modelName = "";
    private double timeout = 10.0;
    private double temperature = 0.95;
    private double topP = 0.1;
    private List<Integer> yesNoIds;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> extraBody = new LinkedHashMap<>();

    /**
     * RerankerConfig.
     * 
     * @since 0.1.7
     */
    public RerankerConfig() {
    }

    /**
     * RerankerConfig.
     * 
     * @param apiBase apiBase
     * @since 0.1.7
     */
    public RerankerConfig(String apiBase) {
        setApiBase(apiBase);
    }

    /**
     * getApiKey.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * setApiKey.
     * 
     * @param apiKey apiKey
     * @since 0.1.7
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    /**
     * getApiBase.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getApiBase() {
        return apiBase;
    }

    /**
     * setApiBase.
     * 
     * @param apiBase apiBase
     * @since 0.1.7
     */
    public void setApiBase(String apiBase) {
        RetrievalValidation.requireNonBlank(apiBase, "RerankerConfig.apiBase");
        this.apiBase = apiBase;
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
        this.modelName = modelName == null ? "" : modelName;
    }

    /**
     * getTimeout.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getTimeout() {
        return timeout;
    }

    /**
     * setTimeout.
     * 
     * @param timeout timeout
     * @since 0.1.7
     */
    public void setTimeout(double timeout) {
        if (timeout <= 0.0) {
            throw RetrievalExceptions.validation("RerankerConfig.timeout must be > 0");
        }
        this.timeout = timeout;
    }

    /**
     * getTemperature.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * setTemperature.
     * 
     * @param temperature temperature
     * @since 0.1.7
     */
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    /**
     * getTopP.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getTopP() {
        return topP;
    }

    /**
     * setTopP.
     * 
     * @param topP topP
     * @since 0.1.7
     */
    public void setTopP(double topP) {
        this.topP = topP;
    }

    /**
     * getYesNoIds.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Integer> getYesNoIds() {
        return yesNoIds == null ? null : List.copyOf(yesNoIds);
    }

    /**
     * setYesNoIds.
     * 
     * @param yesNoIds yesNoIds
     * @since 0.1.7
     */
    public void setYesNoIds(List<Integer> yesNoIds) {
        this.yesNoIds = yesNoIds == null ? null : List.copyOf(yesNoIds);
    }

    /**
     * getExtraBody.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getExtraBody() {
        return new LinkedHashMap<>(extraBody);
    }

    /**
     * setExtraBody.
     * 
     * @param extraBody extraBody
     * @since 0.1.7
     */
    public void setExtraBody(Map<String, Object> extraBody) {
        this.extraBody = extraBody == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraBody);
    }
}
