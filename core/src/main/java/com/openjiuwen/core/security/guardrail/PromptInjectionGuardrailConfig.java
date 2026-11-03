/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.List;
import java.util.Map;

/**
 * Configuration for {@link PromptInjectionGuardrail}.
 *
 * <p>Mirrors Python's {@code PromptInjectionGuardrailConfig} in
 * {@code openjiuwen/core/security/guardrail/builtin.py}.</p>
 */
public class PromptInjectionGuardrailConfig {

    private String mode = "rules";
    private String modelType;
    private String apiUrl;
    private String apiKey;
    private double timeout = 30.0d;
    private String modelPath;
    private String device = "auto";
    private List<String> customPatterns;
    private RiskLevel riskLevel = RiskLevel.HIGH;
    private Map<String, Double> bertThresholds;
    private int attackClassId = 1;
    private String qwenRiskType = "content_risk";
    private ModelOutputParser parser;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public double getTimeout() {
        return timeout;
    }

    public void setTimeout(double timeout) {
        this.timeout = timeout;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public List<String> getCustomPatterns() {
        return customPatterns;
    }

    public void setCustomPatterns(List<String> customPatterns) {
        this.customPatterns = customPatterns;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Map<String, Double> getBertThresholds() {
        return bertThresholds;
    }

    public void setBertThresholds(Map<String, Double> bertThresholds) {
        this.bertThresholds = bertThresholds;
    }

    public int getAttackClassId() {
        return attackClassId;
    }

    public void setAttackClassId(int attackClassId) {
        this.attackClassId = attackClassId;
    }

    public String getQwenRiskType() {
        return qwenRiskType;
    }

    public void setQwenRiskType(String qwenRiskType) {
        this.qwenRiskType = qwenRiskType;
    }

    public ModelOutputParser getParser() {
        return parser;
    }

    public void setParser(ModelOutputParser parser) {
        this.parser = parser;
    }
}
