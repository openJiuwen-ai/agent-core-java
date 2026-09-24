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
        String actual = FileUriUtils.fileUriToPath("file:///d%3A/work/repo.py");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            assertEquals("D:\\work\\repo.py", actual);
        } else {
            // Non-Windows keeps the decoded URI path without drive-letter rewriting.
            assertEquals("/d:/work/repo.py", actual);
        }
    }

    @Test
    void testFileUriToPathPassThrough() {
        assertEquals("plain-text", FileUriUtils.fileUriToPath("plain-text"));
    }
}
