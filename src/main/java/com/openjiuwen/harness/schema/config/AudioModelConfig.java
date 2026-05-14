package com.openjiuwen.harness.schema.config;

/**
 * Mirrors Python's {@code AudioModelConfig} in {@code openjiuwen.harness.schema.config}.
 */
public class AudioModelConfig {
    private String apiKey = "";
    private String baseUrl = "";
    private String transcriptionModel = "";
    private String questionAnsweringModel = "";
    private String acrAccessKey = "";
    private String acrAccessSecret = "";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl; }
    public String getTranscriptionModel() { return transcriptionModel; }
    public void setTranscriptionModel(String transcriptionModel) { this.transcriptionModel = transcriptionModel == null ? "" : transcriptionModel; }
    public String getQuestionAnsweringModel() { return questionAnsweringModel; }
    public void setQuestionAnsweringModel(String questionAnsweringModel) { this.questionAnsweringModel = questionAnsweringModel == null ? "" : questionAnsweringModel; }
    public String getAcrAccessKey() { return acrAccessKey; }
    public void setAcrAccessKey(String acrAccessKey) { this.acrAccessKey = acrAccessKey == null ? "" : acrAccessKey; }
    public String getAcrAccessSecret() { return acrAccessSecret; }
    public void setAcrAccessSecret(String acrAccessSecret) { this.acrAccessSecret = acrAccessSecret == null ? "" : acrAccessSecret; }
}
