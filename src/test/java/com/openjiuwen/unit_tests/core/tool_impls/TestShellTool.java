/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.tool_impls;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import com.openjiuwen.harness.tools.BashTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

/**
 * Tests for shell/bash tool implementation.
 * <p>
 * Mirrors Python's tests.unit_tests.harness.tools.test_shell_tools.
 * Tests shell command execution with security controls.
 */
class TestShellTool {

    private SysOperation sysOp;

    @BeforeEach
    void setupSysOp() {
        SysOperationCard card = SysOperationCard.builder()
                .id("test_shell_tool_op")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().build())
                .build();
        sysOp = new SysOperation(card);
    }

    @Test
    @Tag("level0")
    void testShellToolExists() {
        assertNotNull(BashTool.class);
    }

    @Test
    @Tag("level1")
    void testBashToolConstruction() {
        BashTool tool = new BashTool(sysOp);
        assertNotNull(tool);
    }

    @Test
    @Tag("level1")
    void testBashToolSimpleCommand() {
        BashTool tool = new BashTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("command", "echo hello"),
            Map.of()
        );

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    @Test
    @Tag("level1")
    void testBashToolChineseOutput() {
        BashTool tool = new BashTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("command", "echo 你好世界"),
            Map.of()
        );

        assertTrue(result.isSuccess());
    }

    @Test
    @Tag("level1")
    void testBashToolFailCommand() {
        BashTool tool = new BashTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("command", "echo fail && exit 1"),
            Map.of()
        );

        assertFalse(result.isSuccess());
    }

    @Test
    @Tag("level1")
    void testBashToolAllowlistRestriction() {
        // Create sys operation with restricted allowlist
        SysOperationCard card = SysOperationCard.builder()
                .id("test_shell_allowlist_op")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder()
                        .shellAllowlist(java.util.List.of("echo"))
                        .build())
                .build();
        SysOperation restrictedOp = new SysOperation(card);

        BashTool tool = new BashTool(restrictedOp);
        
        // Allowed command should succeed
        ToolOutput okResult = (ToolOutput) tool.invoke(
            Map.of("command", "echo ok"),
            Map.of()
        );
        assertTrue(okResult.isSuccess());

        // Blocked command should fail
        ToolOutput blockedResult = (ToolOutput) tool.invoke(
            Map.of("command", "whoami"),
            Map.of()
        );
        assertFalse(blockedResult.isSuccess());
        assertNotNull(blockedResult.getError());
    }

    @ParameterizedTest
    @CsvSource({
        "'rm -rf /tmp/foo', 'rm -rf'",
        "'shutdown -h now', 'shutdown'",
        "'reboot', 'reboot'"
    })
    @Tag("level1")
    void testDangerousCommandBlocked(String command, String label) {
        BashTool tool = new BashTool(sysOp);
        ToolOutput result = (ToolOutput) tool.invoke(
            Map.of("command", command),
            Map.of()
        );

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    @Tag("level0")
    void testShellToolMethods() {
        assertTrue(BashTool.class.getDeclaredMethods().length > 0);
    }
}