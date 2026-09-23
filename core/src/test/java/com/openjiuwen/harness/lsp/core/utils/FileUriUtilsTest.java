/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's URI/path conversion behavior in
 * {@code openjiuwen/harness/lsp/core/utils/file_uri.py}.
 */
class FileUriUtilsTest {

    @Test
    void testPathToFileUri() {
        String uri = FileUriUtils.pathToFileUri(".\\src\\main");
        String normalized = uri.replace('\\', '/');
        assertTrue(normalized.startsWith("file:///"));
    }

    @Test
    void testFileUriToPathWindowsDriveNormalization() {
        assertEquals("D:\\work\\repo.py", FileUriUtils.fileUriToPath("file:///d%3A/work/repo.py"));
    }

    @Test
    void testFileUriToPathPassThrough() {
        assertEquals("plain-text", FileUriUtils.fileUriToPath("plain-text"));
    }
}
