/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp;

import com.openjiuwen.harness.lsp.core.LspServerStatus;

import java.util.List;

/**
 * Minimal aggregate status for the Java harness LSP subsystem.
 *
 * <p>Mirrors Python's {@code LspStatus} in
 * {@code openjiuwen.harness.lsp.types}.
 */
public class LspStatus {

    private final boolean initialized;
    private final List<LspServerStatus> servers;

    public LspStatus(boolean initialized, List<LspServerStatus> servers) {
        this.initialized = initialized;
        this.servers = servers != null ? List.copyOf(servers) : List.of();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public List<LspServerStatus> getServers() {
        return servers;
    }
}
