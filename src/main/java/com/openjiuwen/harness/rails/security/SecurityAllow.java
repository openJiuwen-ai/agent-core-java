/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

/**
 * Allow the guarded operation to continue.
 *
 * <p>Mirrors Python's {@code SecurityAllow} in
 * {@code openjiuwen/harness/rails/security/base_security_rail.py}.</p>
 */
public record SecurityAllow(String newArgs) implements SecurityDecision {
}
