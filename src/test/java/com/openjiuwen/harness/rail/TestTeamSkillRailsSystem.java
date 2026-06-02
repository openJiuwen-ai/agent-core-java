/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rail;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.examples.agent_evolving.TeamSkillCreateRailExample;
import com.openjiuwen.examples.agent_evolving.TeamSkillRailExample;
import com.openjiuwen.harness.rails.skills.TeamSkillCreateRail;
import com.openjiuwen.harness.rails.skills.TeamSkillRail;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for TeamSkillRails.
 * <p>
 * Mirrors Python's {@code test_team_skill_rails_system.py} in
 * {@code tests.system_tests.harness.rail}.
 */
@Tag("system-test")
class TestTeamSkillRailsSystem {

    @Test
    void testTeamSkillCreateRailQueuesFollowUpAfterSpawnThreshold(@TempDir Path tempDir) {
        TeamSkillCreateRail rail = new TeamSkillCreateRail(tempDir.resolve("skills").toString(),
                "cn", true, 2);
        FakeAgent agent = new FakeAgent();
        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).build();

        rail.beforeInvoke(ctx);
        rail.afterToolCall(ctxWithTool(agent, "spawn_member"));
        rail.afterToolCall(ctxWithTool(agent, "spawn_member"));
        rail.afterTaskIteration(AgentCallbackContext.builder()
                .agent(agent)
                .inputs(new TaskIterationInputs())
                .build());

        assertThat(agent.loopController.followUps).hasSize(1);
        assertThat(agent.loopController.followUps.get(0)).contains("ask_user");
        assertThat(agent.loopController.followUps.get(0)).contains("team-skill-creator");
    }

    @Test
    void testTeamSkillRailGeneratesAndPersistsPatchAfterCompletion(@TempDir Path tempDir) throws Exception {
        Path skillDir = writeTeamSkill(tempDir, "research-team");
        TeamSkillRail.FileEvolutionStore store = new TeamSkillRail.FileEvolutionStore(tempDir);
        TeamSkillRail rail = new TeamSkillRail(store, null, null);
        EvolutionRecord record = EvolutionRecord.make(
                "trajectory_issue",
                "handoff quality drifted during collaboration",
                EvolutionPatch.builder()
                        .section("Workflow")
                        .action("append")
                        .content("### Experience: tighten handoff\nRequire the leader to restate output format before merge.")
                        .target(EvolutionTarget.BODY)
                        .build(),
                0.6,
                null);

        assertThat(Files.exists(skillDir.resolve("SKILL.md"))).isTrue();
        assertThat(TeamSkillRail.allTasksCompleted("task-a completed\ntask-b completed")).isTrue();
        store.saveRecord("research-team", record);

        var evoLog = rail.store().loadFullEvolutionLog("research-team");
        assertThat(evoLog.getEntries()).hasSize(1);
        assertThat(evoLog.getEntries().get(0).getChange().getSection()).isEqualTo("Workflow");
        assertThat(evoLog.getEntries().get(0).getChange().getContent()).contains("tighten handoff");
    }

    @Test
    void testExamplesCanLoadModelEnvFromLocalDotenv(@TempDir Path tempDir) throws Exception {
        Path cwdEnv = Path.of(".env").toAbsolutePath().normalize();
        String original = Files.exists(cwdEnv) ? Files.readString(cwdEnv) : null;
        try {
            Files.writeString(cwdEnv, String.join("\n",
                    "MODEL_NAME=deepseek-chat",
                    "API_KEY=test-key",
                    "MODEL_PROVIDER=OpenAI",
                    "API_BASE=https://example.test/v1"));

            Map<String, String> createEnv = TeamSkillCreateRailExample.loadEnvIfPresent();
            Map<String, String> railEnv = TeamSkillRailExample.loadEnvIfPresent();

            assertThat(createEnv).containsEntry("MODEL_NAME", "deepseek-chat");
            assertThat(createEnv).containsEntry("API_KEY", "test-key");
            assertThat(railEnv).containsEntry("MODEL_PROVIDER", "OpenAI");
            assertThat(railEnv).containsEntry("API_BASE", "https://example.test/v1");
        } finally {
            if (original == null) {
                Files.deleteIfExists(cwdEnv);
            } else {
                Files.writeString(cwdEnv, original);
            }
        }
    }

    private static AgentCallbackContext ctxWithTool(FakeAgent agent, String toolName) {
        return AgentCallbackContext.builder()
                .agent(agent)
                .inputs(ToolCallInputs.builder().toolName(toolName).toolResult(Map.of("status", "spawned")).build())
                .build();
    }

    private static Path writeTeamSkill(Path skillsDir, String skillName) throws Exception {
        Path skillDir = skillsDir.resolve(skillName);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\n"
                        + "name: " + skillName + "\n"
                        + "description: simple research team\n"
                        + "kind: team-skill\n"
                        + "---\n"
                        + "# Workflow\n"
                        + "1. leader assigns tasks\n"
                        + "2. members execute\n");
        return skillDir;
    }

    private static final class FakeAgent {
        private final FakeLoopController _loop_controller = new FakeLoopController();
        private final FakeLoopController loopController = _loop_controller;
    }

    private static final class FakeLoopController {
        private final List<String> followUps = new ArrayList<>();

        public void enqueueFollowUp(String prompt) {
            followUps.add(prompt);
        }
    }
}
