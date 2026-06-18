/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

/**
 * Marker for security rail decisions.
 *
 * <p>Mirrors Python's {@code SecurityDecision} in
 * {@code openjiuwen/harness/rails/security/base_security_rail.py}.</p>
 */
public sealed interface SecurityDecision
        permits SecurityAllow, SecurityReject, SecurityInterrupt, SecurityAlert {
}
