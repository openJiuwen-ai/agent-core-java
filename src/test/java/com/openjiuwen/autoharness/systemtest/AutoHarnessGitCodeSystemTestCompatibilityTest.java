
package com.openjiuwen.autoharness.systemtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
