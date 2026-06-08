/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core.utils;

/**
 * Mirrors Python's constants in
 * {@code openjiuwen/harness/lsp/core/utils/constants.py}.
 */
public final class LspConstants {

    public static final int LSP_ERROR_CONTENT_MODIFIED = -32801;
    public static final int MAX_RETRIES_FOR_CONTENT_MODIFIED = 3;
    public static final int RETRY_BASE_DELAY_MS = 500;
    public static final int DEFAULT_STARTUP_TIMEOUT_MS = 45_000;
    public static final int MAX_LSP_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    public static final int DEFAULT_GOPLS_TIMEOUT_MS = 60_000;
    public static final int MAX_CRASH_RECOVERY_ATTEMPTS = 3;
    public static final int DEFAULT_REQUEST_TIMEOUT_MS = 15_000;

    private LspConstants() {
    }
}
