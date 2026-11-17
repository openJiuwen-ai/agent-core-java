/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.security.JsonUtils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class ToolOutput used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolOutput {
    @JsonProperty("isSuccess")
    @JsonAlias("success")
    private boolean isSuccess;
    private Object data;
    private String error;

    /**
     * ToolOutputBuilder.
     * 
     * @since 0.1.7
     */
    public static class ToolOutputBuilder {
        /**
         * success.
         * 
         * @param value value
         * @return the result
         * @since 0.1.7
         */
        public ToolOutputBuilder success(boolean value) {
            this.isSuccess = value;
            return this;
        }
    }

    /**
     * JSON-facing view of this result. The key stays {@code isSuccess} so it matches the
     * field consumers already see, instead of Jackson's JavaBean name {@code success}.
     *
     * @return serializable result map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> rendered = new LinkedHashMap<>();
        rendered.put("isSuccess", isSuccess);
        rendered.put("data", data);
        rendered.put("error", error);
        return rendered;
    }

    /**
     * Same JSON text written into tool messages, so {@code String.valueOf(toolOutput)} is parseable.
     *
     * @return JSON object text, or a non-recursive fallback when serialization fails
     */
    @Override
    public String toString() {
        try {
            return JsonUtils.safeJsonDumps(toMap());
        } catch (RuntimeException exception) {
            return "ToolOutput(isSuccess=" + isSuccess + ", error=" + error + ")";
        }
    }
}
