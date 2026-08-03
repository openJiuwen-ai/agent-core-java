/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp;

import com.openjiuwen.harness.lsp.core.LspServerStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code LspStatus} in
 * {@code openjiuwen/harness/lsp/types.py}.
 */
public class LspStatus {

    private boolean initialized;
    private List<LspServerStatus> servers = new ArrayList<>();

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public List<LspServerStatus> getServers() {
        return servers;
    }

    public void setServers(List<LspServerStatus> servers) {
        this.servers = servers == null ? new ArrayList<>() : new ArrayList<>(servers);
    }
}
