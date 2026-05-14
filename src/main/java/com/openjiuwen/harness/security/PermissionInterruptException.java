/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

/**
 * Internal interrupt exception used to suspend tool execution for approval.
 *
 * <p>Java-side supporting type for Python's approval/interrupt flow in
 * {@code openjiuwen.harness.rails.interrupt.confirm_rail} and related rails.
 */
public class PermissionInterruptException extends RuntimeException {

    public PermissionInterruptException(String message) {
        super(message);
    }
}
