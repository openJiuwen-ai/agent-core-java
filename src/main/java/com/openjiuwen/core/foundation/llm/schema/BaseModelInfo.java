/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code BaseModelInfo} in
 * {@code openjiuwen/core/foundation/llm/schema/mode_info.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseModelInfo {

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
    private double temperature = 0.95d;

    @JsonProperty("top_p")
    @Builder.Default
    private double topP = 0.1d;

    @JsonProperty("streaming")
    @JsonAlias("stream")
    @Builder.Default
    private boolean streaming = false;

    @Builder.Default
    private int timeout = 60;

    @JsonProperty("custom_headers")
    private Map<String, Object> customHeaders;

    @JsonProperty("http_version")
    private ModelHttpVersion httpVersion;

    @Builder.Default
    private Map<String, Object> extraFields = new LinkedHashMap<>();

    public BaseModelInfo(String apiKey, String apiBase, String modelName, double temperature, double topP,
                         boolean streaming, int timeout, Map<String, Object> customHeaders,
                         Map<String, Object> extraFields) {
        this(apiKey, apiBase, modelName, temperature, topP, streaming, timeout, customHeaders, null, extraFields);
    }

    public BaseModelInfo(String apiKey, String apiBase, String modelName, Double temperature, Double topP,
                         Boolean streaming, Integer timeout, ModelHttpVersion httpVersion, Boolean verifySsl,
                         String sslCert, Map<String, String> headers, Map<String, Object> extraFields) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName == null ? "" : modelName;
        this.temperature = temperature == null ? 0.95d : temperature;
        this.topP = topP == null ? 0.1d : topP;
        this.streaming = streaming != null && streaming;
        this.timeout = timeout == null ? 60 : timeout;
        this.httpVersion = httpVersion;
        this.customHeaders = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        this.extraFields = extraFields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraFields);
        if (verifySsl != null) {
            this.extraFields.put("verify_ssl", verifySsl);
        }
        if (sslCert != null) {
            this.extraFields.put("ssl_cert", sslCert);
        }
    }

    @JsonAnySetter
    public void putExtraField(String key, Object value) {
        if (!isDeclaredField(key)) {
            extraFields.put(key, value);
        }
    }

    public void setExtraField(String key, Object value) {
        putExtraField(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    public void setExtraFields(Map<String, Object> extraFields) {
        this.extraFields = extraFields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraFields);
    }

    public void setTimeout(int timeout) {
        this.timeout = validateTimeout(timeout);
    }

    private static int validateTimeout(int timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be greater than 0");
        }
        return timeout;
    }

    public static class BaseModelInfoBuilder {
        public BaseModelInfoBuilder timeout(int timeout) {
            this.timeout$value = validateTimeout(timeout);
            this.timeout$set = true;
            return this;
        }
    }

    public Map<String, String> getHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (customHeaders != null) {
            customHeaders.forEach((key, value) -> {
                if (key != null && value != null) {
                    headers.put(key, Objects.toString(value));
                }
            });
        }
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.customHeaders = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
    }

    private boolean isDeclaredField(String key) {
        return "api_key".equals(key)
                || "api_base".equals(key)
                || "model_name".equals(key)
                || "model".equals(key)
                || "temperature".equals(key)
                || "top_p".equals(key)
                || "streaming".equals(key)
                || "stream".equals(key)
                || "timeout".equals(key)
                || "http_version".equals(key)
                || "custom_headers".equals(key);
    }
}
