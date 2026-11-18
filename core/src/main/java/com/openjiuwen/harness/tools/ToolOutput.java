/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.security.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code ToolOutput} in
 * {@code openjiuwen/harness/tools/base_tool.py}.
 */
public class ToolOutput {

    private final boolean success;
    private final Object data;
    private final String error;

    @JsonCreator
    public ToolOutput(
            @JsonProperty("success") boolean success,
            @JsonProperty("data") Object data,
            @JsonProperty("error") String error
    ) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getData() {
        return data;
    }

    public String getError() {
        return error;
    }

    public static ToolOutput of(boolean success, Object data, String error) {
        return new ToolOutput(success, data, error);
    }

    public static ToolOutput success(Object data) {
        return new ToolOutput(true, data, null);
    }

    public static ToolOutput failure(String error) {
        return new ToolOutput(false, null, error);
    }

    /**
     * JSON-facing view. The key stays {@code success}, matching the constructor
     * property, so nested {@code ToolOutput} values serialize the same way.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> rendered = new LinkedHashMap<>();
        rendered.put("success", success);
        rendered.put("data", data);
        rendered.put("error", error);
        return rendered;
    }

    /**
     * Create a new builder for ToolOutput.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ToolOutput.
     */
    public static class Builder {
        private boolean success;
        private Object data;
        private String error;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public ToolOutput build() {
            return new ToolOutput(success, data, error);
        }
    }

    @Override
    public String toString() {
        try {
            return JsonUtils.safeJsonDumps(toMap());
        } catch (RuntimeException exception) {
            return "ToolOutput(success=" + success + ", error=" + error + ")";
        }
    }
}
