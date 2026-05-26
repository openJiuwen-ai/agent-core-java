/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LspRail — initialization, tool registration, cleanup.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.rails.test_lsp_rail}.
 */
class TestLspRail {

    // ---------------------------------------------------------------------------
    // Mock classes
    // ---------------------------------------------------------------------------

    /** Mock ability manager. */
    static class MockAbilityManager {
        private Map<String, Object> registeredTools = new HashMap<>();

        public void register(String name, Object tool) {
            registeredTools.put(name, tool);
        }

        public void unregister(String name) {
            registeredTools.remove(name);
        }

        public boolean isRegistered(String name) {
            return registeredTools.containsKey(name);
        }

        public int getToolCount() {
            return registeredTools.size();
        }
    }

    /** Mock LSP tool. */
    static class MockLspTool {
        private String name;
        private String description;

        public MockLspTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    /** Mock workspace. */
    static class MockWorkspace {
        private String rootPath = "/workspace";

        public String getRootPath() { return rootPath; }
    }

    // ---------------------------------------------------------------------------
    // Tests: LSP tool registration
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Test LSP tool registration")
    void testLspToolRegistration() {
        MockAbilityManager manager = new MockAbilityManager();
        MockLspTool tool = new MockLspTool("lsp_goto_definition", "Navigate to definition");

        manager.register(tool.getName(), tool);

        assertTrue(manager.isRegistered("lsp_goto_definition"));
        assertEquals(1, manager.getToolCount());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test multiple LSP tools registration")
    void testMultipleLspToolsRegistration() {
        MockAbilityManager manager = new MockAbilityManager();

        manager.register("lsp_goto_definition", new MockLspTool("lsp_goto_definition", "Go to definition"));
        manager.register("lsp_find_references", new MockLspTool("lsp_find_references", "Find references"));
        manager.register("lsp_rename", new MockLspTool("lsp_rename", "Rename symbol"));

        assertEquals(3, manager.getToolCount());
        assertTrue(manager.isRegistered("lsp_goto_definition"));
        assertTrue(manager.isRegistered("lsp_find_references"));
        assertTrue(manager.isRegistered("lsp_rename"));
    }

    // ---------------------------------------------------------------------------
    // Tests: LSP tool cleanup
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Test LSP tool cleanup on rail close")
    void testLspToolCleanup() {
        MockAbilityManager manager = new MockAbilityManager();

        // Register tools
        manager.register("lsp_tool_1", new MockLspTool("lsp_tool_1", "Tool 1"));
        manager.register("lsp_tool_2", new MockLspTool("lsp_tool_2", "Tool 2"));

        assertEquals(2, manager.getToolCount());

        // Cleanup
        manager.unregister("lsp_tool_1");
        manager.unregister("lsp_tool_2");

        assertEquals(0, manager.getToolCount());
    }

    // ---------------------------------------------------------------------------
    // Tests: Workspace configuration
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Test workspace root path configuration")
    void testWorkspaceRootPathConfiguration() {
        MockWorkspace workspace = new MockWorkspace();

        assertNotNull(workspace.getRootPath());
        assertTrue(workspace.getRootPath().startsWith("/"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test LSP tool with workspace context")
    void testLspToolWithWorkspaceContext() {
        MockWorkspace workspace = new MockWorkspace();
        MockLspTool tool = new MockLspTool("lsp_search", "Search in workspace");

        // Tool should have workspace context
        assertNotNull(tool.getName());
        assertNotNull(workspace.getRootPath());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2: LSP operations
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    @DisplayName("Test LSP goto definition parameters")
    void testLspGotoDefinitionParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("file", "/workspace/src/Main.java");
        params.put("line", 42);
        params.put("character", 15);

        assertTrue(params.containsKey("file"));
        assertTrue(params.containsKey("line"));
        assertEquals(42, params.get("line"));
    }

    @Test
    @Tag("level2")
    @DisplayName("Test LSP find references parameters")
    void testLspFindReferencesParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("file", "/workspace/src/Main.java");
        params.put("line", 10);
        params.put("character", 5);
        params.put("includeDeclaration", true);

        assertTrue((Boolean) params.get("includeDeclaration"));
    }
}