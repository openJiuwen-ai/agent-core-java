/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend that runs a model locally.
 * <p>
 * Mirrors Python's {@code LocalModelBackend} in
 * {@code openjiuwen/core/security/guardrail/backends.py}.
 */
public class LocalModelBackend extends GuardrailBackend {

    private final String modelPath;
    private final ModelOutputParser parser;
    private final String device;
    private final String riskType;

    protected Object model;
    protected Object tokenizer;
    protected boolean modelLoaded;

    public LocalModelBackend(LocalModelBackendConfig config) {
        this(
                config != null ? config.modelPath() : null,
                config != null ? config.parser() : null,
                config != null ? config.device() : "auto",
                config != null ? config.riskType() : "model_detection"
        );
    }

    public LocalModelBackend(String modelPath, ModelOutputParser parser, String device, String riskType) {
        this.modelPath = modelPath;
        this.parser = parser;
        this.device = device == null || device.isBlank() ? "auto" : device;
        this.riskType = riskType == null || riskType.isBlank() ? "model_detection" : riskType;
    }

    @Override
    public RiskAssessment analyze(GuardrailContext ctx) {
        String text = contextText(ctx);
        if (text.isBlank()) {
            return new RiskAssessment(false, RiskLevel.SAFE, null, 1.0d, null);
        }
        if (parser == null) {
            throw new IllegalStateException("LocalModelBackend requires parser");
        }
        ensureModelLoaded();
        return parser.parse(inference(text));
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

    public void cleanup() {
        model = null;
        tokenizer = null;
        modelLoaded = false;
    }

    public boolean isModelLoaded() {
        return modelLoaded;
    }

    public Map<String, Object> getModelInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("model_path", modelPath);
        info.put("device", device);
        info.put("model_loaded", modelLoaded);
        info.put("has_model", model != null);
        info.put("has_tokenizer", tokenizer != null);
        return info;
    }

    protected void ensureModelLoaded() {
        if (!modelLoaded) {
            loadModel();
            modelLoaded = true;
        }
    }

    protected void loadModel() {
        throw new UnsupportedOperationException("Local model inference runtime is not available in this Java translation");
    }

    protected Object inference(String text) {
        throw new UnsupportedOperationException("Local model inference runtime is not available in this Java translation");
    }
}
