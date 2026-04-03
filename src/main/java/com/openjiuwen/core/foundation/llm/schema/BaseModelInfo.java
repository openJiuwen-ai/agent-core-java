/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Base model information — a simplified configuration used by higher-level components.
 * <p>
 * Mirrors Python's {@code BaseModelInfo} model.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseModelInfo {

    private static final String GREATER_THAN_ZERO_MESSAGE =
            "Input should be greater than 0 [type=greater_than, input_value=%d, input_type=int]";

    @JsonProperty("api_key")
    private String apiKey = "";

    @JsonProperty("api_base")
    private String apiBase;

    @JsonProperty("model")
    private String modelName = "";

    private Double temperature = 0.95;

    @JsonProperty("top_p")
    private Double topP = 0.1;

    @JsonProperty("stream")
    private boolean streaming = false;

    private int timeout = 60;
    @JsonProperty("verify_ssl")
    private boolean verifySsl = true;
    @JsonProperty("ssl_cert")
    private String sslCert;
    private Map<String, String> headers = new LinkedHashMap<>();

    private Map<String, Object> extraFields = new HashMap<>();

    @Builder
    public BaseModelInfo(String apiKey, String apiBase, String modelName, Double temperature, Double topP,
                         Boolean streaming, Integer timeout, Boolean verifySsl, String sslCert,
                         Map<String, String> headers, Map<String, Object> extraFields) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName == null ? "" : modelName;
        this.temperature = temperature == null ? 0.95 : temperature;
        this.topP = topP == null ? 0.1 : topP;
        this.streaming = streaming != null && streaming;
        this.timeout = timeout == null ? 60 : validatePositive(timeout);
        this.verifySsl = verifySsl == null || verifySsl;
        this.sslCert = sslCert;
        this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        this.extraFields = extraFields == null ? new HashMap<>() : new HashMap<>(extraFields);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    @JsonAnySetter
    public void setExtraField(String key, Object value) {
        if (extraFields == null) {
            extraFields = new HashMap<>();
        }
        extraFields.put(key, value);
    }

    public void setTimeout(int timeout) {
        this.timeout = validatePositive(timeout);
    }

    public void setExtraFields(Map<String, Object> extraFields) {
        this.extraFields = extraFields == null ? new HashMap<>() : new HashMap<>(extraFields);
    }

    public Map<String, String> getHeaders() {
        return new LinkedHashMap<>(headers);
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
    }

    private static int validatePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(GREATER_THAN_ZERO_MESSAGE.formatted(value));
        }
        return value;
    }
}
