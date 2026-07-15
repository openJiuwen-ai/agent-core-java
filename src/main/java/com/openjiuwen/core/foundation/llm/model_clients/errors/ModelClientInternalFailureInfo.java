/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

import java.util.Map;

public record ModelClientInternalFailureInfo(
        ModelCallFailureStage stage,
        String modelProvider,
        String apiBase,
        boolean streaming,
        String phase,
        String safeMessage,
        String exceptionClass,
        String safeExceptionMessage) implements ModelCallFailureInfo {
    public ModelClientInternalFailureInfo {
        ModelHttpFailureInfo.requireStage(stage, ModelCallFailureStage.CLIENT_INTERNAL);
    }

    @Override
    public String errorMessage() {
        return ModelTransportFailureInfo.safeJoin("model client internal failure", phase,
                safeMessage != null ? safeMessage : safeExceptionMessage);
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> params = ModelCallFailureInfo.super.toParams();
        params.put("phase", phase);
        params.put("message", safeMessage);
        params.put("exception_class", exceptionClass);
        params.put("exception_message", safeExceptionMessage);
        return params;
    }
}
