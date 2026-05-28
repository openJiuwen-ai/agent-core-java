/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.*;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeepAgent workspace.
 */
class TestDeepAgentWorkspace {

    @Test
    @Tag("level0")
    @DisplayName("Workspace handles workspace operations")
    void testWorkspaceHandlesOperations() {
        Workspace workspace = new Workspace("/tmp/test", "cn");
        assertNotNull(workspace, "Workspace should be constructable");
        assertEquals("/tmp/test", workspace.getRootPath(), "Root path should match");
        assertEquals("cn", workspace.getLanguage(), "Language should match");
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Workspace resolves paths correctly")
    void testWorkspaceResolvesPaths() {
        Workspace workspace = new Workspace("/tmp/test", "en");
        Path root = workspace.root();
        assertNotNull(root, "Root path should not be null");
        
        Path resolved = workspace.resolve("subdir/file.txt");
        assertNotNull(resolved, "Resolved path should not be null");
    }
    
    @Test
    @Tag("level0")
    @DisplayName("Workspace defaults are correct")
    void testWorkspaceDefaults() {
        Workspace workspace = new Workspace();
        assertEquals("./", workspace.getRootPath(), "Default root path should be ./");
        assertEquals("cn", workspace.getLanguage(), "Default language should be cn");
    }
}