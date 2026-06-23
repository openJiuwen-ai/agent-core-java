/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code patterns} contract in
 * {@code openjiuwen/harness/security/patterns.py}.
 *
 * <p>Mirrors Python's {@code test_patterns} in
 * {@code tests/unit_tests/harness/security/test_patterns.py}.</p>
 */
class PermissionPatternsTest {

    @Test
    void matchWildcardSupportsShellSafeSuffixRule() {
        assertTrue(PermissionPatterns.matchWildcard("ls", "ls *"));
        assertTrue(PermissionPatterns.matchWildcard("ls -la", "ls *"));
        assertFalse(PermissionPatterns.matchWildcard("ls; rm -rf /", "ls *"));
    }

    @Test
    void matchWildcardRejectsTrailingNewline() {
        assertTrue(PermissionPatterns.matchWildcard("git status", "git status"));
        assertFalse(PermissionPatterns.matchWildcard("git status\n", "git status"));

        assertTrue(PermissionPatterns.matchWildcard("git status", "git status *"));
        assertTrue(PermissionPatterns.matchWildcard("git status -sb", "git status *"));
        assertFalse(PermissionPatterns.matchWildcard("git status\n", "git status *"));
        assertFalse(PermissionPatterns.matchWildcard("git status -sb\n", "git status *"));
    }

    @Test
    void matchWildcardStillRejectsCommandInjection() {
        assertFalse(PermissionPatterns.matchWildcard("git status; rm -rf /", "git status *"));
        assertFalse(PermissionPatterns.matchWildcard("git status\nrm -rf /", "git status *"));
    }

    @Test
    void pathMatcherChecksParents() {
        PermissionPatterns.PathMatcher matcher = new PermissionPatterns.PathMatcher();

        assertTrue(matcher.matchPath("D:/tmp/*", Path.of("D:/tmp/demo/file.txt")));
        assertFalse(matcher.matchPath("D:/tmp/*", Path.of("D:/other/file.txt")));
    }

    @Test
    void mergeExternalDirectoryAllowIntoPermissionsTracksChanges() {
        Map<String, Object> permissions = new LinkedHashMap<>();
        PermissionPatterns.PermissionsMergeResult result =
                PermissionPatterns.mergeExternalDirectoryAllowIntoPermissions(
                        permissions,
                        List.of("D:/python_to_java_v2/outside/demo.txt")
                );

        assertTrue(result.changed());
        Map<?, ?> externalDirectory = (Map<?, ?>) result.permissions().get("external_directory");
        assertEquals("allow", externalDirectory.get("D:/python_to_java_v2/outside"));
    }

    @Test
    void writePermissionsSectionToAgentConfigYamlPersistsPermissionsNode() throws Exception {
        Path tempFile = Files.createTempFile("permission-patterns", ".yaml");
        try {
            Map<String, Object> permissions = Map.of("enabled", true);

            assertTrue(PermissionPatterns.writePermissionsSectionToAgentConfigYaml(tempFile, permissions));
            String yaml = Files.readString(tempFile);
            assertTrue(yaml.contains("permissions:"));
            assertTrue(yaml.contains("enabled: true"));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
