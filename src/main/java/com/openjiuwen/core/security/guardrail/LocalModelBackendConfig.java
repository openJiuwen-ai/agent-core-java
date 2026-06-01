/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

/**
 * Configuration for local model guardrail backend.
 *
 * <p>Mirrors Python's {@code LocalModelBackendConfig} in
 * {@code openjiuwen.core.security.guardrail.backends}.</p>
 */
public class LocalModelBackendConfig {

    private final String modelPath;
    private final ModelOutputParser parser;
    private final String device;
    private final String riskType;

    public LocalModelBackendConfig(String modelPath) {
        this(modelPath, null, "auto", "model_detection");
    }

    public LocalModelBackendConfig(String modelPath, ModelOutputParser parser, String device, String riskType) {
        this.modelPath = modelPath;
        this.parser = parser;
        this.device = device != null ? device : "auto";
        this.riskType = riskType != null ? riskType : "model_detection";
    }

    public String getModelPath() {
        return modelPath;
    }

    public ModelOutputParser getParser() {
        return parser;
    }

    public String getDevice() {
        return device;
    }

    public String getRiskType() {
        return riskType;
    }
}
