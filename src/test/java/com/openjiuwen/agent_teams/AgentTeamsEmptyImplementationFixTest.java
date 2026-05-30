package com.openjiuwen.agent_teams;

import com.openjiuwen.agent_teams.spawn.InProcessHandle;
import com.openjiuwen.agent_teams.team_workspace.WorkspaceManager;
import com.openjiuwen.agent_teams.team_workspace.WorkspaceRails;
import com.openjiuwen.agent_teams.worktree.WorktreeCleanup;
import com.openjiuwen.agent_teams.worktree.WorktreeChangeSummary;
import com.openjiuwen.agent_teams.worktree.WorktreeConfig;
import com.openjiuwen.agent_teams.worktree.WorktreeManager;
import com.openjiuwen.agent_teams.worktree.WorktreeModels;
import com.openjiuwen.agent_teams.worktree.WorktreeRemote;
import com.openjiuwen.agent_teams.worktree.WorktreeSessionHolder;
import com.openjiuwen.agent_teams.worktree.WorktreeTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for empty-implementation review fixes in agent_teams.
 *
 * <p>Mirrors Python's no-op health hooks, workspace CWD context wiring, and
 * worktree cleanup/remote safety behavior.</p>
 */
class AgentTeamsEmptyImplementationFixTest {

    @AfterEach
    void clearTeamWorkspaceContext() {
        WorkspaceRails.clearTeamWorkspace();
        WorktreeSessionHolder.setCurrentSession(null);
    }

    @Test
    void inProcessHealthHooksRemainPythonAlignedNoOps() {
        InProcessHandle handle = new InProcessHandle("inproc-test");

        handle.startHealthCheck(0.01);
        handle.stopHealthCheck();

        assertFalse(handle.isAlive());
        assertFalse(handle.isHealthy());
    }

    @Test
    void workspaceRailsStoresTeamWorkspaceContextOnInit() {
        WorkspaceManager manager = new WorkspaceManager(
            new WorkspaceManager.TeamWorkspaceConfig(),
            "/tmp/team-workspace",
            "team-alpha",
            WorkspaceManager.WorkspaceMode.LOCAL,
            null,
            "leader",
            "leader",
            null
        );
        WorkspaceRails rails = new WorkspaceRails(manager, "member-a");

        rails.init(new Object());

        assertEquals("/tmp/team-workspace", WorkspaceRails.getTeamWorkspace());
    }

    @Test
    void cleanupEphemeralSlugRulesMatchPythonPatterns() {
        assertTrue(WorktreeCleanup.isEphemeralSlug("teammate-1234abcd"));
        assertTrue(WorktreeCleanup.isEphemeralSlug("agent-123abcd"));
        assertFalse(WorktreeCleanup.isEphemeralSlug("feature-login"));
    }

    @Test
    void remoteWorktreeRemoveMissingPathIsIdempotent() throws Exception {
        Path tempDir = Files.createTempDirectory("remote-worktree-missing");
        Path missing = tempDir.resolve("missing-worktree");
        WorktreeRemote.WorktreeRemoteRequest request = new WorktreeRemote.WorktreeRemoteRequest();
        request.setWorktreePath(missing.toString());

        WorktreeRemote.WorktreeRemoteResponse response = new WorktreeRemote()
            .removeRemoteWorktree(request)
            .get(5, TimeUnit.SECONDS);

        assertTrue(response.isSuccess());
    }

    @Test
    void worktreeToolsShareSessionHolderAndRejectNestedEnter() throws Exception {
        Path workspace = Files.createTempDirectory("worktree-tools-session");
        WorktreeTools tools = new WorktreeTools(new WorktreeManager(
            new WorktreeConfig(),
            null,
            workspace.toString(),
            fakeBackend(null),
            List.of()
        ));

        WorktreeTools.ToolOutput enter = tools.enterWorktree(
            Map.of("name", "user/feature-login"),
            "member-a",
            "team-a"
        ).get(5, TimeUnit.SECONDS);

        assertTrue(enter.isSuccess());
        assertEquals("user/feature-login", WorktreeSessionHolder.getCurrentSession().getSlug());
        assertEquals("worktree-user+feature-login", WorktreeSessionHolder.getCurrentSession().getBranchName());
        assertEquals(
            workspace.resolve(".agent_teams").resolve("worktrees").resolve("user").resolve("feature-login").toString(),
            WorktreeSessionHolder.getCurrentSession().getWorktreePath()
        );

        WorktreeTools.ToolOutput duplicate = tools.enterWorktree(Map.of(), "member-a", "team-a")
            .get(5, TimeUnit.SECONDS);

        assertFalse(duplicate.isSuccess());
        assertTrue(duplicate.getError().contains("Already in worktree"));
    }

    @Test
    void worktreeToolsRemoveCurrentWorktreeAndClearSession() throws Exception {
        Path workspace = Files.createTempDirectory("worktree-tools-remove");
        AtomicReference<String> removedPath = new AtomicReference<>();
        WorktreeTools tools = new WorktreeTools(new WorktreeManager(
            new WorktreeConfig(),
            null,
            workspace.toString(),
            fakeBackend(removedPath),
            List.of()
        ));

        tools.enterWorktree(Map.of("name", "cleanup-target"), "member-a", "team-a")
            .get(5, TimeUnit.SECONDS);
        String expectedPath = WorktreeSessionHolder.getCurrentSession().getWorktreePath();

        WorktreeTools.ToolOutput exit = tools.exitWorktree(
            Map.of("action", "remove", "discard_changes", true)
        ).get(5, TimeUnit.SECONDS);

        assertTrue(exit.isSuccess());
        assertEquals(expectedPath, removedPath.get());
        assertNull(WorktreeSessionHolder.getCurrentSession());
        assertEquals("remove", exit.getData().get("action"));
    }

    private static WorktreeManager.WorktreeBackend fakeBackend(AtomicReference<String> removedPath) {
        return new WorktreeManager.WorktreeBackend() {
            @Override
            public WorktreeModels.WorktreeCreateResult create(String slug, String repoRoot, String targetPath) {
                return WorktreeModels.WorktreeCreateResult.success(
                    targetPath,
                    "worktree-" + slug.replace('/', '+'),
                    "abc123"
                );
            }

            @Override
            public boolean remove(String worktreePath, boolean force) {
                if (removedPath != null) {
                    removedPath.set(worktreePath);
                }
                return true;
            }

            @Override
            public WorktreeChangeSummary detectChanges(String worktreePath) {
                return new WorktreeChangeSummary(false, 0, null);
            }
        };
    }
}
