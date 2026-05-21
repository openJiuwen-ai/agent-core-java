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
 * Single input parameter for PermissionSceneHook.
 *
 * <p>Avoids a long list of positional arguments.
 *
 * <p>Mirrors Python's {@code PermissionSceneHookInput} in
 * {@code openjiuwen.harness.security.host}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionSceneHookInput {

    /** Agent callback context. */
    private Object ctx;

    /** Tool call being checked. */
    private Object toolCall;

    /** User input if this is a resume. */
    private Object userInput;

    /** Normalized tool name (after alias resolution). */
    private String normalizedToolName;

    /** Tool arguments parsed from tool call. */
    private Map<String, Object> toolArgs;

    /** Permission engine instance. */
    private Object engine;
}