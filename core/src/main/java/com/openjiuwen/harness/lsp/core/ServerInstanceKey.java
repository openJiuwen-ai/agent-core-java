/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

/**
 * Unique cache key for an LSP server instance.
 * <p>
 * Mirrors Python's {@code ServerInstanceKey} in
 * {@code openjiuwen/harness/lsp/core/manager.py}.
 * </p>
 *
 * @param serverId server identifier
 * @param root project root for this server instance
 */
public record ServerInstanceKey(String serverId, String root) {
}
