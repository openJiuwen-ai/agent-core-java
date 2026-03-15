/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.security.guardrail;

import java.util.List;
import java.util.Map;

/**
 * Guardrail that checks user input events.
 */
public class UserInputGuardrail extends BaseGuardrail {

    public UserInputGuardrail() {
        this(null, null, true);
    }

    public UserInputGuardrail(GuardrailBackend backend, List<String> events, boolean enableLogging) {
        super(backend, events, enableLogging);
    }

    @Override
    protected List<String> defaultEvents() {
        return List.of("user_input");
    }

    @Override
    public GuardrailResult detect(String eventName, Object[] args, Map<String, Object> kwargs) throws Exception {
        Object text = kwargs != null ? kwargs.get("text") : null;
        if (!(text instanceof String stringText) || stringText.isEmpty()) {
            return GuardrailResult.pass(Map.of("empty_input", true));
        }
        if (backend == null) {
            return GuardrailResult.pass();
        }
        return super.detect(eventName, args, kwargs);
    }
}
