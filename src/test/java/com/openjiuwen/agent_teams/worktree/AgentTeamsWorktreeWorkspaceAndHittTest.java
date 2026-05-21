package com.openjiuwen.agent_teams.worktree;

import com.openjiuwen.agent_teams.messager.InProcessMessager;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.workspace.ConflictStrategy;
import com.openjiuwen.agent_teams.workspace.TeamWorkspaceConfig;
import com.openjiuwen.agent_teams.workspace.TeamWorkspaceManager;
import com.openjiuwen.agent_teams.workspace.WorkspaceFileLock;
import com.openjiuwen.agent_teams.workspace.WorkspaceMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.test_hitt}
 * and {@code tests.unit_tests.agent_teams.worktree.test_manager}.
 * Tests for worktree config defaults, session management, and workspace file operations.
 */
class AgentTeamsWorktreeWorkspaceAndHittTest {

    @AfterEach
    void cleanup() {
        InProcessMessager.cleanupBus();
        WorktreeSessionHolder.setCurrentSession(null);
    }

    @Test
    void worktreeConfigDefaultsMatchPythonIntent() {
        WorktreeConfig config = new WorktreeConfig();
        assertFalse(config.isEnabled());
        assertEquals(30, config.getCleanupAfterDays());
        assertTrue(config.isAutoCleanupOnShutdown());
        assertEquals(WorktreeLifecyclePolicy.AUTO, config.getLifecyclePolicy());
    }

    @Test
    void worktreeManagerEnterSetsAndRemoveClearsCurrentSession() {
        MessagerTransportConfig transportConfig = new MessagerTransportConfig();
        transportConfig.setNodeId("leader");
        InProcessMessager messager = new InProcessMessager(transportConfig);
        WorktreeConfig config = new WorktreeConfig();
        config.setEnabled(true);
        config.setBaseDir("build/test-worktrees");

        WorktreeManager manager = new WorktreeManager(config, messager, "workspace-root");
        WorktreeSession session = manager.enter("slug-a", "member-a", "team-a");

        assertEquals(session, WorktreeSessionHolder.getCurrentSession());
        assertEquals("slug-a", session.getSlug());
        assertTrue(manager.removeCurrent(true));
        assertEquals(null, WorktreeSessionHolder.getCurrentSession());
    }

    @Test
    void requireCurrentSessionUsesPythonAlignedErrorMessage() {
        WorktreeSessionHolder.setCurrentSession(null);

        IllegalStateException error = assertThrows(IllegalStateException.class, WorktreeSessionHolder::requireCurrentSession);

        assertEquals("Not in a worktree session", error.getMessage());
    }

    @Test
    void workspaceModelsAndManagerProvideMinimalLockingAndMountSemantics() throws Exception {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        assertFalse(config.isEnabled());
        assertEquals(ConflictStrategy.LOCK, config.getConflictStrategy());
        assertEquals(List.of("artifacts/code", "artifacts/docs", "artifacts/reports", "trajectories"), config.getArtifactDirs());

        Path shared = Files.createTempDirectory("shared-workspace");
        Path agentRoot = Files.createTempDirectory("agent-workspace");
        TeamWorkspaceManager manager = new TeamWorkspaceManager(config, shared.toString(), "team-alpha", WorkspaceMode.LOCAL);

        manager.mountIntoWorkspace(agentRoot.toString());
        assertTrue(Files.exists(agentRoot.resolve(".team").resolve("team-alpha")));

        WorkspaceFileLock lock = new WorkspaceFileLock("src/main.py", "m1", "Alice", Instant.now().toString());
        assertTrue(manager.acquireLock(lock));
        assertNotNull(manager.getLock("src/main.py"));
        assertFalse(manager.acquireLock(new WorkspaceFileLock("src/main.py", "m2", "Bob", Instant.now().toString())));
        assertTrue(manager.releaseLock("src/main.py", "m1"));
    }

    @Test
    void enableHittInjectsHumanAgentAndReservedNamesAreRejected() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setEnableHitt(true);
        spec.injectHumanAgentIfEnabled();
        assertTrue(spec.getPredefinedMembers().stream().anyMatch(member -> "human_agent".equals(member.getMemberName()) && member.getRoleType() == TeamRole.HUMAN_AGENT));

        TeamAgentSpec reservedLeader = new TeamAgentSpec();
        LeaderSpec leaderSpec = new LeaderSpec();
        leaderSpec.setMemberName("human_agent");
        reservedLeader.setLeader(leaderSpec);
        assertThrows(IllegalArgumentException.class, reservedLeader::build);
    }
}
