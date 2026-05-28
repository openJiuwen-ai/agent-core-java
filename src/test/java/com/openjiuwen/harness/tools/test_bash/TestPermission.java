/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.test_bash;

import com.openjiuwen.harness.tools.shell.bash.BashPermissionUtils;
import com.openjiuwen.harness.tools.shell.bash.PermissionConfig;
import com.openjiuwen.harness.tools.shell.bash.PermissionMode;
import com.openjiuwen.harness.tools.shell.bash.PermissionResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Permission.
 * <p>
 * Mirrors Python's {@code test_permission.py} from
 * {@code tests/unit_tests/harness/tools/test_bash/test_permission.py}.
 *
 * <p>Tests the permission check pipeline for bash commands.
 */
@DisplayName("Permission Tests")
class TestPermission {

    // Helper to create PermissionConfig
    private PermissionConfig makeConfig(String mode, List<Pattern> deny, List<Pattern> allow) {
        return new PermissionConfig(PermissionMode.valueOf(mode.toUpperCase()), deny, allow);
    }

    @Nested
    @DisplayName("Bypass Mode Tests")
    class BypassModeTests {

        @Test
        @DisplayName("test allows everything")
        void testAllowsEverything() {
            // Python: test_allows_everything
            PermissionConfig config = makeConfig("bypass", null, null);
            PermissionResult result = BashPermissionUtils.checkPermission("rm -rf /", config);
            
            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("test ignores deny patterns")
        void testIgnoresDenyPatterns() {
            // Python: test_ignores_deny_patterns
            PermissionConfig config = new PermissionConfig(
                    PermissionMode.BYPASS,
                    List.of(Pattern.compile("rm")),
                    null
            );
            PermissionResult result = BashPermissionUtils.checkPermission("rm foo", config);
            
            assertTrue(result.isAllowed());
        }
    }

    @Nested
    @DisplayName("Deny Patterns Tests")
    class DenyPatternsTests {

        @Test
        @DisplayName("test deny blocks")
        void testDenyBlocks() {
            // Python: test_deny_blocks
            PermissionConfig config = makeConfig("auto", List.of(Pattern.compile("\\bsudo\\b")), null);
            PermissionResult result = BashPermissionUtils.checkPermission("sudo apt install foo", config);
            
            assertFalse(result.isAllowed());
            assertTrue(result.getReason().toLowerCase().contains("denied"));
        }

        @Test
        @DisplayName("test deny checks each segment")
        void testDenyChecksEachSegment() {
            // Python: test_deny_checks_each_segment
            PermissionConfig config = makeConfig("auto", List.of(Pattern.compile("\\bsudo\\b")), null);
            PermissionResult result = BashPermissionUtils.checkPermission("echo hi | sudo tee file", config);
            
            assertFalse(result.isAllowed());
        }

        @Test
        @DisplayName("test no deny match passes")
        void testNoDenyMatchPasses() {
            // Python: test_no_deny_match_passes
            PermissionConfig config = makeConfig("auto", List.of(Pattern.compile("\\bsudo\\b")), null);
            PermissionResult result = BashPermissionUtils.checkPermission("echo hello", config);
            
            assertTrue(result.isAllowed());
        }
    }

    @Nested
    @DisplayName("Allow Patterns Tests")
    class AllowPatternsTests {

        @Test
        @DisplayName("test allow passes")
        void testAllowPasses() {
            // Python: test_allow_passes
            PermissionConfig config = makeConfig("auto", null, List.of(Pattern.compile("^git\\s")));
            PermissionResult result = BashPermissionUtils.checkPermission("git status", config);
            
            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("test deny takes precedence")
        void testDenyTakesPrecedence() {
            // Python: test_deny_takes_precedence
            PermissionConfig config = makeConfig("auto",
                    List.of(Pattern.compile("--force")),
                    List.of(Pattern.compile("^git\\s"))
            );
            PermissionResult result = BashPermissionUtils.checkPermission("git push --force", config);
            
            assertFalse(result.isAllowed());
        }
    }

    @Nested
    @DisplayName("Read Only Mode Tests")
    class ReadOnlyModeTests {

        @Test
        @DisplayName("test read command allowed")
        void testReadCommandAllowed() {
            // Python: test_read_command_allowed
            PermissionConfig config = makeConfig("read_only", null, null);
            PermissionResult result = BashPermissionUtils.checkPermission("cat foo.txt | grep bar", config);
            
            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("test write command denied")
        void testWriteCommandDenied() {
            // Python: test_write_command_denied
            PermissionConfig config = makeConfig("read_only", null, null);
            PermissionResult result = BashPermissionUtils.checkPermission("rm foo.txt", config);
            
            assertFalse(result.isAllowed());
        }
    }
}