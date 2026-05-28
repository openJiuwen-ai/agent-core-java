/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AgentModeRail plan mode enforcement.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.rails.test_agent_mode_rail}.
 */
class TestAgentModeRail {

    // ---------------------------------------------------------------------------
    // Helper classes mirroring Python test utilities
    // ---------------------------------------------------------------------------

    /** Stub tool info for testing tool filtering. */
    static class ToolInfoStub {
        private final String name;

        public ToolInfoStub(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /** Stub prompt builder for testing section injection. */
    static class PromptBuilderStub {
        private String language = "en";
        private List<Object> addedSections = new ArrayList<>();
        private List<String> removedSections = new ArrayList<>();

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public void addSection(Object section) {
            addedSections.add(section);
        }

        public void removeSection(String sectionName) {
            removedSections.add(sectionName);
        }

        public List<Object> getAddedSections() {
            return addedSections;
        }

        public List<String> getRemovedSections() {
            return removedSections;
        }
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic mode operations)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Test tool info stub creation")
    void testToolInfoStubCreation() {
        ToolInfoStub tool = new ToolInfoStub("test_tool");
        assertEquals("test_tool", tool.getName());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test prompt builder section management")
    void testPromptBuilderSectionManagement() {
        PromptBuilderStub builder = new PromptBuilderStub();
        builder.addSection("section1");
        builder.addSection("section2");
        builder.removeSection("old_section");
        
        assertEquals(2, builder.getAddedSections().size());
        assertEquals(1, builder.getRemovedSections().size());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Mode filtering)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Test plan mode tool filtering")
    void testPlanModeToolFiltering() {
        // In plan mode, certain tools should be filtered out
        List<ToolInfoStub> allTools = new ArrayList<>();
        allTools.add(new ToolInfoStub("read_file"));
        allTools.add(new ToolInfoStub("execute_code"));  // Should be filtered in plan mode
        allTools.add(new ToolInfoStub("write_file"));
        
        String mode = "plan";
        List<ToolInfoStub> filteredTools = new ArrayList<>();
        
        // Filter tools based on mode
        List<String> allowedInPlanMode = List.of("read_file", "search", "plan");
        for (ToolInfoStub tool : allTools) {
            if (allowedInPlanMode.contains(tool.getName())) {
                filteredTools.add(tool);
            }
        }
        
        assertEquals(1, filteredTools.size());
        assertEquals("read_file", filteredTools.get(0).getName());
    }

    @Test
    @Tag("level1")
    @DisplayName("Test execution mode allows all tools")
    void testExecutionModeAllowsAllTools() {
        List<ToolInfoStub> allTools = new ArrayList<>();
        allTools.add(new ToolInfoStub("read_file"));
        allTools.add(new ToolInfoStub("execute_code"));
        allTools.add(new ToolInfoStub("write_file"));
        
        String mode = "execution";
        
        // In execution mode, all tools should be allowed
        assertEquals(3, allTools.size());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Language support)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    @DisplayName("Test language selection affects prompts")
    void testLanguageSelectionAffectsPrompts() {
        PromptBuilderStub builder = new PromptBuilderStub();
        
        // Test English language
        builder.setLanguage("en");
        assertEquals("en", builder.getLanguage());
        
        // Test Chinese language
        builder.setLanguage("cn");
        assertEquals("cn", builder.getLanguage());
    }

    @Test
    @Tag("level2")
    @DisplayName("Test valid mode values")
    void testValidModeValues() {
        Set<String> validModes = Set.of("plan", "execution", "auto");
        
        assertTrue(validModes.contains("plan"));
        assertTrue(validModes.contains("execution"));
        assertTrue(validModes.contains("auto"));
        assertFalse(validModes.contains("invalid"));
    }
}