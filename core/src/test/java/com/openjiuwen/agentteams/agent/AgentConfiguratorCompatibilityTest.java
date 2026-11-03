
package com.openjiuwen.agentteams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.agentteams.teamworkspace.TeamWorkspaceConfig;
import com.openjiuwen.agentteams.teamworkspace.TeamWorkspaceManager;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

@Tag("agent-teams-config-slice")
class AgentConfiguratorCompatibilityTest {
    @Test
    void shouldCreateConfiguratorWithAgentCard() {
        AgentCard card = AgentCard.builder().id("test.card").name("test_agent").description("Test agent").build();
        AgentConfigurator configurator = new AgentConfigurator(card);

        assertThat(configurator.getSpec()).isNull();
        assertThat(configurator.getCtx()).isNull();
    }

    @Test
    void shouldSetupInfraWithBasicSpec() {
        AgentCard card = AgentCard.builder().id("test.card").name("leader").description("Test leader").build();
        TeamAgentSpec spec = TeamAgentSpec.builder().name("test_team")
                .members(List.of(
                        TeamMemberSpec.builder().name("leader").role(TeamRole.LEADER).description("Leader").build()))
                .build();
        TeamRuntimeContext ctx =
            TeamRuntimeContext.builder().teamId("test_team").memberName("leader").role(TeamRole.LEADER).build();

        AgentConfigurator configurator = new AgentConfigurator(card);
        configurator.setupInfra(spec, ctx);

        assertThat(configurator.getSpec()).isNotNull();
        assertThat(configurator.getCtx()).isNotNull();
        assertThat(configurator.getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(configurator.getMemberName()).isEqualTo("leader");
    }

    @Test
    void shouldBuildModelAllocatorWhenSetupInfraCompletes() {
        AgentCard card = AgentCard.builder().id("test.card").name("leader").build();
        TeamAgentSpec spec = TeamAgentSpec.builder().name("test_team")
                .members(List.of(TeamMemberSpec.builder().name("leader").role(TeamRole.LEADER).build())).build();
        TeamRuntimeContext ctx =
            TeamRuntimeContext.builder().teamId("test_team").memberName("leader").role(TeamRole.LEADER).build();

        AgentConfigurator configurator = new AgentConfigurator(card);
        configurator.setupInfra(spec, ctx);

        // setupInfra completes successfully
        assertThat(configurator.getSpec()).isNotNull();
        assertThat(configurator.getTeamBackend()).isNotNull();
    }

    @Test
    void shouldSupportManualModelAllocatorAttachment() {
        AgentCard card = AgentCard.builder().id("test.card").name("leader").build();

        AgentConfigurator configurator = new AgentConfigurator(card);
        // Before setupInfra, allocator is null
        assertThat(configurator.getModelAllocator()).isNull();

        // setModelAllocator directly
        configurator.setModelAllocator(null);
        assertThat(configurator.getModelAllocator()).isNull();
    }

    @Test
    void shouldBuildSpawnPayload() {
        AgentCard card = AgentCard.builder().id("test.card").name("leader").build();
        TeamAgentSpec spec = TeamAgentSpec.builder().name("test_team").members(List.of(
                TeamMemberSpec.builder().name("leader").role(TeamRole.LEADER).build(),
                TeamMemberSpec.builder().name("worker").role(TeamRole.MEMBER).description("Worker desc").build()))
                .build();
        TeamRuntimeContext ctx =
            TeamRuntimeContext.builder().teamId("test_team").memberName("leader").role(TeamRole.LEADER).build();

        AgentConfigurator configurator = new AgentConfigurator(card);
        configurator.setupInfra(spec, ctx);

        var payload = configurator.buildSpawnPayload(ctx, "Hello worker");
        assertThat(payload).containsKeys("coordination", "query");
        assertThat(payload.get("query")).isEqualTo("Hello worker");
    }

    @Test
    void shouldBuildSpawnPayloadWithDefaultMessage() {
        AgentCard card = AgentCard.builder().id("test.card").name("leader").build();
        TeamAgentSpec spec = TeamAgentSpec.builder().name("test_team")
                .members(List.of(TeamMemberSpec.builder().name("leader").role(TeamRole.LEADER).build())).build();
        TeamRuntimeContext ctx =
            TeamRuntimeContext.builder().teamId("test_team").memberName("leader").role(TeamRole.LEADER).build();

        AgentConfigurator configurator = new AgentConfigurator(card);
        configurator.setupInfra(spec, ctx);

        var payload = configurator.buildSpawnPayload(ctx, null);
        assertThat(payload).containsKeys("coordination", "query");
        assertThat(payload.get("query").toString()).contains("Join the team");
    }

    @Test
    void shouldBuildMemberContext() {
        AgentCard card = AgentCard.builder().id("test.card").name("leader").build();
        TeamAgentSpec spec = TeamAgentSpec.builder().name("test_team")
                .members(List.of(TeamMemberSpec.builder().name("leader").role(TeamRole.LEADER).build())).build();
        TeamRuntimeContext ctx =
            TeamRuntimeContext.builder().teamId("test_team").memberName("leader").role(TeamRole.LEADER).build();

        AgentConfigurator configurator = new AgentConfigurator(card);
        configurator.setupInfra(spec, ctx);

        TeamMemberSpec memberSpec = TeamMemberSpec.builder().name("worker").role(TeamRole.MEMBER).build();

        TeamRuntimeContext memberCtx = configurator.buildMemberContext(memberSpec);
        assertThat(memberCtx.getMemberName()).isEqualTo("worker");
        assertThat(memberCtx.getRole()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void shouldCreateWorkspaceManager() {
        TeamWorkspaceConfig wsConfig = TeamWorkspaceConfig.builder().isEnabled(true).rootPath(null).build();

        TeamWorkspaceManager mgr = AgentConfigurator.createWorkspaceManager(wsConfig, "test_team");
        assertThat(mgr).isNotNull();
        assertThat(mgr.getWorkspacePath()).isNotNull();
    }

    @Test
    void shouldGetDefaultRoleAsLeader() {
        AgentCard card = AgentCard.builder().id("test.card").name("agent").build();
        AgentConfigurator configurator = new AgentConfigurator(card);
        assertThat(configurator.getRole()).isEqualTo(TeamRole.LEADER);
    }

    @Test
    void shouldGetNullMemberNameWhenNoContext() {
        AgentCard card = AgentCard.builder().id("test.card").name("agent").build();
        AgentConfigurator configurator = new AgentConfigurator(card);
        assertThat(configurator.getMemberName()).isNull();
    }
}
