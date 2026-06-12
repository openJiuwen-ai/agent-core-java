/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Model client connection configuration.
 *
 * <p>Mirrors Python's {@code ModelClientConfig} in
 * {@code openjiuwen/core/foundation/llm/schema/config.py}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelClientConfig {

    @Builder.Default
    @JsonProperty("client_id")
    private String clientId = UUID.randomUUID().toString();

    @JsonProperty("client_provider")
    private String clientProvider;

    @JsonProperty("api_key")
    private String apiKey;

    @JsonProperty("api_base")
    private String apiBase;

    @Builder.Default
    private double timeout = 60.0;

    @Builder.Default
    @JsonProperty("max_retries")
    private int maxRetries = 3;

    @Builder.Default
    @JsonProperty("verify_ssl")
    private boolean verifySsl = true;

    @JsonProperty("ssl_cert")
    private String sslCert;

    @Builder.Default
    @JsonProperty("custom_headers")
    private Map<String, Object> customHeaders = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> extraFields = new LinkedHashMap<>();

    public static ModelClientConfig of(ProviderType provider, String apiKey, String apiBase) {
        return ModelClientConfig.builder()
                .clientProvider(provider == null ? null : provider.getValue())
                .apiKey(apiKey)
                .apiBase(apiBase)
                .build();
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    @JsonAnySetter
    public void setExtraField(String key, Object value) {
        if (extraFields == null) {
            extraFields = new LinkedHashMap<>();
        }
        extraFields.put(key, value);
    }
}
