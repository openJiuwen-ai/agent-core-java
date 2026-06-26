/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializable audio model configuration.
 *
 * <p>Mirrors Python's {@code AudioModelSpec} in
 * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
 */
public class AudioModelSpec {

    public static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_ACR_BASE_URL = "";
    public static final String DEFAULT_OPENAI_AUDIO_TRANSCRIPTION_MODEL = "whisper-1";
    public static final String DEFAULT_OPENAI_AUDIO_QA_MODEL = "gpt-4o-audio-preview";
    public static final int DEFAULT_AUDIO_HTTP_TIMEOUT = 60;
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

    public Map<String, Object> build() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("api_key", apiKey);
        values.put("base_url", baseUrl);
        values.put("transcription_model", transcriptionModel);
        values.put("question_answering_model", questionAnsweringModel);
        values.put("max_retries", maxRetries);
        values.put("http_timeout", httpTimeout);
        values.put("max_audio_bytes", maxAudioBytes);
        values.put("acr_access_key", acrAccessKey);
        values.put("acr_access_secret", acrAccessSecret);
        values.put("acr_base_url", acrBaseUrl);
        return values;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? DEFAULT_OPENAI_BASE_URL : baseUrl;
    }

    public String getTranscriptionModel() {
        return transcriptionModel;
    }

    public void setTranscriptionModel(String transcriptionModel) {
        this.transcriptionModel = transcriptionModel == null
                ? DEFAULT_OPENAI_AUDIO_TRANSCRIPTION_MODEL : transcriptionModel;
    }

    public String getQuestionAnsweringModel() {
        return questionAnsweringModel;
    }

    public void setQuestionAnsweringModel(String questionAnsweringModel) {
        this.questionAnsweringModel = questionAnsweringModel == null
                ? DEFAULT_OPENAI_AUDIO_QA_MODEL : questionAnsweringModel;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getHttpTimeout() {
        return httpTimeout;
    }

    public void setHttpTimeout(int httpTimeout) {
        this.httpTimeout = httpTimeout;
    }

    public int getMaxAudioBytes() {
        return maxAudioBytes;
    }

    public void setMaxAudioBytes(int maxAudioBytes) {
        this.maxAudioBytes = maxAudioBytes;
    }

    public String getAcrAccessKey() {
        return acrAccessKey;
    }

    public void setAcrAccessKey(String acrAccessKey) {
        this.acrAccessKey = acrAccessKey == null ? "" : acrAccessKey;
    }

    public String getAcrAccessSecret() {
        return acrAccessSecret;
    }

    public void setAcrAccessSecret(String acrAccessSecret) {
        this.acrAccessSecret = acrAccessSecret == null ? "" : acrAccessSecret;
    }

    public String getAcrBaseUrl() {
        return acrBaseUrl;
    }

    public void setAcrBaseUrl(String acrBaseUrl) {
        this.acrBaseUrl = acrBaseUrl == null ? DEFAULT_ACR_BASE_URL : acrBaseUrl;
    }
}
