package com.openjiuwen.harness.schema.config;

/**
 * Mirrors Python's {@code VisionModelConfig} in {@code openjiuwen.harness.schema.config}.
 */
public class VisionModelConfig {
    private String apiKey = "";
    private String baseUrl = "";
    private String model = "";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model == null ? "" : model; }
}
