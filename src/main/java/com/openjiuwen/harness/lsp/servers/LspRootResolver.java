/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers;

/**
 * Root resolution contract for Java harness LSP server definitions.
 *
 * <p>Mirrors Python's {@code find_root} callable shape from
 * {@code openjiuwen.harness.lsp.servers.registry}.
 */
@FunctionalInterface
public interface LspRootResolver {
    String resolve(String filePath);
}
