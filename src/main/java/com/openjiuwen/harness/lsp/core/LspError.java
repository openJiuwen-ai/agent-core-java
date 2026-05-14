/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

/**
 * Minimal LSP error type for Java harness runtime skeleton.
 *
 * <p>Mirrors Python's {@code LSPError} in {@code openjiuwen.harness.lsp.core.client}.
 */
public class LspError extends RuntimeException {

    private final int code;

    public LspError(int code, String message) {
        super("LSP Error " + code + ": " + message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
