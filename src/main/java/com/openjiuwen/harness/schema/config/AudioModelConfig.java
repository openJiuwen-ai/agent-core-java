package com.openjiuwen.harness.schema.config;

import java.util.Map;

/**
 * Mirrors Python's {@code AudioModelConfig} in {@code openjiuwen.harness.schema.config}.
 */
public class AudioModelConfig {
    public static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_OPENAI_AUDIO_TRANSCRIPTION_MODEL = "gpt-4o-transcribe";
    public static final String DEFAULT_OPENAI_AUDIO_QA_MODEL = "gpt-4o-audio-preview";
    public static final String DEFAULT_ACR_BASE_URL = "https://identify-ap-southeast-1.acrcloud.com/v1/identify";
    public static final int DEFAULT_AUDIO_HTTP_TIMEOUT = 20;
    public static final int DEFAULT_MAX_AUDIO_BYTES = 25 * 1024 * 1024;

    private String apiKey = "";
    private String baseUrl = DEFAULT_OPENAI_BASE_URL;
    private String transcriptionModel = DEFAULT_OPENAI_AUDIO_TRANSCRIPTION_MODEL;
    private String questionAnsweringModel = DEFAULT_OPENAI_AUDIO_QA_MODEL;
    private int maxRetries = 3;
    private int httpTimeout = DEFAULT_AUDIO_HTTP_TIMEOUT;
    private int maxAudioBytes = DEFAULT_MAX_AUDIO_BYTES;
    private String acrAccessKey = "";
    private String acrAccessSecret = "";
    private String acrBaseUrl = DEFAULT_ACR_BASE_URL;

    public static AudioModelConfig fromEnv() {
        return fromEnv(System.getenv());
    }

    public static AudioModelConfig fromEnv(Map<String, String> env) {
        AudioModelConfig config = new AudioModelConfig();
        config.setApiKey(firstPresent(env, "AUDIO_API_KEY", "OPENAI_API_KEY", ""));
        config.setBaseUrl(firstPresent(env, "AUDIO_BASE_URL", "AUDIO_API_BASE", "OPENAI_BASE_URL",
                DEFAULT_OPENAI_BASE_URL));
        config.setTranscriptionModel(firstPresent(env, "AUDIO_TRANSCRIPTION_MODEL", "AUDIO_MODEL_NAME",
                DEFAULT_OPENAI_AUDIO_TRANSCRIPTION_MODEL));
        config.setQuestionAnsweringModel(firstPresent(env, "AUDIO_QUESTION_ANSWERING_MODEL",
                DEFAULT_OPENAI_AUDIO_QA_MODEL));
        config.setMaxRetries(parseInt(env.get("AUDIO_MAX_RETRIES"), 3));
        config.setHttpTimeout(parseInt(env.get("AUDIO_HTTP_TIMEOUT"), DEFAULT_AUDIO_HTTP_TIMEOUT));
        config.setMaxAudioBytes(parseInt(env.get("AUDIO_MAX_AUDIO_BYTES"), DEFAULT_MAX_AUDIO_BYTES));
        config.setAcrAccessKey(env.getOrDefault("ACR_ACCESS_KEY", ""));
        config.setAcrAccessSecret(env.getOrDefault("ACR_ACCESS_SECRET", ""));
        config.setAcrBaseUrl(env.getOrDefault("ACR_BASE_URL", DEFAULT_ACR_BASE_URL));
        return config;
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl; }
    public String getTranscriptionModel() { return transcriptionModel; }
    public void setTranscriptionModel(String transcriptionModel) { this.transcriptionModel = transcriptionModel == null ? "" : transcriptionModel; }
    public String getQuestionAnsweringModel() { return questionAnsweringModel; }
    public void setQuestionAnsweringModel(String questionAnsweringModel) { this.questionAnsweringModel = questionAnsweringModel == null ? "" : questionAnsweringModel; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public int getHttpTimeout() { return httpTimeout; }
    public void setHttpTimeout(int httpTimeout) { this.httpTimeout = httpTimeout; }
    public int getMaxAudioBytes() { return maxAudioBytes; }
    public void setMaxAudioBytes(int maxAudioBytes) { this.maxAudioBytes = maxAudioBytes; }
    public String getAcrAccessKey() { return acrAccessKey; }
    public void setAcrAccessKey(String acrAccessKey) { this.acrAccessKey = acrAccessKey == null ? "" : acrAccessKey; }
    public String getAcrAccessSecret() { return acrAccessSecret; }
    public void setAcrAccessSecret(String acrAccessSecret) { this.acrAccessSecret = acrAccessSecret == null ? "" : acrAccessSecret; }
    public String getAcrBaseUrl() { return acrBaseUrl; }
    public void setAcrBaseUrl(String acrBaseUrl) { this.acrBaseUrl = acrBaseUrl == null ? "" : acrBaseUrl; }

    private static String firstPresent(Map<String, String> env, String firstKey, String fallback) {
        String value = env.get(firstKey);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String firstPresent(Map<String, String> env, String firstKey, String secondKey, String fallback) {
        String first = env.get(firstKey);
        if (first != null && !first.isBlank()) {
            return first;
        }
        String second = env.get(secondKey);
        return second == null || second.isBlank() ? fallback : second;
    }

    private static String firstPresent(Map<String, String> env, String firstKey, String secondKey, String thirdKey,
            String fallback) {
        String first = env.get(firstKey);
        if (first != null && !first.isBlank()) {
            return first;
        }
        String second = env.get(secondKey);
        if (second != null && !second.isBlank()) {
            return second;
        }
        String third = env.get(thirdKey);
        return third == null || third.isBlank() ? fallback : third;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
