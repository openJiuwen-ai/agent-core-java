/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

/**
 * Minimal permission levels for Java harness tool checks.
 *
 * <p>Mirrors Python's {@code PermissionLevel} in
 * {@code openjiuwen.harness.security.models}.
 */
public enum PermissionLevel {
    ALLOW,
    ASK,
    DENY
}
