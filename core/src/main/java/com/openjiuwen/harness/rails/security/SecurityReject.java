/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

/**
 * Reject the guarded operation.
 *
 * <p>Mirrors Python's {@code SecurityReject} in
 * {@code openjiuwen/harness/rails/security/base_security_rail.py}.</p>
 */
public record SecurityReject(String message, Object result, Object toolMessage) implements SecurityDecision {
    public SecurityReject(String message) {
        this(message, null, null);
    }
}
