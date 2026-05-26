/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.test_bash;

import com.openjiuwen.harness.tools.shell.bash.BashSecurityUtils;
import com.openjiuwen.harness.tools.shell.bash.SecurityCheck;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Security.
 * <p>
 * Mirrors Python's {@code test_security.py} from
 * {@code tests/unit_tests/harness/tools/test_bash/test_security.py}.
 *
 * <p>Tests injection detection and destructive command warnings.
 */
@DisplayName("Security Tests")
class TestSecurity {

    @Nested
    @DisplayName("Check Injection Tests")
    class CheckInjectionTests {

        @Test
        @DisplayName("test safe command")
        void testSafeCommand() {
            // Python: test_safe_command
            SecurityCheck result = BashSecurityUtils.checkInjection("echo hello");
            
            assertFalse(result.isBlocked());
        }

        @Test
        @DisplayName("test backtick blocked")
        void testBacktickBlocked() {
            // Python: test_backtick
            SecurityCheck result = BashSecurityUtils.checkInjection("echo `whoami`");
            
            assertTrue(result.isBlocked());
            assertTrue(result.getReason().toLowerCase().contains("backtick"));
        }

        @Test
        @DisplayName("test dollar paren blocked")
        void testDollarParenBlocked() {
            // Python: test_dollar_paren
            SecurityCheck result = BashSecurityUtils.checkInjection("echo $(id)");
            
            assertTrue(result.isBlocked());
            assertTrue(result.getReason().contains("$("));
        }

        @Test
        @DisplayName("test process substitution blocked")
        void testProcessSubstitutionBlocked() {
            // Python: test_process_substitution
            SecurityCheck result = BashSecurityUtils.checkInjection("diff <(ls a) <(ls b)");
            
            assertTrue(result.isBlocked());
            assertTrue(result.getReason().toLowerCase().contains("process substitution"));
        }

        @Test
        @DisplayName("test single quoted backtick blocked")
        void testSingleQuotedBacktickBlocked() {
            // Python: test_single_quoted_backtick_blocked
            // the heuristic is conservative — it blocks even if context is ambiguous
            SecurityCheck result = BashSecurityUtils.checkInjection("echo \"hello `world`\"");
            
            assertTrue(result.isBlocked());
        }

        @Test
        @DisplayName("test normal redirect allowed")
        void testNormalRedirectAllowed() {
            // Python: test_normal_redirect_allowed
            SecurityCheck result = BashSecurityUtils.checkInjection("echo hello > output.txt");
            
            assertFalse(result.isBlocked());
        }

        @Test
        @DisplayName("test pipe allowed")
        void testPipeAllowed() {
            // Python: test_pipe_allowed
            SecurityCheck result = BashSecurityUtils.checkInjection("cat file | grep foo");
            
            assertFalse(result.isBlocked());
        }
    }

    @Nested
    @DisplayName("Destructive Warning Tests")
    class DestructiveWarningTests {

        @Test
        @DisplayName("test git reset hard detected")
        void testGitResetHardDetected() {
            // Python: test_destructive_detected - git reset --hard
            String warning = BashSecurityUtils.getDestructiveWarning("git reset --hard HEAD~1");
            
            assertNotNull(warning);
            assertTrue(warning.toLowerCase().contains("uncommitted"));
        }

        @Test
        @DisplayName("test git push force detected")
        void testGitPushForceDetected() {
            // Python: test_destructive_detected - git push --force
            String warning = BashSecurityUtils.getDestructiveWarning("git push --force origin main");
            
            assertNotNull(warning);
            assertTrue(warning.toLowerCase().contains("remote history"));
        }

        @Test
        @DisplayName("test git clean detected")
        void testGitCleanDetected() {
            // Python: test_destructive_detected - git clean
            String warning = BashSecurityUtils.getDestructiveWarning("git clean -fd");
            
            assertNotNull(warning);
            assertTrue(warning.toLowerCase().contains("untracked"));
        }

        @Test
        @DisplayName("test git checkout detected")
        void testGitCheckoutDetected() {
            // Python: test_destructive_detected - git checkout
            String warning = BashSecurityUtils.getDestructiveWarning("git checkout -- .");
            
            assertNotNull(warning);
            assertTrue(warning.toLowerCase().contains("unstaged"));
        }

        @Test
        @DisplayName("test git stash drop detected")
        void testGitStashDropDetected() {
            // Python: test_destructive_detected - git stash drop
            String warning = BashSecurityUtils.getDestructiveWarning("git stash drop");
            
            assertNotNull(warning);
            assertTrue(warning.toLowerCase().contains("stashed"));
        }

        @Test
        @DisplayName("test git branch D detected")
        void testGitBranchDDetected() {
            // Python: test_destructive_detected - git branch -D
            String warning = BashSecurityUtils.getDestructiveWarning("git branch -D feature");
            
            assertNotNull(warning);
            assertTrue(warning.toLowerCase().contains("force-delete"));
        }

        @Test
        @DisplayName("test git commit amend detected")
        void testGitCommitAmendDetected() {
            // Python: test_destructive_detected - git commit --amend
            String warning = BashSecurityUtils.getDestructiveWarning("git commit --amend");
            
            assertNotNull(warning);
            assertTrue(warning.toLowerCase().contains("rewrite"));
        }

        @Test
        @DisplayName("test safe command no warning")
        void testSafeCommandNoWarning() {
            // Python: test_safe commands - git status
            String warning = BashSecurityUtils.getDestructiveWarning("git status");
            
            assertNull(warning);
        }

        @Test
        @DisplayName("test normal git push no warning")
        void testNormalGitPushNoWarning() {
            // Python: test_safe commands - git push origin main
            String warning = BashSecurityUtils.getDestructiveWarning("git push origin main");
            
            assertNull(warning);
        }

        @Test
        @DisplayName("test ls no warning")
        void testLsNoWarning() {
            // Python: test_safe commands - ls -la
            String warning = BashSecurityUtils.getDestructiveWarning("ls -la");
            
            assertNull(warning);
        }
    }
}