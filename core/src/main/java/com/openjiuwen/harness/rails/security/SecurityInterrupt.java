/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interrupt the guarded operation and wait for user input.
 *
 * <p>Mirrors Python's {@code SecurityInterrupt} in
 * {@code openjiuwen/harness/rails/security/base_security_rail.py}.</p>
 */
public record SecurityInterrupt(Map<String, Object> request, String subjectId) implements SecurityDecision {
    public SecurityInterrupt {
        request = request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
        subjectId = subjectId == null ? "" : subjectId;
    }
}
