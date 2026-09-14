/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

/**
 * Abstract detection backend for guardrail analysis.
 * <p>
 * Mirrors Python's {@code GuardrailBackend} in
 * {@code openjiuwen/core/security/guardrail/backends.py}.
 */
public abstract class GuardrailBackend {

    public abstract RiskAssessment analyze(GuardrailContext ctx);

    protected static String contextText(GuardrailContext ctx) {
        if (ctx == null) {
            return "";
        }
        return ctx.getText().orElseGet(() -> {
            Object content = ctx.getContent();
            return content == null ? "" : String.valueOf(content);
        });
    }
}
