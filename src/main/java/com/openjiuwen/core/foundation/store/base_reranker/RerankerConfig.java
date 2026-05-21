/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.base_reranker;

import java.util.HashMap;
import java.util.Map;

/**
 * Reranker model configuration.
 * <p>
 * Mirrors Python's {@code RerankerConfig} model from
 * <code>foundation/store/base_reranker.py</code>.
 */
public class RerankerConfig {

    private String apiKey = "";
    private String apiBase;
    private String modelName = "";
    private double timeout = 10.0;
    private double temperature = 0.95;
    private double topP = 0.1;
    private int[] yesNoIds = null;
    private Map<String, Object> extraBody = new HashMap<>();

    public RerankerConfig() {
    }

    public RerankerConfig(String apiBase) {
        this.apiBase = apiBase;
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiBase() { return apiBase; }
    public void setApiBase(String apiBase) { this.apiBase = apiBase; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public double getTimeout() { return timeout; }
    public void setTimeout(double timeout) { this.timeout = timeout; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public double getTopP() { return topP; }
    public void setTopP(double topP) { this.topP = topP; }

    public int[] getYesNoIds() { return yesNoIds; }
    public void setYesNoIds(int[] yesNoIds) { this.yesNoIds = yesNoIds; }

    public Map<String, Object> getExtraBody() { return extraBody; }
    public void setExtraBody(Map<String, Object> extraBody) { this.extraBody = extraBody; }
}
