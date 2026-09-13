/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.SysOperationToolAdapter;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.registry.OperationDef;
import com.openjiuwen.core.sysop.registry.OperationRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

/**
 * SysOp system tests for registry and tool adapter wiring.
 * <p>
 * Local fs/shell/code behavior is covered by {@code Local*OperationTest} and
 * {@code SandboxOperationTest}; this class avoids duplicating those paths.
 */
@Tag("system-test")
class SysOpAdvancedSystemTest {
    @Nested
    @DisplayName("OperationRegistry Tests")
    class OperationRegistryTests {
        @Test
        @DisplayName("OperationRegistry has LOCAL mode operations")
        void testLocalOperationsRegistered() {
            List<String> localOps = OperationRegistry.getSupportedOperations(OperationMode.LOCAL);
            assertNotNull(localOps);
            assertFalse(localOps.isEmpty(), "Should have LOCAL operations registered");
        }

        @Test
        @DisplayName("OperationRegistry has SANDBOX mode operations")
        void testSandboxOperationsRegistered() {
            List<String> sandboxOps = OperationRegistry.getSupportedOperations(OperationMode.SANDBOX);
            assertNotNull(sandboxOps);
            assertFalse(sandboxOps.isEmpty(), "Should have SANDBOX operations registered");
        }

        @Test
        @DisplayName("OperationRegistry getOperationInfo for fs LOCAL")
        void testGetFsOperationInfo() {
            Optional<OperationDef> def = OperationRegistry.getOperationInfo("fs", OperationMode.LOCAL);
            assertTrue(def.isPresent(), "fs LOCAL operation should be registered");
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
            SysOperationCard card = SysOperationCard.builder().id("adapter_test").mode(OperationMode.LOCAL)
                    .workConfig(new LocalWorkConfig()).build();

            SysOperation sysOp = new SysOperation(card);
            List<SysOperationToolAdapter.ToolEntry> tools = SysOperationToolAdapter.extractTools(card, sysOp);

            assertNotNull(tools);
            assertFalse(tools.isEmpty(), "Should extract at least one tool");
            for (SysOperationToolAdapter.ToolEntry entry : tools) {
                assertNotNull(entry.toolId(), "Tool ID should not be null");
                assertNotNull(entry.localFunction(), "LocalFunction should not be null");
            }
        }

        @Test
        @DisplayName("ToolAdapter getToolIdPrefix formats correctly")
        void testToolIdPrefix() {
            String prefix = SysOperationToolAdapter.getToolIdPrefix("my_sysop");
            assertNotNull(prefix);
            assertTrue(prefix.startsWith("my_sysop"), "Prefix should start with sysop id");
        }
    }
}
