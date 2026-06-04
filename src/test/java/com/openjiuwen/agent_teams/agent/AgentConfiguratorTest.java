package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.agent_teams.schema.TeamSpec;
import com.openjiuwen.agent_teams.workspace.TeamWorkspaceConfig;
import com.openjiuwen.agent_teams.workspace.TeamWorkspaceManager;
import com.openjiuwen.agent_teams.worktree.WorktreeConfig;
import com.openjiuwen.agent_teams.worktree.WorktreeManager;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_teams.agent.agent_configurator}.
 */
class AgentConfiguratorTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeAndBuildAgentConfiguresWorkspaceToolsAndAllocator() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("config-team");
        spec.setModelPool(List.of(new ModelPoolEntry("model-a", "http://localhost:8000", "key")));

        LeaderSpec leader = new LeaderSpec();
        leader.setMemberName("leader");
        spec.setLeader(leader);

        TeamWorkspaceConfig workspace = new TeamWorkspaceConfig();
        workspace.setEnabled(true);
        workspace.setRootPath(tempDir.resolve("team-workspace").toString());
        spec.setWorkspace(workspace);

        WorktreeConfig worktree = new WorktreeConfig();
        worktree.setEnabled(true);
        spec.setWorktree(worktree);

        DeepAgentConfig deepConfig = new DeepAgentConfig();
        deepConfig.setSystemPrompt("Base prompt");
        DeepAgentSpec leaderAgent = new DeepAgentSpec();
        leaderAgent.setConfig(deepConfig);
        spec.getAgents().put("leader", leaderAgent);

        AgentCard card = new AgentCard();
        card.setName("leader-card");
        TeamRuntimeContext ctx = leaderContext("config-team", "leader");

        AgentConfigurator configurator = new AgentConfigurator(card);
        configurator.initialize(spec, ctx);

        assertTrue(configurator.isWorkspaceInitialized());
        assertInstanceOf(TeamWorkspaceManager.class, configurator.getWorkspaceManager());
        assertInstanceOf(WorktreeManager.class, configurator.getWorktreeManager());

        Object agent = configurator.buildAgent();

        assertInstanceOf(DeepAgent.class, agent);
        assertFalse(configurator.getRegisteredTools().isEmpty());
        assertInstanceOf(RoundRobinModelAllocator.class, configurator.getModelAllocator());
        assertNotNull(configurator.getTeamBackend());
    }

    private static TeamRuntimeContext leaderContext(String teamName, String memberName) {
        TeamSpec teamSpec = new TeamSpec();
        teamSpec.setTeamName(teamName);
        teamSpec.setLeaderMemberName(memberName);
        teamSpec.setLanguage("en");

        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.LEADER);
        ctx.setMemberName(memberName);
        ctx.setTeamSpec(teamSpec);
        return ctx;
    }
}
