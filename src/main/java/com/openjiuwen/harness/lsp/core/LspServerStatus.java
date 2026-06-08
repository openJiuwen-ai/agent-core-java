/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors Python's {@code LspServerStatus} in
 * {@code openjiuwen/harness/lsp/core/types.py}.
 */
public class LspServerStatus {

    @JsonProperty("server_id")
    private String serverId;

    private String name;
    private boolean running;
    private LspServerState state = LspServerState.STOPPED;
    private String root;

    @JsonProperty("crash_count")
    private int crashCount;

    @JsonProperty("last_error")
    private String lastError;

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public LspServerState getState() {
        return state;
    }

    public void setState(LspServerState state) {
        this.state = state == null ? LspServerState.STOPPED : state;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public int getCrashCount() {
        return crashCount;
    }

    public void setCrashCount(int crashCount) {
        this.crashCount = crashCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
