/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.multiagent.teams.hierarchical_msgbus.SupervisorAgent;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Regression coverage for process-global skill tool registration and rail ownership. */
class SkillToolIsolationTest {
    @TempDir
    private Path tempDir;

    private MockedStatic<Runner> runner;

    @BeforeEach
    void isolateResourceRegistry() {
        ResourceMgr resources = new ResourceMgr();
        runner = mockStatic(Runner.class, CALLS_REAL_METHODS);

        // Registration and invocation use a real registry without touching other tests' owners or tags.
        runner.when(Runner::getResourceMgr).thenReturn(resources);
    }

    @AfterEach
    void restoreResourceRegistry() {
        runner.close();
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, false", "true, true", "false, true"})
    void coreAndHarnessSkillToolsCoexistInBothOrders(boolean isCoreFirst, boolean hasExistingCoreTool)
            throws Exception {
        if (!hasExistingCoreTool) {
            assertCoreAndHarnessCoexist(isCoreFirst);
            return;
        }
        try (SupervisorAgent existing = newSupervisor("existing-supervisor")) {
            Tool original = Runner.resourceMgr().getTool("skill_tool");
            assertCoreAndHarnessCoexist(isCoreFirst);
            assertThat(Runner.resourceMgr().getTool("skill_tool")).isSameAs(original);
        }
    }

    private void assertCoreAndHarnessCoexist(boolean isCoreFirst) throws Exception {
        try (DeepAgent agent = newAgent("harness")) {
            if (!isCoreFirst) {
                registerSkills(agent, "harness", "harness-only-body");
            }
            try (SupervisorAgent supervisor = newSupervisor("skill-supervisor")) {
                if (isCoreFirst) {
                    registerSkills(agent, "harness", "harness-only-body");
                }
                Tool coreTool = Runner.resourceMgr().getTool("skill_tool");
                assertThat(coreTool).isNotNull();
                assertThat(skillTool(agent, "skill_tool")).isNotSameAs(coreTool);
                assertSkillBody(agent, "harness-only-body");
            }
        }
    }

    @Test
    void sameCardAgentsKeepSeparateSkillsAcrossCloseAndRecreate() throws Exception {
        try (DeepAgent second = newAgent("second")) {
            registerSkills(second, "second", "second-only-body");
            assertFirstAgentIsIsolatedAndCloses(second);
            assertSkillBody(second, "second-only-body");
            try (DeepAgent replacement = newAgent("replacement")) {
                registerSkills(replacement, "replacement", "replacement-only-body");
                assertSkillBody(replacement, "replacement-only-body");
                assertSkillBody(second, "second-only-body");
            }
        }
    }

    private void assertFirstAgentIsIsolatedAndCloses(DeepAgent second) throws Exception {
        Set<String> firstIds;
        try (DeepAgent first = newAgent("first")) {
            firstIds = registerSkills(first, "first", "first-only-body").getOwnedToolIds();
            assertThat(firstIds).hasSize(2);
            Tool firstRead = skillTool(first, "skill_tool");
            assertThat(firstRead.getCard().getId())
                    .isNotEqualTo(skillTool(second, "skill_tool").getCard().getId());
            Tool firstList = skillTool(first, "list_skill");
            assertThat(firstList.getCard().getId())
                    .isNotEqualTo(skillTool(second, "list_skill").getCard().getId());
            assertSkillBody(first, "first-only-body");
            assertSkillBody(second, "second-only-body");
            assertSkillList(first, "first");
            assertSkillList(second, "second");
        }
        assertResourcesReleased(firstIds);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void unregisterRailReleasesOnlyItsOwnTools(boolean shouldRemoveActiveRail) throws Exception {
        try (DeepAgent agent = newAgent("shared")) {
            SkillUseRail first = registerSkills(agent, "first", "first-only-body");
            Set<String> firstIds = first.getOwnedToolIds();
            assertThat(firstIds).hasSize(2);
            SkillUseRail second = registerSkills(agent, "second", "second-only-body");
            Set<String> secondIds = second.getOwnedToolIds();
            assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
            assertSkillBody(agent, "first-only-body");

            SkillUseRail removed = second;
            SkillUseRail remaining = first;
            if (shouldRemoveActiveRail) {
                removed = first;
                remaining = second;
            }
            assertUnregisterReleasesOwnedResources(agent, removed, remaining);
            if (!shouldRemoveActiveRail) {
                assertSkillBody(agent, "first-only-body");
                assertSkillList(agent, "first");
            }
            Set<String> remainingIds = remaining.getOwnedToolIds();
            agent.unregisterRail(remaining).join();
            assertResourcesReleased(remainingIds);
        }
    }

    private static void assertUnregisterReleasesOwnedResources(
            DeepAgent agent, SkillUseRail removed, SkillUseRail remaining) {
        Set<String> removedIds = removed.getOwnedToolIds();
        agent.unregisterRail(removed).join();
        assertResourcesReleased(removedIds);
        assertThat(removed.getOwnedToolIds()).isEmpty();
        assertThat(removed.registeredToolNames()).isEmpty();
        for (String id : remaining.getOwnedToolIds()) {
            assertThat(Runner.resourceMgr().getTool(id)).isNotNull();
        }
        agent.unregisterRail(removed).join();
    }

    private static void assertResourcesReleased(Set<String> ids) {
        for (String id : ids) {
            assertThat(Runner.resourceMgr().getTool(id)).isNull();
        }
    }

    private static SupervisorAgent newSupervisor(String id) {
        return new SupervisorAgent(new AgentCard(id, "supervisor", "test"));
    }

    private DeepAgent newAgent(String directory) throws IOException {
        Path root = Files.createDirectories(tempDir.resolve(directory));
        return new DeepAgent(new AgentCard("same-skill-agent", "same-skill-agent", "test"),
                new DeepAgentConfig(), new Workspace(root.toString(), "en"));
    }

    private SkillUseRail registerSkills(DeepAgent agent, String directory, String body) throws IOException {
        Path root = tempDir.resolve(directory).resolve("skills");
        Path skill = Files.createDirectories(root.resolve("shared-name"));
        Files.writeString(skill.resolve("SKILL.md"),
                "---\nname: shared-name\ndescription: " + directory + "\n---\n" + body,
                StandardCharsets.UTF_8);
        SkillUseRail rail = new SkillUseRail(root.toString(),
                SkillUseRail.SKILL_MODE_AUTO_LIST, true, false, null, null);
        agent.registerRail(rail).join();
        assertThat(rail.getOwnedToolNames()).containsExactly("skill_tool", "list_skill");
        assertThat(rail.registeredToolNames()).containsExactly("skill_tool", "list_skill");
        return rail;
    }

    private static Tool skillTool(DeepAgent agent, String name) {
        String id = agent.getAbilityManager().getTools().get(name).getId();
        Tool tool = Runner.resourceMgr().getTool(id);
        assertThat(tool).isNotNull();
        assertThat(tool.getCard().getName()).isEqualTo(name);
        return tool;
    }

    private static void assertSkillBody(DeepAgent agent, String body) throws Exception {
        Object output = skillTool(agent, "skill_tool")
                .invoke(Map.of("skill_name", "shared-name"), Map.of());
        assertThat(output).isInstanceOfSatisfying(ToolOutput.class, result -> {
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isInstanceOfSatisfying(Map.class,
                    data -> assertThat(data.get("skill_content").toString()).endsWith(body));
        });
    }

    private static void assertSkillList(DeepAgent agent, String description) throws Exception {
        Object output = skillTool(agent, "list_skill").invoke(Map.of(), Map.of());
        assertThat(output).isInstanceOfSatisfying(ToolOutput.class, result -> {
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().toString()).contains("description=" + description);
        });
    }
}
