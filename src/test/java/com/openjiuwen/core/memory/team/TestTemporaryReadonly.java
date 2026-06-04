/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory.team;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Temporary lifecycle plus read-only source workspace tests.
 * Mirrors Python's tests/unit_tests/core/memory/team/test_temporary_readonly.py.
 */
@DisplayName("Temporary readonly tests")
class TestTemporaryReadonly {

    @TempDir
    Path tempDir;

    @Test
    void testReadOnlyManagerWorkspaceRootMatchesSource() {
        TeamMemoryManager manager = new TeamMemoryManager(readOnlyParams("general"));

        assertInstanceOf(Workspace.class, manager.getWorkspace());
        Workspace workspace = (Workspace) manager.getWorkspace();
        assertEquals(tempDir.normalize().toString(), Path.of(workspace.getRootPath()).normalize().toString());
    }

    @Test
    void testInitToolkitReadOnlyTools() throws Exception {
        TeamMemoryManager manager = new TeamMemoryManager(readOnlyParams("general"));

        assertTrue(manager.initToolkit().get());
        assertNotNull(manager.getToolkit());
        assertTrue(manager.getToolkit().isReadOnly());

        Set<String> names = manager.getToolkit().getTools().stream()
                .map(Tool::getCard)
                .map(card -> card.getName())
                .collect(Collectors.toSet());
        assertTrue(names.contains("memory_search"));
        assertFalse(names.contains("write_memory"));
        assertFalse(names.contains("edit_memory"));

        manager.close().get();
    }

    @Test
    void testLoadAndInjectPassesReadOnlyToBuildMemorySection() throws Exception {
        TeamMemoryManager manager = new TeamMemoryManager(readOnlyParams("general"));
        assertTrue(manager.initToolkit().get());

        MockDeepAgentLite deepAgent = new MockDeepAgentLite();
        manager.loadAndInject(deepAgent, "").get();

        PromptSection section = deepAgent.systemPromptBuilder.getSection(TeamMemoryManager.SECTION_NAME);
        assertNotNull(section);
        assertTrue(section.render("en").toLowerCase().contains("read"));

        manager.close().get();
    }

    @Test
    void testLoadAndInjectCodingPassesReadOnly() throws Exception {
        TeamMemoryManager manager = new TeamMemoryManager(readOnlyParams("coding"));
        assertTrue(manager.initToolkit().get());

        MockDeepAgentLite deepAgent = new MockDeepAgentLite();
        manager.loadAndInject(deepAgent, "").get();

        PromptSection section = deepAgent.systemPromptBuilder.getSection(TeamMemoryManager.SECTION_NAME);
        assertNotNull(section);
        assertTrue(manager.getToolkit().isReadOnly());

        manager.close().get();
    }

    private TeamMemoryManagerParams readOnlyParams(String scenario) {
        return TeamMemoryManagerParams.builder()
                .memberName("m1")
                .teamName("t1")
                .role("teammate")
                .lifecycle("temporary")
                .scenario(scenario)
                .embeddingConfig(null)
                .workspace(null)
                .sysOperation(null)
                .teamMemoryDir(null)
                .language("en")
                .promptMode("proactive")
                .enableAutoExtract(false)
                .readOnlySourceWorkspace(tempDir.toString())
                .build();
    }

    private static final class MockPromptBuilder {
        private final Map<String, PromptSection> sections = new LinkedHashMap<>();

        public void addSection(PromptSection section) {
            sections.put(section.getName(), section);
        }

        public PromptSection getSection(String name) {
            return sections.get(name);
        }
    }

    private static final class MockDeepAgentLite {
        public final MockPromptBuilder systemPromptBuilder = new MockPromptBuilder();

        public MockPromptBuilder getSystemPromptBuilder() {
            return systemPromptBuilder;
        }
    }
}
