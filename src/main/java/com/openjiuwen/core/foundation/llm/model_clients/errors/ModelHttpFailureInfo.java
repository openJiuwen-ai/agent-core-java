/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

import java.util.Map;

public record ModelHttpFailureInfo(
        ModelCallFailureStage stage,
        String modelProvider,
        String apiBase,
        boolean streaming,
        int statusCode,
        String safeResponseBody,
        boolean responseBodyTruncated) implements ModelCallFailureInfo {
    public ModelHttpFailureInfo {
        requireStage(stage, ModelCallFailureStage.HTTP_STATUS);
    }

    @Override
    public String errorMessage() {
        return "HTTP " + statusCode + ": " + safeResponseBody;
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> params = ModelCallFailureInfo.super.toParams();
        params.put("status_code", statusCode);
        params.put("response_body", safeResponseBody);
        params.put("response_body_truncated", responseBodyTruncated);
        return params;
    }

    public String responseBody() {
        return safeResponseBody;
    }

    static void requireStage(ModelCallFailureStage actual, ModelCallFailureStage expected) {
        if (actual != expected) {
            throw new IllegalArgumentException("failure stage must be " + expected);
        }
    }
}
