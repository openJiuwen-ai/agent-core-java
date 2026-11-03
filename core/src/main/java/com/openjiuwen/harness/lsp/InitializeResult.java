/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors Python's {@code InitializeResult} in
 * {@code openjiuwen/harness/lsp/types.py}.
 */
public class InitializeResult {

    private boolean success;

    @JsonProperty("servers_loaded")
    private int serversLoaded;

    @JsonProperty("duration_ms")
    private double durationMs;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getServersLoaded() {
        return serversLoaded;
    }

    public void setServersLoaded(int serversLoaded) {
        this.serversLoaded = serversLoaded;
    }

    public double getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(double durationMs) {
        this.durationMs = durationMs;
    }
}
