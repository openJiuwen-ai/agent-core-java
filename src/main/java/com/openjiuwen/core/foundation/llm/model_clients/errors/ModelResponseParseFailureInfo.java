/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

import java.util.Map;

public record ModelResponseParseFailureInfo(
        ModelCallFailureStage stage,
        String modelProvider,
        String apiBase,
        boolean streaming,
        String phase,
        String safeResponseBody,
        boolean responseBodyTruncated,
        String exceptionClass,
        String safeExceptionMessage) implements ModelCallFailureInfo {
    public ModelResponseParseFailureInfo {
        ModelHttpFailureInfo.requireStage(stage, ModelCallFailureStage.RESPONSE_PARSE);
    }

    @Override
    public String errorMessage() {
        return ModelTransportFailureInfo.safeJoin("response parse failure", phase, safeExceptionMessage);
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> params = ModelCallFailureInfo.super.toParams();
        params.put("phase", phase);
        params.put("response_body", safeResponseBody);
        params.put("response_body_truncated", responseBodyTruncated);
        params.put("exception_class", exceptionClass);
        params.put("exception_message", safeExceptionMessage);
        return params;
    }

    public String responseBody() {
        return safeResponseBody;
    }
}
