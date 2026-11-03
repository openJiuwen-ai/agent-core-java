/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

/**
 * Mirrors Python's {@code LSPError} in
 * {@code openjiuwen/harness/lsp/core/client.py}.
 */
public class LspError extends RuntimeException {

    private final int code;
    private final String errorMessage;

    public LspError(int code, String errorMessage) {
        super("LSP Error " + code + ": " + errorMessage);
        this.code = code;
        this.errorMessage = errorMessage;
    }

    public int getCode() {
        return code;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
