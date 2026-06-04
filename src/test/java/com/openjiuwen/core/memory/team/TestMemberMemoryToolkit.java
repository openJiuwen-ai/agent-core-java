/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory.team;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.core.memory.lite.MemoryToolContext;
import com.openjiuwen.core.runner.Runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for MemberMemoryToolkit.
 * Mirrors Python's tests/unit_tests/core/memory/team/test_member_memory_toolkit.py.
 */
@DisplayName("MemberMemoryToolkit tests")
class TestMemberMemoryToolkit {

    @TempDir
    Path tempDir;

    @Test
    void testMemberMemoryToolkitInitializationGeneralScenario() throws Exception {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("alice", "team1", workspace(tempDir), "general");

        assertTrue(toolkit.initialize().get());
        assertEquals("general", toolkit.getScenario());
        assertInstanceOf(MemoryToolContext.class, toolkit.getCtx());
    }

    @Test
    void testMemberMemoryToolkitInitializationCodingScenario() throws Exception {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("bob", "team1", workspace(tempDir), "coding");

        assertTrue(toolkit.initialize().get());
        assertEquals("coding", toolkit.getScenario());
        assertInstanceOf(CodingMemoryToolContext.class, toolkit.getCtx());
    }

    @Test
    void testMemberMemoryToolkitScenarioNormalization() {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("charlie", "team1", workspace(tempDir), "  CODING  ");

        assertEquals("coding", toolkit.getScenario());
    }

    @Test
    void testMemberMemoryToolkitReadOnlyFlag() {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("dave", "team1", workspace(tempDir), "general", true);

        assertTrue(toolkit.isReadOnly());
    }

    @Test
    void testMemberMemoryToolkitGetToolsReturnsList() throws Exception {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("eve", "team1", workspace(tempDir), "general");

        toolkit.initialize().get();

        assertFalse(toolkit.getTools().isEmpty());
    }

    @Test
    void testMemberMemoryToolkitGetToolCardsReturnsList() throws Exception {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("frank", "team1", workspace(tempDir), "general");

        toolkit.initialize().get();

        assertFalse(toolkit.getToolCards().isEmpty());
        assertTrue(toolkit.getToolCards().stream().allMatch(ToolCard.class::isInstance));
    }

    @Test
    void testMemberMemoryToolkitClose() throws Exception {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("grace", "team1", workspace(tempDir), "general");
        toolkit.initialize().get();

        toolkit.close().get();

        assertNull(toolkit.getManager());
        assertNull(toolkit.getCtx());
        assertTrue(toolkit.getTools().isEmpty());
        assertFalse(toolkit.isInitialized());
    }

    @Test
    void testMemberMemoryToolkitManagerProperty() throws Exception {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("henry", "team1", workspace(tempDir), "general");

        assertNull(toolkit.getManager());
        toolkit.initialize().get();
        assertNotNull(toolkit.getManager());
    }

    @Test
    void testCreateGeneralToolsReturnsList() {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("iris", "team1", workspace(tempDir), "general");

        List<Tool> tools = MemberMemoryToolkit.createGeneralTools(toolkit, false);

        assertFalse(tools.isEmpty());
        assertTrue(toolNames(tools).contains("memory_search"));
    }

    @Test
    void testCreateGeneralToolsReadOnly() {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("jack", "team1", workspace(tempDir), "general");

        List<Tool> toolsRw = MemberMemoryToolkit.createGeneralTools(toolkit, false);
        List<Tool> toolsRo = MemberMemoryToolkit.createGeneralTools(toolkit, true);

        assertTrue(toolsRo.size() < toolsRw.size());
        assertFalse(toolNames(toolsRo).contains("write_memory"));
        assertFalse(toolNames(toolsRo).contains("edit_memory"));
    }

    @Test
    void testCreateCodingToolsReturnsList() {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("kate", "team1", workspace(tempDir), "coding");

        List<Tool> tools = MemberMemoryToolkit.createCodingTools(toolkit, false);

        assertFalse(tools.isEmpty());
        assertTrue(toolNames(tools).contains("coding_memory_read"));
    }

    @Test
    void testCreateCodingToolsReadOnly() {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("leo", "team1", workspace(tempDir), "coding");

        List<Tool> toolsRw = MemberMemoryToolkit.createCodingTools(toolkit, false);
        List<Tool> toolsRo = MemberMemoryToolkit.createCodingTools(toolkit, true);

        assertTrue(toolsRo.size() < toolsRw.size());
        assertFalse(toolNames(toolsRo).contains("coding_memory_write"));
        assertFalse(toolNames(toolsRo).contains("coding_memory_edit"));
    }

    @Test
    void testDifferentMembersHaveDifferentToolIds() {
        MemberMemoryToolkit toolkit1 = new MemberMemoryToolkit("alice", "team1", workspace(tempDir), "general");
        MemberMemoryToolkit toolkit2 = new MemberMemoryToolkit("bob", "team1", workspace(tempDir), "general");

        Set<String> ids1 = toolIds(MemberMemoryToolkit.createGeneralTools(toolkit1, false));
        Set<String> ids2 = toolIds(MemberMemoryToolkit.createGeneralTools(toolkit2, false));

        assertTrue(ids1.stream().noneMatch(ids2::contains));
    }

    @Test
    void testTwoToolkitsSameTeamDifferentManagersWhenInitialized() throws Exception {
        Path rootA = tempDir.resolve("a");
        Path rootB = tempDir.resolve("b");
        Files.createDirectories(rootA);
        Files.createDirectories(rootB);
        MemberMemoryToolkit toolkitA = new MemberMemoryToolkit("m1", "team_iso", workspace(rootA), "general");
        MemberMemoryToolkit toolkitB = new MemberMemoryToolkit("m2", "team_iso", workspace(rootB), "general");

        assertTrue(toolkitA.initialize().get());
        assertTrue(toolkitB.initialize().get());

        assertNotSame(toolkitA.getManager(), toolkitB.getManager());

        toolkitA.close().get();
        toolkitB.close().get();
    }

    @Test
    void testInitializeCloseLeavesRunnerToolRegistryUnchanged() throws Exception {
        String toolId = "team_x.solo.memory_search";
        Object before = Runner.resourceMgr().getTool(toolId);
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("solo", "team_x", workspace(tempDir), "general");

        assertTrue(toolkit.initialize().get());
        toolkit.close().get();

        assertEquals(before, Runner.resourceMgr().getTool(toolId));
    }

    @Test
    void testGeneralToolNamesIncludeSearchCodingIncludesRead() {
        MemberMemoryToolkit toolkitGeneral = new MemberMemoryToolkit("g", "t", workspace(tempDir), "general");
        MemberMemoryToolkit toolkitCoding = new MemberMemoryToolkit("c", "t", workspace(tempDir), "coding");

        assertTrue(toolNames(MemberMemoryToolkit.createGeneralTools(toolkitGeneral, false)).contains("memory_search"));
        assertTrue(toolNames(MemberMemoryToolkit.createCodingTools(toolkitCoding, false)).contains("coding_memory_read"));
    }

    @Test
    void testReadOnlyInitializedToolsAreReadOnlyOnly() throws Exception {
        MemberMemoryToolkit toolkit = new MemberMemoryToolkit("ro", "t", workspace(tempDir), "general", true);

        assertTrue(toolkit.initialize().get());

        Set<String> names = toolNames(toolkit.getTools());
        assertTrue(names.contains("memory_search"));
        assertFalse(names.contains("write_memory"));
        assertFalse(names.contains("edit_memory"));

        toolkit.close().get();
    }

    private static MockWorkspace workspace(Path root) {
        return new MockWorkspace(root);
    }

    private static Set<String> toolNames(List<Tool> tools) {
        return tools.stream().map(tool -> tool.getCard().getName()).collect(Collectors.toSet());
    }

    private static Set<String> toolIds(List<Tool> tools) {
        return tools.stream().map(tool -> tool.getCard().getId()).collect(Collectors.toSet());
    }

    private static final class MockWorkspace {
        private final Path root;

        private MockWorkspace(Path root) {
            this.root = root;
        }

        public Path getNodePath(String nodeName) throws IOException {
            Path nodePath = root.resolve(nodeName);
            Files.createDirectories(nodePath);
            return nodePath;
        }
    }
}
