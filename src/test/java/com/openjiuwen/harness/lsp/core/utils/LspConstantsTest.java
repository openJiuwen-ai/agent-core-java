/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's LSP constant values in
 * {@code openjiuwen/harness/lsp/core/utils/constants.py}.
 */
class LspConstantsTest {

    @Test
    void testExpectedValues() {
        assertEquals(-32801, LspConstants.LSP_ERROR_CONTENT_MODIFIED);
        assertEquals(3, LspConstants.MAX_RETRIES_FOR_CONTENT_MODIFIED);
        assertEquals(500, LspConstants.RETRY_BASE_DELAY_MS);
        assertEquals(45_000, LspConstants.DEFAULT_STARTUP_TIMEOUT_MS);
        assertEquals(10 * 1024 * 1024, LspConstants.MAX_LSP_FILE_SIZE_BYTES);
        assertEquals(60_000, LspConstants.DEFAULT_GOPLS_TIMEOUT_MS);
        assertEquals(3, LspConstants.MAX_CRASH_RECOVERY_ATTEMPTS);
        assertEquals(15_000, LspConstants.DEFAULT_REQUEST_TIMEOUT_MS);
    }
}
