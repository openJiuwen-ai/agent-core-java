/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

/**
 * Minimal snapshot for one Java harness LSP server instance.
 *
 * <p>Mirrors Python's {@code LspServerStatus} in
 * {@code openjiuwen.harness.lsp.core.types}.
 */
public class LspServerStatus {

    private final String serverId;
    private final String name;
    private final boolean running;
    private final String workspaceFolder;
    private final LspServerState state;

    public LspServerStatus(String serverId, String name, boolean running, String workspaceFolder, LspServerState state) {
        this.serverId = serverId;
        this.name = name;
        this.running = running;
        this.workspaceFolder = workspaceFolder;
        this.state = state;
    }

    public String getServerId() {
        return serverId;
    }

    public String getName() {
        return name;
    }

    public boolean isRunning() {
        return running;
    }

    public String getWorkspaceFolder() {
        return workspaceFolder;
    }

    public LspServerState getState() {
        return state;
    }
}
