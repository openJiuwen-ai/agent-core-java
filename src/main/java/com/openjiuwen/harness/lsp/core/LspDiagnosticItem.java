/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code LspDiagnosticItem} in
 * {@code openjiuwen/harness/lsp/core/diagnostic_registry.py}.
 */
public class LspDiagnosticItem {

    private String message;
    private int severity;
    private Map<String, Object> range = new LinkedHashMap<>();
    private String source;
    private Object code;

    public LspDiagnosticItem() {
    }

    public LspDiagnosticItem(String message, int severity, Map<String, Object> range, String source, Object code) {
        this.message = message;
        this.severity = severity;
        setRange(range);
        this.source = source;
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    public Map<String, Object> getRange() {
        return range;
    }

    public void setRange(Map<String, Object> range) {
        this.range = range == null ? new LinkedHashMap<>() : new LinkedHashMap<>(range);
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @JsonProperty("code")
    public Object getCode() {
        return code;
    }

    public void setCode(Object code) {
        this.code = code;
    }
}
