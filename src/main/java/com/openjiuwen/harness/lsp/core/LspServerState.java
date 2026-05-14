/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

/**
 * Minimal lifecycle states for a Java harness LSP server instance.
 *
 * <p>Mirrors Python's {@code LspServerState} in
 * {@code openjiuwen.harness.lsp.core.types}.
 */
public enum LspServerState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}
