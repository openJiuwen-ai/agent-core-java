/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

import java.util.Map;

public record ModelTransportFailureInfo(
        ModelCallFailureStage stage,
        String modelProvider,
        String apiBase,
        boolean streaming,
        String phase,
        String exceptionClass,
        String safeExceptionMessage) implements ModelCallFailureInfo {
    public ModelTransportFailureInfo {
        ModelHttpFailureInfo.requireStage(stage, ModelCallFailureStage.TRANSPORT);
    }

    @Override
    public String errorMessage() {
        return safeJoin("transport failure", phase, safeExceptionMessage);
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> params = ModelCallFailureInfo.super.toParams();
        params.put("phase", phase);
        params.put("exception_class", exceptionClass);
        params.put("exception_message", safeExceptionMessage);
        return params;
    }

    static String safeJoin(String prefix, String phase, String message) {
        StringBuilder builder = new StringBuilder(prefix);
        if (phase != null && !phase.isBlank()) {
            builder.append(" at ").append(phase);
        }
        if (message != null && !message.isBlank()) {
            builder.append(": ").append(message);
        }
        return builder.toString();
    }
}
