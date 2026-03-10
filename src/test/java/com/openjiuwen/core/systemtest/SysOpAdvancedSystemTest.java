/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.SysOperationToolAdapter;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.local.LocalCodeOperation;
import com.openjiuwen.core.sysop.local.LocalFsOperation;
import com.openjiuwen.core.sysop.local.LocalShellOperation;
import com.openjiuwen.core.sysop.registry.OperationRegistry;
import com.openjiuwen.core.sysop.registry.OperationDef;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.sysop.result.WriteFileResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Advanced SysOp system tests covering gaps identified in CHECK doc:
 * SysOperationToolAdapter, OperationRegistry, LocalCode/Shell/Fs operations.
 * All tests are local (no remote API required).
 */
@Tag("system-test")
class SysOpAdvancedSystemTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("OperationRegistry Tests")
    class OperationRegistryTests {

        @Test
        @DisplayName("OperationRegistry has LOCAL mode operations")
        void testLocalOperationsRegistered() {
            List<String> localOps = OperationRegistry.getSupportedOperations(OperationMode.LOCAL);
            assertNotNull(localOps);
            assertFalse(localOps.isEmpty(), "Should have LOCAL operations registered");
            System.out.println("[OperationRegistry LOCAL] Operations: " + localOps);
        }

        @Test
        @DisplayName("OperationRegistry has SANDBOX mode operations")
        void testSandboxOperationsRegistered() {
            List<String> sandboxOps = OperationRegistry.getSupportedOperations(OperationMode.SANDBOX);
            assertNotNull(sandboxOps);
            assertFalse(sandboxOps.isEmpty(), "Should have SANDBOX operations registered");
            System.out.println("[OperationRegistry SANDBOX] Operations: " + sandboxOps);
        }

        @Test
        @DisplayName("OperationRegistry getOperationInfo for fs LOCAL")
        void testGetFsOperationInfo() {
            Optional<OperationDef> def = OperationRegistry.getOperationInfo("fs", OperationMode.LOCAL);
            assertTrue(def.isPresent(), "fs LOCAL operation should be registered");
            System.out.println("[OperationRegistry] fs LOCAL: " + def.get());
        }

        @Test
        @DisplayName("OperationRegistry getOperationInfo for shell LOCAL")
        void testGetShellOperationInfo() {
            Optional<OperationDef> def = OperationRegistry.getOperationInfo("shell", OperationMode.LOCAL);
            assertTrue(def.isPresent(), "shell LOCAL operation should be registered");
        }

        @Test
        @DisplayName("OperationRegistry getOperationInfo for code LOCAL")
        void testGetCodeOperationInfo() {
            Optional<OperationDef> def = OperationRegistry.getOperationInfo("code", OperationMode.LOCAL);
            assertTrue(def.isPresent(), "code LOCAL operation should be registered");
        }
    }

    @Nested
    @DisplayName("SysOperationToolAdapter Tests")
    class ToolAdapterTests {

        @Test
        @DisplayName("ToolAdapter extracts tools from SysOperation")
        void testExtractTools() {
            SysOperationCard card = SysOperationCard.builder()
                    .id("adapter_test")
                    .mode(OperationMode.LOCAL)
                    .workConfig(new LocalWorkConfig())
                    .build();

            SysOperation sysOp = new SysOperation(card);
            List<SysOperationToolAdapter.ToolEntry> tools =
                    SysOperationToolAdapter.extractTools(card, sysOp);

            assertNotNull(tools);
            assertFalse(tools.isEmpty(), "Should extract at least one tool");
            for (SysOperationToolAdapter.ToolEntry entry : tools) {
                assertNotNull(entry.toolId(), "Tool ID should not be null");
                assertNotNull(entry.localFunction(), "LocalFunction should not be null");
                System.out.println("[ToolAdapter] Tool: " + entry.toolId());
            }
        }

        @Test
        @DisplayName("ToolAdapter getToolIdPrefix formats correctly")
        void testToolIdPrefix() {
            String prefix = SysOperationToolAdapter.getToolIdPrefix("my_sysop");
            assertNotNull(prefix);
            assertTrue(prefix.startsWith("my_sysop"), "Prefix should start with sysop id");
            System.out.println("[ToolAdapter] Prefix: " + prefix);
        }
    }

    @Nested
    @DisplayName("LocalFsOperation Tests")
    class LocalFsOperationTests {

        @Test
        @DisplayName("LocalFsOperation writeFile and readFile")
        void testWriteAndReadFile() throws Exception {
            LocalFsOperation fs = new LocalFsOperation(null);

            String filePath = tempDir.resolve("test_fs_write.txt").toString();

            WriteFileResult writeResult = fs.writeFile(
                    filePath, "Hello from system test", "w",
                    false, true, true, null, "UTF-8", null);

            assertNotNull(writeResult);
            System.out.println("[LocalFs Write] Result: " + writeResult);

            ReadFileResult readResult = fs.readFile(
                    filePath, "r", null, null, null, "UTF-8", 4096, null);

            assertNotNull(readResult);
            System.out.println("[LocalFs Read] Result: " + readResult);
        }

        @Test
        @DisplayName("LocalFsOperation writeFile creates file if not exists")
        void testWriteCreatesFile() throws Exception {
            LocalFsOperation fs = new LocalFsOperation(null);
            String filePath = tempDir.resolve("new_file.txt").toString();

            WriteFileResult result = fs.writeFile(
                    filePath, "New file content", "w",
                    false, false, true, null, "UTF-8", null);

            assertNotNull(result);
            assertTrue(Files.exists(Path.of(filePath)), "File should be created");
        }
    }

    @Nested
    @DisplayName("LocalShellOperation Tests")
    class LocalShellOperationTests {

        @Test
        @DisplayName("LocalShellOperation executeCmd echo")
        void testExecuteEcho() {
            LocalShellOperation shell = new LocalShellOperation(null);

            ExecuteCmdResult result = shell.executeCmd(
                    "echo HelloSystemTest",
                    tempDir.toString(),
                    30,
                    null,
                    null);

            assertNotNull(result);
            System.out.println("[LocalShell] Result: " + result);
        }
    }

    @Nested
    @DisplayName("LocalCodeOperation Tests")
    class LocalCodeOperationTests {

        @Test
        @DisplayName("LocalCodeOperation executeCode Python")
        void testExecutePython() {
            LocalCodeOperation code = new LocalCodeOperation(null);
            try {
                var result = code.executeCode(
                        "print('hello from python')",
                        "python",
                        30,
                        null,
                        null);
                assertNotNull(result);
                System.out.println("[LocalCode Python] Result: " + result);
            } catch (Exception e) {
                // Python may not be available on all test machines
                System.out.println("[LocalCode Python] Skipped: " + e.getMessage());
            }
        }
    }
}
