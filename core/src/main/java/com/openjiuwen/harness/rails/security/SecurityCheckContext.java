/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.openjiuwen.harness.rails.CallbackContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Context passed to concrete security rail checks.
 *
 * <p>Mirrors Python's {@code SecurityCheckContext} in
 * {@code openjiuwen/harness/rails/security/base_security_rail.py}.</p>
 */
public record SecurityCheckContext(
        CallbackContext callbackContext,
        String event,
        Object userInput,
        Map<String, Object> autoConfirmConfig,
        String subjectId
) {
    public SecurityCheckContext {
        autoConfirmConfig = autoConfirmConfig == null ? new LinkedHashMap<>() : new LinkedHashMap<>(autoConfirmConfig);
        subjectId = subjectId == null ? "" : subjectId;
    }
}
