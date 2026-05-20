package com.openjiuwen.autoharness.systemtest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutoHarnessGitCodeSystemTestCompatibilityTest {

    @Test
    void systemTestUsernameShouldFallBackToForkOwnerLikePythonSchema() {
        assertThat(AutoHarnessGitCodeSystemTest.resolveGitCodeUsernameForSystemTest("", "fork-owner"))
                .isEqualTo("fork-owner");
    }

    @Test
    void systemTestUsernameShouldPreferExplicitUsernameLikePythonSchema() {
        assertThat(AutoHarnessGitCodeSystemTest.resolveGitCodeUsernameForSystemTest("bot-user", "fork-owner"))
                .isEqualTo("bot-user");
    }
}
