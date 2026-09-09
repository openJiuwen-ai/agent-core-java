/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamMemoryConfig;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies Agent Teams discovery and use of the optional Memory integration.
 *
 * @since 0.1.7
 */
class TeamAgentMemoryIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void teamAgentShouldLoadMemoryIntegrationFromServiceProvider() throws Exception {
        Path workspace = tempDir.resolve("team-workspace");
        Files.createDirectories(workspace.resolve("memory"));
        Files.writeString(workspace.resolve("memory").resolve("MEMORY.md"),
                "leader remembers escalation policy");
        TeamAgentSpec spec = createSpec(tempDir.resolve("team-memory"));
        TeamRuntimeContext context = TeamRuntimeContext.builder().teamId("memory-team")
                .metadata(new LinkedHashMap<>(Map.of("workspace_path", workspace.toString()))).build();

        TeamAgent agent = new TeamAgent().configure(spec, context);
        Map<String, Object> result = agent.dispatchTask("policy");

        assertThat(result).containsEntry("team_id", "memory-team");
        AgentTeamsMemory memory = assertInstanceOf(AgentTeamsMemory.class, agent.getMemoryManager());
        assertThat(memory.manager().getOwnedToolNames()).contains("memory_search", "memory_get", "read_memory",
                "write_memory", "edit_memory");
        assertThat(agent.getDeepAgent().getAgent().getSystemPromptBuilder().getSection("team_memory").render("en"))
                .contains("leader remembers escalation policy");

        agent.close();
        assertThat(memory.manager().getOwnedToolNames()).isEmpty();
        assertThat(agent.getDeepAgent().getAgent().getSystemPromptBuilder().hasSection("team_memory")).isFalse();
    }

    private static TeamAgentSpec createSpec(Path sharedDir) {
        return TeamAgentSpec.builder().name("memory-team").lifecycle("persistent").language("en")
                .members(List.of(TeamMemberSpec.builder().name("lead").role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("worker").role(TeamRole.MEMBER).build()))
                .memory(TeamMemoryConfig.builder().enabled(true).sharedMemory(true).scenario("general")
                        .teamMemoryDir(sharedDir.toString()).build())
                .build();
    }
}
