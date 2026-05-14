/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

/**
 * Cache key for one LSP server instance under a specific workspace root.
 *
 * <p>Mirrors Python's {@code ServerInstanceKey} in {@code openjiuwen.harness.lsp.core.manager}.
 */
public record LspServerInstanceKey(String serverId, String root) {
}
