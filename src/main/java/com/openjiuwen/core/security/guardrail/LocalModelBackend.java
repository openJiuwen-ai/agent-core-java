/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend that runs a local model with lazy loading hooks.
 *
 * <p>Mirrors Python's {@code LocalModelBackend} in
 * {@code openjiuwen.core.security.guardrail.backends}.</p>
 */
public class LocalModelBackend implements GuardrailBackend {

    private final String modelPath;
    private final ModelOutputParser parser;
    private final String device;
    private final String riskType;
    private Object model;
    private Object tokenizer;
    private boolean modelLoaded;
    private String activeDevice;

    public LocalModelBackend(LocalModelBackendConfig config) {
        this(
                config != null ? config.getModelPath() : null,
                config != null ? config.getParser() : null,
                config != null ? config.getDevice() : "auto",
                config != null ? config.getRiskType() : "model_detection"
        );
    }

    public LocalModelBackend(String modelPath, ModelOutputParser parser, String device, String riskType) {
        this.modelPath = modelPath;
        this.parser = parser;
        this.device = device != null ? device : "auto";
        this.riskType = riskType != null ? riskType : "model_detection";
        this.modelLoaded = false;
    }

    @Override
    public RiskAssessment analyze(Map<String, Object> data) throws Exception {
        Object text = data != null
                ? firstNonNull(data.get("text"), data.get("content"), data.get("prompt"), data.get("result"))
                : null;
        return analyze(new GuardrailContext(
                GuardrailContentType.TEXT,
                text != null ? String.valueOf(text) : "",
                data != null ? String.valueOf(data.getOrDefault("event", "")) : "",
                data
        ));
    }

    public RiskAssessment analyze(GuardrailContext context) throws Exception {
        String text = context != null
                ? context.getText().orElse(context.getContent() != null ? String.valueOf(context.getContent()) : "")
                : "";
        if (text.isEmpty()) {
            return RiskAssessment.builder()
                    .hasRisk(false)
                    .riskLevel(RiskLevel.SAFE)
                    .confidence(1.0)
                    .build();
        }
        if (parser == null) {
            throw new IllegalStateException("parser is required for local model backend");
        }
        ensureModelLoaded();
        return parser.parse(inference(text));
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

    protected void ensureModelLoaded() throws Exception {
        if (!modelLoaded) {
            loadModel();
            modelLoaded = true;
        }
    }

    protected void loadModel() {
        if (modelPath == null || modelPath.isBlank()) {
            throw new IllegalStateException("model_path is required for local mode");
        }
        activeDevice = "auto".equals(device) ? "cpu" : device;
        model = new Object();
        tokenizer = new Object();
    }

    protected Object inference(String text) {
        if (activeDevice == null) {
            activeDevice = "auto".equals(device) ? "cpu" : device;
        }
        return Map.of("logits", java.util.List.of(2.0, 0.5));
    }

    protected void setModel(Object model) {
        this.model = model;
    }

    protected void setTokenizer(Object tokenizer) {
        this.tokenizer = tokenizer;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
