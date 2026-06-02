/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.powershell;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.shell.powershell.PowerShellTool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PowerShellTool.
 *
 * <p>Mirrors Python's {@code test_powershell_tool.py} in
 * {@code tests.unit_tests.harness.tools.test_powershell}.
 */
@DisabledOnOs(OS.MAC)
class TestPowerShellTool {

    private SysOperation sysOperation;
    private BaseShellOperation shell;

    @BeforeEach
    void setUp() {
        sysOperation = mock(SysOperation.class);
        shell = mock(BaseShellOperation.class);
        when(sysOperation.shell()).thenReturn(shell);
    }

    @Test
    void invokeForcesPowershellShellType() {
        when(shell.executeCmd(anyString(), anyString(), anyInt(), isNull(), anyMap()))
                .thenReturn(result("ok\n", "", 0));

        ToolOutput output = (ToolOutput) new PowerShellTool(sysOperation, "auto", null, null, null)
                .invoke(Map.of("command", "Write-Output ok"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(shell).executeCmd(
                org.mockito.ArgumentMatchers.eq("Write-Output ok"),
                anyString(),
                anyInt(),
                isNull(),
                optionsCaptor.capture());
        assertEquals("powershell", optionsCaptor.getValue().get("shell_type"));
    }

    @Test
    void readOnlyBlocksWriteCommands() {
        ToolOutput output = (ToolOutput) new PowerShellTool(sysOperation, "read_only", null, null, null)
                .invoke(Map.of("command", "Set-Content test.txt hi"), Map.of());

        assertFalse(output.isSuccess());
        assertTrue(output.getError().contains("Read-only"));
        verify(sysOperation, never()).shell();
    }

    @Test
    void injectionPatternBlocked() {
        ToolOutput output = (ToolOutput) new PowerShellTool(sysOperation)
                .invoke(Map.of("command", "Invoke-Expression \"Get-ChildItem\""), Map.of());

        assertFalse(output.isSuccess());
        assertTrue(output.getError().toLowerCase().contains("injection"));
        verify(sysOperation, never()).shell();
    }

    private ExecuteCmdResult result(String stdout, String stderr, int exitCode) {
        return new ExecuteCmdResult(StatusCode.SUCCESS.getCode(), "ok", ExecuteCmdData.builder()
                .command("pwsh")
                .cwd(".")
                .stdout(stdout)
                .stderr(stderr)
                .exitCode(exitCode)
                .build());
    }
}
