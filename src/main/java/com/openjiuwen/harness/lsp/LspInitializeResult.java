/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp;

/**
 * Minimal initialization result for Java harness LSP.
 *
 * <p>Mirrors Python's {@code InitializeResult} in
 * {@code openjiuwen.harness.lsp.types}.
 */
public class LspInitializeResult {

    private final boolean success;
    private final int serversLoaded;
    private final double durationMs;

    public LspInitializeResult(boolean success, int serversLoaded, double durationMs) {
        this.success = success;
        this.serversLoaded = serversLoaded;
        this.durationMs = durationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getServersLoaded() {
        return serversLoaded;
    }

    public double getDurationMs() {
        return durationMs;
    }
}
