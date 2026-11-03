/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PowerShellSecurityTest {

    @Test
    void checkInjectionBlocksInvokeExpression() {
        PowerShellSecurity.SecurityCheck check = PowerShellSecurity.checkInjection("Invoke-Expression $payload");

        assertTrue(check.isBlocked());
        assertEquals("PowerShell injection detected: Invoke-Expression", check.getReason());
    }

    @Test
    void checkInjectionAllowsSimpleReadOnlyCommand() {
        PowerShellSecurity.SecurityCheck check = PowerShellSecurity.checkInjection("Get-Content README.md");

        assertFalse(check.isBlocked());
        assertNull(check.getReason());
    }

    @Test
    void getDestructiveWarningDetectsRecursiveDelete() {
        assertEquals(
                "May permanently remove files or directories",
                PowerShellSecurity.getDestructiveWarning("Remove-Item foo -Recurse -Force")
        );
    }

    @Test
    void getDestructiveWarningReturnsNullForSafeCommand() {
        assertNull(PowerShellSecurity.getDestructiveWarning("Get-ChildItem ."));
    }
}
