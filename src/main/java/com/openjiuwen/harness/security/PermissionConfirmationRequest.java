/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single input parameter for RequestPermissionConfirmationHook.
 *
 * <p>Mirrors Python's {@code PermissionConfirmationRequest} in
 * {@code openjiuwen.harness.security.host}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionConfirmationRequest {

    /** Agent callback context. */
    private Object ctx;

    /** Tool call being checked. */
    private Object toolCall;

    /** Permission evaluation result (ASK level). */
    private PermissionResult result;

    /** Auto-confirm key derived from tool call. */
    private String autoConfirmKey;
}