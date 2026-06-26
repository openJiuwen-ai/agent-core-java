/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.worktree.Git;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused parity tests for {@link WorkspaceMetaTool}.
 *
 * <p>Mirrors Python's {@code WorkspaceMetaTool} in
 * {@code openjiuwen/agent_teams/team_workspace/tools.py}.</p>
 */
class WorkspaceMetaToolTest {

    @TempDir
    Path tempDir;

    @Test
    void cardCarriesWorkspaceMetaSchema() {
        WorkspaceMetaTool tool = new WorkspaceMetaTool(manager(false), "en");

        WorkspaceMetaTool.WorkspaceMetaToolCard card = tool.getCard();

        assertThat(card.getId()).isEqualTo("team.workspace_meta");
        assertThat(card.getName()).isEqualTo("workspace_meta");
        assertThat(card.getDescription()).contains("Metadata tool");
        assertThat(card.getInputParams()).containsEntry("type", "object");
        assertThat(card.getInputParams()).containsEntry("required", List.of("action"));
        Map<?, ?> properties = (Map<?, ?>) card.getInputParams().get("properties");
        Map<?, ?> action = (Map<?, ?>) properties.get("action");
        assertThat(action.get("enum")).isEqualTo(List.of("lock", "unlock", "locks", "history"));
    }

    @Test
    void lockRequiresPathAndReportsCurrentHolder() {
        WorkspaceMetaTool tool = new WorkspaceMetaTool(manager(false), "en");

        ToolOutput missingPath = tool.invoke(Map.of("action", "lock")).toCompletableFuture().join();
        ToolOutput acquired = tool.invoke(
                Map.of("action", "lock", "path", "artifacts/report.md"),
                Map.of("member_name", "alice", "display_name", "Alice")
        ).toCompletableFuture().join();
        ToolOutput rejected = tool.invoke(
                Map.of("action", "lock", "path", "artifacts/report.md"),
                Map.of("member_name", "bob", "display_name", "Bob")
        ).toCompletableFuture().join();

        assertThat(missingPath.isSuccess()).isFalse();
        assertThat(missingPath.getError()).isEqualTo("'path' is required for lock action");
        assertThat(acquired.isSuccess()).isTrue();
        assertThat(((Map<?, ?>) acquired.getData()).get("locked")).isEqualTo("artifacts/report.md");
        assertThat(rejected.isSuccess()).isFalse();
        assertThat(rejected.getError()).isEqualTo("Locked by Alice");
    }

    @Test
    void unlockListsLocksAndHandlesUnknownAction() {
        WorkspaceMetaTool tool = new WorkspaceMetaTool(manager(false), "en");

        tool.invoke(
                Map.of("action", "lock", "path", "artifacts/report.md"),
                Map.of("member_name", "alice", "display_name", "Alice")
        ).toCompletableFuture().join();
        ToolOutput locks = tool.invoke(Map.of("action", "locks")).toCompletableFuture().join();
        ToolOutput released = tool.invoke(
                Map.of("action", "unlock", "path", "artifacts/report.md"),
                Map.of("member_name", "alice")
        ).toCompletableFuture().join();
        ToolOutput repeatedRelease = tool.invoke(
                Map.of("action", "unlock", "path", "artifacts/report.md"),
                Map.of("member_name", "alice")
        ).toCompletableFuture().join();
        ToolOutput unknown = tool.invoke(Map.of("action", "dance")).toCompletableFuture().join();
        ToolOutput missingAction = tool.invoke(Map.of()).toCompletableFuture().join();

        assertThat(locks.isSuccess()).isTrue();
        List<?> lockItems = (List<?>) ((Map<?, ?>) locks.getData()).get("locks");
        Map<?, ?> lockItem = (Map<?, ?>) lockItems.getFirst();
        assertThat(lockItem.get("file_path")).isEqualTo("artifacts/report.md");
        assertThat(lockItem.get("holder_id")).isEqualTo("alice");
        assertThat(lockItem.get("holder_name")).isEqualTo("Alice");
        assertThat(((Map<?, ?>) released.getData()).get("released")).isEqualTo(true);
        assertThat(((Map<?, ?>) repeatedRelease.getData()).get("released")).isEqualTo(false);
        assertThat(unknown.isSuccess()).isFalse();
        assertThat(unknown.getError()).isEqualTo("Unknown action 'dance'");
        assertThat(missingAction.getError()).isEqualTo("Unknown action 'None'");
    }

    @Test
    void historyRequiresPathAndDelegatesToWorkspaceManager() {
        RecordingManager manager = manager(true);
        WorkspaceMetaTool tool = new WorkspaceMetaTool(manager, "en");

        ToolOutput missingPath = tool.invoke(Map.of("action", "history")).toCompletableFuture().join();
        ToolOutput history = tool.invoke(Map.of("action", "history", "path", "artifacts/report.md"))
                .toCompletableFuture().join();

        assertThat(missingPath.isSuccess()).isFalse();
        assertThat(missingPath.getError()).isEqualTo("'path' is required for history action");
        assertThat(manager.historyPath).isEqualTo("artifacts/report.md");
        assertThat(((Map<?, ?>) history.getData()).get("history")).isEqualTo(manager.history);
    }

    private RecordingManager manager(boolean versionControl) {
        try {
            TeamWorkspaceConfig config = new TeamWorkspaceConfig();
            config.setVersionControl(versionControl);
            Path workspacePath = tempDir.resolve("workspace");
            Files.createDirectories(workspacePath);
            return new RecordingManager(config, workspacePath.toString(), "team-alpha");
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Test double for workspace git history while using real lock behavior.
     *
     * <p>Mirrors Python's {@code TeamWorkspaceManager} collaborator in
     * {@code openjiuwen/agent_teams/team_workspace/tools.py}.</p>
     */
    private static final class RecordingManager extends TeamWorkspaceManager {
        private final List<Map<String, String>> history = List.of(Map.of(
                "commit", "abc123",
                "author", "Alice",
                "date", "2026-06-11T00:00:00+00:00",
                "message", "Update report"
        ));
        private String historyPath;

        private RecordingManager(TeamWorkspaceConfig config, String workspacePath, String teamName) {
            super(config, workspacePath, teamName);
        }

        @Override
        public CompletableFuture<List<Map<String, String>>> getHistory(String relativePath) {
            historyPath = relativePath;
            return CompletableFuture.completedFuture(history);
        }

        @Override
        protected CompletableFuture<Git.GitResult> runGit(List<String> args, String cwd, boolean check) {
            return CompletableFuture.completedFuture(new Git.GitResult(0, "", ""));
        }
    }
}
