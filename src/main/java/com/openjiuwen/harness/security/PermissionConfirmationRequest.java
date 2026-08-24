/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Single input passed to the hosted permission confirmation callback.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.host.PermissionConfirmationRequest}.
 * The Java variant carries the normalized tool name and arguments directly rather than
 * the raw {@code ctx/tool_call} handles, since the Java rail resolves those before
 * delegating to the host. {@code result} is the engine decision (typically ASK) and
 * {@code autoConfirmKey} is the session-scoped remember-key the rail would otherwise
 * use for the built-in interrupt/resume path.
 *
 * @since 0.1.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionConfirmationRequest {
    /** Normalized tool name (e.g. {@code bash}). */
    private String toolName;

    /** Tool arguments as a mutable map. */
    private Map<String, Object> toolArgs;

    /** Engine decision that prompted the confirmation request. */
    private PermissionResult result;

    /** Session auto-confirm key the rail would otherwise use. */
    private String autoConfirmKey;
}
