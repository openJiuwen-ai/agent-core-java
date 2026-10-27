/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.multiagent.teams.hierarchical_msgbus.SupervisorAgent;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Regression coverage for process-global skill tool registration and rail ownership. */
class SkillToolIsolationTest {
    @TempDir
    private Path tempDir;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void coreAndHarnessSkillToolsCoexistInBothOrders(boolean isCoreFirst) throws Exception {
        Tool original = Runner.resourceMgr().getTool("skill_tool");
        try {
            assertCoreAndHarnessCoexist(isCoreFirst);
        } finally {
            if (original == null) {
                Runner.resourceMgr().removeTool("skill_tool");
            }
        }
        assertThat(Runner.resourceMgr().getTool("skill_tool")).isSameAs(original);
    }

    private void assertCoreAndHarnessCoexist(boolean isCoreFirst) throws Exception {
        try (DeepAgent agent = newAgent("harness")) {
            if (isCoreFirst) {
                new SupervisorAgent(new AgentCard("skill-supervisor", "supervisor", "test"));
            }
            registerSkills(agent, "harness", "harness-only-body");
            if (!isCoreFirst) {
                new SupervisorAgent(new AgentCard("skill-supervisor", "supervisor", "test"));
            }
            Tool coreTool = Runner.resourceMgr().getTool("skill_tool");
            assertThat(coreTool).isNotNull();
            assertThat(skillTool(agent, "skill_tool")).isNotSameAs(coreTool);
            assertSkillBody(agent, "harness-only-body");
        }
    }

    @Test
    void sameCardAgentsKeepSeparateSkillsAcrossCloseAndRecreate() throws Exception {
        try (DeepAgent first = newAgent("first"); DeepAgent second = newAgent("second")) {
            registerSkills(first, "first", "first-only-body");
            registerSkills(second, "second", "second-only-body");
            Tool firstRead = skillTool(first, "skill_tool");
            Tool firstList = skillTool(first, "list_skill");
            assertThat(firstRead.getCard().getId())
                    .isNotEqualTo(skillTool(second, "skill_tool").getCard().getId());
            assertThat(firstList.getCard().getId())
                    .isNotEqualTo(skillTool(second, "list_skill").getCard().getId());
            assertSkillBody(first, "first-only-body");
            assertSkillBody(second, "second-only-body");
            assertSkillList(first, "first");
            assertSkillList(second, "second");

            first.close();
            assertThat(Runner.resourceMgr().getTool(firstRead.getCard().getId())).isNull();
            assertThat(Runner.resourceMgr().getTool(firstList.getCard().getId())).isNull();
            assertSkillBody(second, "second-only-body");
            try (DeepAgent replacement = newAgent("replacement")) {
                registerSkills(replacement, "replacement", "replacement-only-body");
                assertSkillBody(replacement, "replacement-only-body");
                assertSkillBody(second, "second-only-body");
            }
        }
    }

    private DeepAgent newAgent(String directory) throws IOException {
        Path root = Files.createDirectories(tempDir.resolve(directory));
        return new DeepAgent(new AgentCard("same-skill-agent", "same-skill-agent", "test"),
                new DeepAgentConfig(), new Workspace(root.toString(), "en"));
    }

    private void registerSkills(DeepAgent agent, String directory, String body) throws IOException {
        Path root = tempDir.resolve(directory).resolve("skills");
        Path skill = Files.createDirectories(root.resolve("shared-name"));
        Files.writeString(skill.resolve("SKILL.md"),
                "---\nname: shared-name\ndescription: " + directory + "\n---\n" + body,
                StandardCharsets.UTF_8);
        SkillUseRail rail = new SkillUseRail(root.toString(),
                SkillUseRail.SKILL_MODE_AUTO_LIST, true, false, null, null);
        agent.registerRail(rail).join();
        assertThat(rail.getOwnedToolNames()).containsExactly("skill_tool", "list_skill");
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
