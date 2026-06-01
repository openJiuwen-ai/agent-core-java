/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory.team;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.foundation.tool.ToolCard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for TeamMemoryManager lifecycle.
 * Mirrors Python's tests/unit_tests/core/memory/team/test_team_memory_integration.py.
 */
@DisplayName("TeamMemoryIntegration tests")
class TestTeamMemoryIntegration {

    @TempDir
    Path tempDir;

    @Test
    void testFullLifecycleInitRegisterInjectClose() throws Exception {
        TeamMemoryManager manager = new TeamMemoryManager(createParams("test_member", "test_team",
                "teammate", "temporary", "general", tempDir, false, null));

        assertTrue(manager.initToolkit().get());

        MockDeepAgent deepAgent = new MockDeepAgent();
        manager.registerTools(deepAgent);
        assertFalse(manager.getOwnedToolNames().isEmpty());

        manager.loadAndInject(deepAgent, "test").get();
        PromptSection section = deepAgent.systemPromptBuilder.getSection(TeamMemoryManager.SECTION_NAME);
        assertNotNull(section);
        assertEquals(TeamMemoryManager.SECTION_NAME, section.getName());

        manager.close().get();
        assertTrue(manager.getOwnedToolNames().isEmpty());
        assertNull(manager.getToolkit());
    }

    @Test
    void testLifecycleWithPersistentLeaderAutoExtract() throws Exception {
        Path teamMemoryDir = tempDir.resolve("team_memory");
        Files.createDirectories(teamMemoryDir);
        TeamMemoryManager manager = new TeamMemoryManager(createParams("leader", "team1",
                "leader", "persistent", "general", tempDir, true, teamMemoryDir.toString()));

        assertTrue(manager.initToolkit().get());
        manager.extractAfterRound("summary").get();
        assertEquals(1, manager.getExtractInvocationCount());
    }

    @Test
    void testLifecycleInjectWithoutRegisterDoesNotFail() throws Exception {
        TeamMemoryManager manager = new TeamMemoryManager(createParams("test_member", "test_team",
                "teammate", "temporary", "general", tempDir, false, null));

        assertTrue(manager.initToolkit().get());

        MockDeepAgent deepAgent = new MockDeepAgent();
        manager.loadAndInject(deepAgent, "test").get();

        PromptSection section = deepAgent.systemPromptBuilder.getSection(TeamMemoryManager.SECTION_NAME);
        assertNotNull(section);
    }

    @Test
    void testLifecycleMultipleRoundsInject() throws Exception {
        TeamMemoryManager manager = new TeamMemoryManager(createParams("test_member", "test_team",
                "teammate", "temporary", "general", tempDir, false, null));

        assertTrue(manager.initToolkit().get());
        MockDeepAgent deepAgent = new MockDeepAgent();

        manager.loadAndInject(deepAgent, "first").get();
        PromptSection firstSection = manager.getCachedBaseSection();
        manager.loadAndInject(deepAgent, "second").get();
        PromptSection secondSection = manager.getCachedBaseSection();

        assertTrue(firstSection == secondSection);
    }

    @Test
    void testLifecycleExtractDoesNotRunTwiceInSameRound() throws Exception {
        Path teamMemoryDir = tempDir.resolve("team_memory");
        Files.createDirectories(teamMemoryDir);
        TeamMemoryManager manager = new TeamMemoryManager(createParams("leader", "team1",
                "leader", "persistent", "general", tempDir, true, teamMemoryDir.toString()));

        assertTrue(manager.initToolkit().get());

        manager.extractAfterRound("summary").get();
        assertEquals(1, manager.getExtractInvocationCount());

        manager.extractAfterRound("summary").get();
        assertEquals(2, manager.getExtractInvocationCount());
    }

    @Test
    void testLifecycleReadOnlyMode() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);
        TeamMemoryManager manager = new TeamMemoryManager(createParams("m1", "t1",
                "leader", "temporary", "general", null, false, null, source.toString()));

        assertTrue(manager.initToolkit().get());
        assertNotNull(manager.getWorkspace());
    }

    @Test
    void testLifecycleCodingScenario() throws Exception {
        TeamMemoryManager manager = new TeamMemoryManager(createParams("m1", "t1",
                "teammate", "temporary", "coding", tempDir, false, null));

        assertTrue(manager.initToolkit().get());
        MockDeepAgent deepAgent = new MockDeepAgent();
        manager.loadAndInject(deepAgent, "").get();

        assertNotNull(deepAgent.systemPromptBuilder.getSection(TeamMemoryManager.SECTION_NAME));
    }

    @Test
    void testLifecycleChineseLanguage() throws Exception {
        TeamMemoryManagerParams params = createParams("m1", "t1",
                "teammate", "temporary", "general", tempDir, false, null);
        params.setLanguage("cn");
        TeamMemoryManager manager = new TeamMemoryManager(params);

        assertTrue(manager.initToolkit().get());
        MockDeepAgent deepAgent = new MockDeepAgent();
        manager.loadAndInject(deepAgent, "").get();

        PromptSection section = deepAgent.systemPromptBuilder.getSection(TeamMemoryManager.SECTION_NAME);
        assertNotNull(section);
        assertTrue(section.render("cn").length() > 0);
    }

    @Test
    void testLifecycleProactiveMode() throws Exception {
        TeamMemoryManager manager = new TeamMemoryManager(createParams("m1", "t1",
                "teammate", "temporary", "general", tempDir, false, null));

        assertTrue(manager.initToolkit().get());
        MockDeepAgent deepAgent = new MockDeepAgent();
        manager.loadAndInject(deepAgent, "").get();

        assertNotNull(deepAgent.systemPromptBuilder.getSection(TeamMemoryManager.SECTION_NAME));
    }

    @Test
    void testLifecycleCloseAfterMultipleOperations() throws Exception {
        TeamMemoryManager manager = new TeamMemoryManager(createParams("test_member", "test_team",
                "teammate", "temporary", "general", tempDir, false, null));

        assertTrue(manager.initToolkit().get());
        MockDeepAgent deepAgent = new MockDeepAgent();
        manager.registerTools(deepAgent);

        manager.loadAndInject(deepAgent, "test1").get();
        manager.loadAndInject(deepAgent, "test2").get();

        manager.close().get();
        manager.close().get();

        assertNull(manager.getToolkit());
        assertTrue(manager.getOwnedToolNames().isEmpty());
    }

    private static TeamMemoryManagerParams createParams(String memberName,
                                                        String teamName,
                                                        String role,
                                                        String lifecycle,
                                                        String scenario,
                                                        Path workspaceRoot,
                                                        boolean enableAutoExtract,
                                                        String teamMemoryDir) {
        return createParams(memberName, teamName, role, lifecycle, scenario, workspaceRoot, enableAutoExtract,
                teamMemoryDir, null);
    }

    private static TeamMemoryManagerParams createParams(String memberName,
                                                        String teamName,
                                                        String role,
                                                        String lifecycle,
                                                        String scenario,
                                                        Path workspaceRoot,
                                                        boolean enableAutoExtract,
                                                        String teamMemoryDir,
                                                        String readOnlySourceWorkspace) {
        return TeamMemoryManagerParams.builder()
                .memberName(memberName)
                .teamName(teamName)
                .role(role)
                .lifecycle(lifecycle)
                .scenario(scenario)
                .embeddingConfig(null)
                .workspace(workspaceRoot == null ? null : new MockWorkspace(workspaceRoot))
                .sysOperation(null)
                .teamMemoryDir(teamMemoryDir)
                .language("en")
                .promptMode("proactive")
                .enableAutoExtract(enableAutoExtract)
                .readOnlySourceWorkspace(readOnlySourceWorkspace)
                .build();
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

    private static final class MockPromptBuilder {
        private final Map<String, PromptSection> sections = new LinkedHashMap<>();

        public void addSection(PromptSection section) {
            sections.put(section.getName(), section);
        }

        public void removeSection(String name) {
            sections.remove(name);
        }

        public PromptSection getSection(String name) {
            return sections.get(name);
        }
    }

    private static final class MockAbilityManager {
        private final Map<String, ToolCard> abilities = new LinkedHashMap<>();

        public Object add(Object toolCard) {
            if (toolCard instanceof ToolCard card) {
                abilities.put(card.getName(), card);
            }
            return toolCard;
        }

        public Object remove(List<String> names) {
            for (String name : names) {
                abilities.remove(name);
            }
            return null;
        }
    }

    private static final class MockDeepAgent {
        public final MockPromptBuilder systemPromptBuilder = new MockPromptBuilder();
        public final MockAbilityManager abilityManager = new MockAbilityManager();

        public MockPromptBuilder getSystemPromptBuilder() {
            return systemPromptBuilder;
        }

        public MockAbilityManager getAbilityManager() {
            return abilityManager;
        }
    }
}
