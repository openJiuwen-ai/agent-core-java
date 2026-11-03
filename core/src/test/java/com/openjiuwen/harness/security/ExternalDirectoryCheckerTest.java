/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code checker} contract in
 * {@code openjiuwen/harness/security/checker.py}.
 */
class ExternalDirectoryCheckerTest {

    @Test
    void extractPathsFromCommandResolvesRelativeShellPaths() {
        List<Path> paths = ExternalDirectoryChecker.extractPathsFromCommand(
                "cat ../outside.txt",
                Path.of("D:/python_to_java_v2/workspace/current")
        );

        assertEquals(1, paths.size());
        assertTrue(paths.get(0).toString().replace("\\", "/").endsWith("/workspace/outside.txt"));
    }

    @Test
    void checkExternalPathsReturnsAskForOutsideShellPath() {
        ExternalDirectoryChecker checker = new ExternalDirectoryChecker(
                Map.of("external_directory", Map.of("*", "ask")),
                Path.of("D:/python_to_java_v2/workspace")
        );

        PermissionResult result = checker.checkExternalPaths(
                "bash",
                Map.of("command", "cat ../../outside.txt", "workdir", "current")
        );

        assertNotNull(result);
        assertEquals(PermissionLevel.ASK, result.getPermission());
        assertEquals("external_directory.*", result.getMatchedRule());
    }

    @Test
    void checkExternalPathsSkipsInternalReadFile() {
        ExternalDirectoryChecker checker = new ExternalDirectoryChecker(
                Map.of("external_directory", Map.of("*", "ask")),
                Path.of("D:/python_to_java_v2/workspace")
        );

        PermissionResult result = checker.checkExternalPaths(
                "read_file",
                Map.of("path", "inside/file.txt")
        );

        assertNull(result);
    }
}
