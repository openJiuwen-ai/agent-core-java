/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code InterruptRequest} in
 * {@code openjiuwen/core/single_agent/interrupt/response.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InterruptRequest {
    @JsonProperty("message")
    private String message = "";

    @JsonProperty("payload_schema")
    private Map<String, Object> payloadSchema = new LinkedHashMap<>();

    @JsonProperty("auto_confirm_key")
    private String autoConfirmKey = "";

    @JsonProperty("ui_options")
    private List<Map<String, Object>> uiOptions;

    private final Map<String, Object> extraFields = new LinkedHashMap<>();

    public InterruptRequest() {
    }

    public InterruptRequest(String message, Map<String, Object> payloadSchema, String autoConfirmKey) {
        this.message = message == null ? "" : message;
        this.payloadSchema = payloadSchema == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payloadSchema);
        this.autoConfirmKey = autoConfirmKey == null ? "" : autoConfirmKey;
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    @JsonAnySetter
    public void putExtraField(String key, Object value) {
        extraFields.put(key, value);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>(extraFields);
        result.put("message", message);
        result.put("payload_schema", payloadSchema);
        result.put("auto_confirm_key", autoConfirmKey);
        result.put("ui_options", uiOptions);
        return result;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }

    public Map<String, Object> getPayloadSchema() {
        return payloadSchema;
    }

    public void setPayloadSchema(Map<String, Object> payloadSchema) {
        this.payloadSchema = payloadSchema == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payloadSchema);
    }

    public String getAutoConfirmKey() {
        return autoConfirmKey;
    }

    public void setAutoConfirmKey(String autoConfirmKey) {
        this.autoConfirmKey = autoConfirmKey == null ? "" : autoConfirmKey;
    }

    public List<Map<String, Object>> getUiOptions() {
        return uiOptions;
    }

    public void setUiOptions(List<Map<String, Object>> uiOptions) {
        this.uiOptions = uiOptions;
    }
}
