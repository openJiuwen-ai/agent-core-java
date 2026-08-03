/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/tools/test_bash/test_security.py}.
 */
class BashSecurityPythonParityTest {

    @Test
    void safeCommandIsNotBlocked() {
        BashSecurity.SecurityCheck result = BashSecurity.checkInjection("echo hello");

        assertThat(result.isBlocked()).isFalse();
    }

    @Test
    void backtickCommandSubstitutionIsBlocked() {
        BashSecurity.SecurityCheck result = BashSecurity.checkInjection("echo `whoami`");

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.getReason()).contains("backtick");
    }

    @Test
    void dollarParenCommandSubstitutionIsBlocked() {
        BashSecurity.SecurityCheck result = BashSecurity.checkInjection("echo $(id)");

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.getReason()).contains("$(");
    }

    @Test
    void processSubstitutionIsBlocked() {
        BashSecurity.SecurityCheck result = BashSecurity.checkInjection("diff <(ls a) <(ls b)");

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.getReason()).contains("process substitution");
    }

    @Test
    void doubleQuotedBacktickIsConservativelyBlocked() {
        BashSecurity.SecurityCheck result = BashSecurity.checkInjection("echo \"hello `world`\"");

        assertThat(result.isBlocked()).isTrue();
    }

    @Test
    void normalRedirectIsAllowed() {
        BashSecurity.SecurityCheck result = BashSecurity.checkInjection("echo hello > output.txt");

        assertThat(result.isBlocked()).isFalse();
    }

    @Test
    void pipeIsAllowed() {
        BashSecurity.SecurityCheck result = BashSecurity.checkInjection("cat file | grep foo");

        assertThat(result.isBlocked()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "'git reset --hard HEAD~1', uncommitted",
            "'git push --force origin main', 'remote history'",
            "'git push -f origin main', 'remote history'",
            "'git clean -fd', untracked",
            "'git checkout -- .', unstaged",
            "'git stash drop', stashed",
            "'git stash clear', stashed",
            "'git branch -D feature', force-delete",
            "'git commit --amend', rewrite",
            "'git push --no-verify', hooks",
            "'DROP TABLE users;', database",
            "'TRUNCATE TABLE logs;', truncate",
            "'kubectl delete pod foo', Kubernetes",
            "'terraform destroy', Terraform"
    })
    void destructiveCommandsReturnWarnings(String command, String keyword) {
        String warning = BashSecurity.getDestructiveWarning(command);

        assertThat(warning).isNotNull();
        assertThat(warning.toLowerCase()).contains(keyword.toLowerCase());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "git status",
            "git log --oneline",
            "git push origin main",
            "ls -la",
            "echo hello",
            "python test.py"
    })
    void safeCommandsReturnNoDestructiveWarning(String command) {
        assertThat(BashSecurity.getDestructiveWarning(command)).isNull();
    }
}
