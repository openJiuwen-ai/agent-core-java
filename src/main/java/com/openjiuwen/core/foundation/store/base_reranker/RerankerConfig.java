/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.base_reranker;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reranker model configuration.
 * <p>
 * Mirrors Python's {@code RerankerConfig} in
 * {@code openjiuwen/core/foundation/store/base_reranker.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RerankerConfig {

    @JsonProperty("api_key")
    @Builder.Default
    private String apiKey = "";

    @JsonProperty("api_base")
    private String apiBase;

    @JsonProperty("model_name")
    @JsonAlias("model")
    @Builder.Default
    private String modelName = "";

    @Builder.Default
    private double timeout = 10.0d;

    @JsonProperty("temperature")
    @Builder.Default
    private double temperature = 0.95d;

    @JsonProperty("top_p")
    @Builder.Default
    private double topP = 0.1d;

    @JsonProperty("yes_no_ids")
    private List<Integer> yesNoIds;

    @JsonProperty("extra_body")
    @Builder.Default
    private Map<String, Object> extraBody = new LinkedHashMap<>();

    public RerankerConfig(String apiBase) {
        this();
        this.apiBase = apiBase;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public String getApiBase() {
        return apiBase;
    }

    public void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName == null ? "" : modelName;
    }

    public double getTimeout() {
        return timeout;
    }

    public void setTimeout(double timeout) {
        this.timeout = timeout;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getTopP() {
        return topP;
    }

    public void setTopP(double topP) {
        this.topP = topP;
    }

    public List<Integer> getYesNoIds() {
        return yesNoIds == null ? null : List.copyOf(yesNoIds);
    }

    public void setYesNoIds(List<Integer> yesNoIds) {
        this.yesNoIds = yesNoIds == null ? null : List.copyOf(yesNoIds);
    }

    public void setYesNoIds(int[] yesNoIds) {
        if (yesNoIds == null) {
            this.yesNoIds = null;
            return;
        }
        List<Integer> values = new ArrayList<>(yesNoIds.length);
        for (int yesNoId : yesNoIds) {
            values.add(yesNoId);
        }
        this.yesNoIds = values;
    }

    public Map<String, Object> getExtraBody() {
        return extraBody == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraBody);
    }

    public void setExtraBody(Map<String, Object> extraBody) {
        this.extraBody = extraBody == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraBody);
    }
}
