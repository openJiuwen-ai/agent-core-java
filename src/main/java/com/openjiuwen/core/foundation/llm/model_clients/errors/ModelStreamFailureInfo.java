/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

import java.util.Map;

public record ModelStreamFailureInfo(
        ModelCallFailureStage stage,
        String modelProvider,
        String apiBase,
        boolean streaming,
        String phase,
        String event,
        String exceptionClass,
        String safeExceptionMessage) implements ModelCallFailureInfo {
    public ModelStreamFailureInfo {
        ModelHttpFailureInfo.requireStage(stage, ModelCallFailureStage.STREAM);
    }

    @Override
    public String errorMessage() {
        return ModelTransportFailureInfo.safeJoin("stream failure", phase, safeExceptionMessage);
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> params = ModelCallFailureInfo.super.toParams();
        params.put("phase", phase);
        params.put("event", event);
        params.put("exception_class", exceptionClass);
        params.put("exception_message", safeExceptionMessage);
        return params;
    }
}
