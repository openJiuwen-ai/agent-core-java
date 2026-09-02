/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.List;
import java.util.Map;

/**
 * Guardrail that checks user input events.
 * 
 * @since 0.1.7
 */
public class UserInputGuardrail extends BaseGuardrail {
    /**
     * UserInputGuardrail.
     * 
     * @since 0.1.7
     */
    public UserInputGuardrail() {
        this(null, null, true);
    }

    /**
     * UserInputGuardrail.
     * 
     * @param backend backend
     * @param events events
     * @param enableLogging enableLogging
     * @since 0.1.7
     */
    public UserInputGuardrail(GuardrailBackend backend, List<String> events, boolean enableLogging) {
        super(backend, events, enableLogging);
    }

    /**
     * defaultEvents.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected List<String> defaultEvents() {
        return List.of("user_input");
    }

    /**
     * detect.
     * 
     * @param eventName eventName
     * @param args args
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
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
