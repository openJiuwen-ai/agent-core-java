/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.List;
import java.util.Map;

/**
 * Guardrail that checks user input events.
 */
public class UserInputGuardrail extends BaseGuardrail {

    /**
     * Auto-generated for codecheck compliance.
     */
    public UserInputGuardrail() {
        this(null, null, true);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public UserInputGuardrail(GuardrailBackend backend, List<String> events, boolean enableLogging) {
        super(backend, events, enableLogging);
    }

    /**
     * Default events for user input guardrail.
     */
    public static final List<Object> DEFAULT_EVENTS = List.of("user_input");

    /**
     * Auto-generated for codecheck compliance.
     */
    protected List<String> defaultEvents() {
        return List.of("user_input");
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public GuardrailContext extractContext(Object event, Object[] args, Map<String, Object> kwargs) {
        Object text = kwargs != null ? kwargs.get("text") : null;
        if (!(text instanceof String stringText) || stringText.isEmpty()) {
            return GuardrailContext.builder()
                    .contentType(GuardrailContentType.TEXT)
                    .content("")
                    .event(String.valueOf(event))
                    .build();
        }
        return GuardrailContext.builder()
                .contentType(GuardrailContentType.TEXT)
                .content(stringText)
                .event(String.valueOf(event))
                .build();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public GuardrailResult detect(Object event, Object[] args, Map<String, Object> kwargs) throws Exception {
        Object text = kwargs != null ? kwargs.get("text") : null;
        if (!(text instanceof String stringText) || stringText.isEmpty()) {
            return GuardrailResult.pass_(Map.of("empty_input", true));
        }
        if (getBackend() == null) {
            return GuardrailResult.pass_();
        }
        return super.detect(event, args, kwargs);
    }
}
