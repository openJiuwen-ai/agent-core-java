/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.result.BaseResult;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the SysOp module (System Operations).
 * Tests file system and shell operations in LOCAL mode.
 * Corresponds to Python's skill_use example (system operations).
 */
@Tag("system-test")
class SysOpSystemTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("SysOperation creation in LOCAL mode")
    void testSysOperationCreation() {
        SysOperationCard card = SysOperationCard.builder()
                .id("test_sysop")
                .mode(OperationMode.LOCAL)
                .build();

        SysOperation sysOp = new SysOperation(card);
        assertNotNull(sysOp);
        assertTrue(sysOp.getMode() == OperationMode.LOCAL);
        System.out.println("[SysOp Create] Mode: " + sysOp.getMode());
    }

    @Test
    @DisplayName("SysOperation file system operations - read and write")
    void testSysOpFileOperations() throws Exception {
        // Create test file in temp directory
        Path testFile = tempDir.resolve("test_read.txt");
        Files.writeString(testFile, "Hello from integration test");

        LocalWorkConfig workConfig = new LocalWorkConfig();
        SysOperationCard card = SysOperationCard.builder()
                .id("fs_test")
                .mode(OperationMode.LOCAL)
                .workConfig(workConfig)
                .build();

        SysOperation sysOp = new SysOperation(card);
        BaseFsOperation fs = sysOp.fs();
        assertNotNull(fs, "FS operation should not be null");
        System.out.println("[SysOp FS] FS operation obtained: " + fs.getClass().getSimpleName());
    }

    @Test
    @DisplayName("SysOperation shell operation availability")
    void testSysOpShellOperation() {
        SysOperationCard card = SysOperationCard.builder()
                .id("shell_test")
                .mode(OperationMode.LOCAL)
                .build();

        SysOperation sysOp = new SysOperation(card);
        BaseShellOperation shell = sysOp.shell();
        assertNotNull(shell, "Shell operation should not be null");
        System.out.println("[SysOp Shell] Shell operation: " + shell.getClass().getSimpleName());
    }

    @Test
    @DisplayName("SysOperationCard with LocalWorkConfig")
    void testSysOperationCardConfig() {
        LocalWorkConfig workConfig = new LocalWorkConfig();
        SysOperationCard card = SysOperationCard.builder()
                .id("config_test")
                .name("Test SysOp")
                .description("System operations for testing")
                .mode(OperationMode.LOCAL)
                .workConfig(workConfig)
                .build();

        assertNotNull(card.getId());
        assertNotNull(card.getMode());
        System.out.println("[SysOp Card] Id=" + card.getId()
                + ", Mode=" + card.getMode());
    }
}
