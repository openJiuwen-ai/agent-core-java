/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BashSecurityTest {

    @Test
    void checkInjectionBlocksDollarParenSubstitution() {
        BashSecurity.SecurityCheck check = BashSecurity.checkInjection("echo $(whoami)");

        assertTrue(check.isBlocked());
        assertEquals("Shell injection detected: $() command substitution", check.getReason());
    }

    @Test
    void checkInjectionAllowsSimpleReadOnlyCommand() {
        BashSecurity.SecurityCheck check = BashSecurity.checkInjection("cat README.md");

        assertFalse(check.isBlocked());
        assertNull(check.getReason());
    }

    @Test
    void getDestructiveWarningDetectsSudo() {
        assertEquals(
                "sudo may require a password in non-interactive mode; configure NOPASSWD or run as root",
                BashSecurity.getDestructiveWarning("sudo rm -rf /tmp/work")
        );
    }

    @Test
    void getDestructiveWarningReturnsNullForSafeCommand() {
        assertNull(BashSecurity.getDestructiveWarning("rg needle README.md"));
    }
}
