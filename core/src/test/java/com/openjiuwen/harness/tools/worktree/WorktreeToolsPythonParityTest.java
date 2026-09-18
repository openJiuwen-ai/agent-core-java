/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import com.openjiuwen.core.sysop.Cwd;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for worktree lifecycle tools.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/harness/tools/worktree/test_tools.py}.</p>
 */
class WorktreeToolsPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/harness/tools/worktree/test_tools.py";

    @TempDir
    Path tempDir;

    @TestFactory
    Collection<DynamicTest> pythonWorktreeToolCases() {
        return List.of(
                caseOf("test_generate_random_slug_format", this::generateRandomSlugFormat),
                caseOf("test_resolve_owner_prefers_generic_keys", this::resolveOwnerPrefersGenericKeys),
                caseOf("test_resolve_owner_falls_back_to_legacy_keys", this::resolveOwnerFallsBackToLegacyKeys),
                caseOf("test_resolve_owner_returns_none_when_missing", this::resolveOwnerReturnsNoneWhenMissing),
                caseOf("test_enter_rejects_invalid_slug", this::enterRejectsInvalidSlug),
                caseOf("test_enter_refuses_when_already_in_session", this::enterRefusesWhenAlreadyInSession),
                caseOf("test_exit_without_session_returns_error", this::exitWithoutSessionReturnsError),
                caseOf("test_exit_validates_action_value", this::exitValidatesActionValue),
                caseOf("test_exit_remove_translates_validation_error_to_tool_output",
                        this::exitRemoveTranslatesValidationErrorToToolOutput),
                caseOf("test_event_handler_receives_generic_events", this::eventHandlerReceivesGenericEvents),
                caseOf("test_legacy_team_kwargs_propagate_to_session",
                        this::legacyTeamKwargsPropagateToSession),
                caseOf("test_enter_existing_worktree_by_name", this::enterExistingWorktreeByName),
                caseOf("test_unnamed_enter_reuses_session_default_after_keep",
                        this::unnamedEnterReusesSessionDefaultAfterKeep),
                caseOf("test_explicit_name_does_not_replace_session_default",
                        this::explicitNameDoesNotReplaceSessionDefault)
        );
    }

    private DynamicTest caseOf(String pythonNode, ThrowingRunnable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, () -> {
            resetState();
            try {
                executable.run();
            } finally {
                resetState();
            }
        });
    }

    private void generateRandomSlugFormat() {
        String slug = EnterWorktreeTool.generateRandomSlug();

        String[] parts = slug.split("-");
        assertThat(parts).hasSize(3);
        assertThat(parts).allSatisfy(part -> assertThat(part).isNotBlank());
        assertThat(parts[2]).hasSize(4).matches("[0-9a-f]{4}");
        Integer.parseInt(parts[2], 16);
    }

    private void resolveOwnerPrefersGenericKeys() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        RecordingManager manager = recordingManager(backend);
        EnterWorktreeTool tool = new EnterWorktreeTool(manager);

        ToolOutput output = (ToolOutput) tool.invoke(
                Map.of("name", "owner-wt"),
                Map.of("owner_id", "alice", "member_name", "legacy", "tag", "team-a", "team_name", "old-team")
        );

        assertThat(output.isSuccess()).isTrue();
        assertThat(manager.lastMemberName).isEqualTo("alice");
        assertThat(manager.lastTeamName).isEqualTo("team-a");
    }

    private void resolveOwnerFallsBackToLegacyKeys() throws Exception {
        RecordingManager manager = recordingManager(new RecordingBackend());
        EnterWorktreeTool tool = new EnterWorktreeTool(manager);

        ToolOutput output = (ToolOutput) tool.invoke(
                Map.of("name", "legacy-wt"),
                Map.of("member_name", "bob", "team_name", "team-b")
        );

        assertThat(output.isSuccess()).isTrue();
        assertThat(manager.lastMemberName).isEqualTo("bob");
        assertThat(manager.lastTeamName).isEqualTo("team-b");
    }

    private void resolveOwnerReturnsNoneWhenMissing() throws Exception {
        RecordingManager manager = recordingManager(new RecordingBackend());
        EnterWorktreeTool tool = new EnterWorktreeTool(manager);

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("name", "missing-owner"), Map.of());

        assertThat(output.isSuccess()).isTrue();
        assertThat(manager.lastMemberName).isNull();
        assertThat(manager.lastTeamName).isNull();
    }

    private void enterRejectsInvalidSlug() throws Exception {
        EnterWorktreeTool tool = new EnterWorktreeTool(recordingManager(new RecordingBackend()));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("name", "../escape"), Map.of());

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).contains("Invalid worktree name");
    }

    private void enterRefusesWhenAlreadyInSession() throws Exception {
        WorktreeSessionContext.setCurrentSession(new WorktreeSession("/tmp", "/tmp/wt", "existing"));
        EnterWorktreeTool tool = new EnterWorktreeTool(recordingManager(new RecordingBackend()));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("name", "another"), Map.of());

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).contains("Already in worktree", "existing");
    }

    private void exitWithoutSessionReturnsError() throws Exception {
        ExitWorktreeTool tool = new ExitWorktreeTool(recordingManager(new RecordingBackend()));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("action", "keep"), Map.of());

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).contains("No active worktree session");
    }

    private void exitValidatesActionValue() throws Exception {
        WorktreeSessionContext.setCurrentSession(session("wt", false));
        ExitWorktreeTool tool = new ExitWorktreeTool(recordingManager(new RecordingBackend()));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("action", "bogus"), Map.of());

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).contains("'action' must be 'keep' or 'remove'");
    }

    private void exitRemoveTranslatesValidationErrorToToolOutput() throws Exception {
        WorktreeSessionContext.setCurrentSession(session("wt", false));
        RecordingManager manager = recordingManager(new RecordingBackend());
        manager.exitFailureMessage = "Worktree has 2 uncommitted files. Set discard_changes=True to proceed.";
        ExitWorktreeTool tool = new ExitWorktreeTool(manager);

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("action", "remove"), Map.of());

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).contains("Worktree has 2 uncommitted files", "discard_changes=True");
    }

    private void eventHandlerReceivesGenericEvents() throws Exception {
        Path repo = initGitRepo("events-repo");
        Path workspace = Files.createDirectories(tempDir.resolve("events-workspace"));
        Cwd.initCwd(repo.toString(), repo.toString(), workspace.toString(), null);

        RecordingBackend backend = new RecordingBackend();
        RecordingHandler handler = new RecordingHandler();
        WorktreeManager manager = new WorktreeManager(new WorktreeConfig(), backend, handler, null);
        EnterWorktreeTool enterTool = new EnterWorktreeTool(manager);
        ExitWorktreeTool exitTool = new ExitWorktreeTool(manager);

        ToolOutput enter = (ToolOutput) enterTool.invoke(
                Map.of("name", "wt-happy"),
                Map.of("owner_id", "alice", "tag", "team-a")
        );
        Map<String, Object> enterData = data(enter);
        ToolOutput exit = (ToolOutput) exitTool.invoke(Map.of("action", "remove", "discard_changes", true), Map.of());

        assertThat(enter.isSuccess()).isTrue();
        assertThat(Files.isDirectory(Path.of(String.valueOf(enterData.get("worktree_path"))))).isTrue();
        assertThat(Cwd.getCwd()).isEqualTo(data(exit).get("original_cwd"));
        assertThat(exit.isSuccess()).isTrue();
        assertThat(handler.events).hasSize(2);
        assertThat(handler.events.get(0)).isInstanceOf(WorktreeCreatedEvent.class);
        assertThat(handler.events.get(1)).isInstanceOf(WorktreeRemovedEvent.class);
        WorktreeCreatedEvent created = (WorktreeCreatedEvent) handler.events.get(0);
        WorktreeRemovedEvent removed = (WorktreeRemovedEvent) handler.events.get(1);
        assertThat(created.getWorktreeName()).isEqualTo("wt-happy");
        assertThat(created.getOwnerId()).isEqualTo("alice");
        assertThat(created.getTag()).isEqualTo("team-a");
        assertThat(removed.getWorktreeName()).isEqualTo("wt-happy");
        assertThat(removed.getOwnerId()).isEqualTo("alice");
        assertThat(removed.getTag()).isEqualTo("team-a");
    }

    private void legacyTeamKwargsPropagateToSession() throws Exception {
        RecordingManager manager = recordingManager(new RecordingBackend());
        EnterWorktreeTool tool = new EnterWorktreeTool(manager);

        ToolOutput output = (ToolOutput) tool.invoke(
                Map.of("name", "legacy-wt"),
                Map.of("member_name", "member-1", "team_name", "team-x")
        );

        assertThat(output.isSuccess()).isTrue();
        assertThat(manager.calls).containsExactly("legacy-wt/member-1/team-x");
        assertThat(WorktreeSessionContext.getCurrentSession().getMemberName()).isEqualTo("member-1");
        assertThat(WorktreeSessionContext.getCurrentSession().getTeamName()).isEqualTo("team-x");
    }

    private void enterExistingWorktreeByName() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        initCwdForCase("existing-workspace");
        String target = WorktreeManager.resolveTargetPath("bold-elm-1732");
        backend.markExisting(target);
        RecordingManager manager = new RecordingManager(backend);
        EnterWorktreeTool tool = new EnterWorktreeTool(manager);

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("name", "bold-elm-1732"), Map.of());
        Map<String, Object> outputData = data(output);

        assertThat(output.isSuccess()).isTrue();
        assertThat(outputData).containsEntry("existed", true);
        assertThat(Path.of(String.valueOf(outputData.get("worktree_path"))).normalize())
                .isEqualTo(Path.of(target).normalize());
        assertThat(outputData.get("worktree_branch")).isEqualTo("worktree-bold-elm-1732");
        assertThat(Cwd.getCwd()).isEqualTo(target);
    }

    private void unnamedEnterReusesSessionDefaultAfterKeep() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        RecordingManager manager = recordingManager(backend);
        EnterWorktreeTool enterTool = new EnterWorktreeTool(manager);
        ExitWorktreeTool exitTool = new ExitWorktreeTool(manager);

        ToolOutput first = (ToolOutput) enterTool.invoke(Map.of(), Map.of());
        String defaultName = WorktreeSessionContext.getDefaultWorktreeName();
        String firstPath = String.valueOf(data(first).get("worktree_path"));
        ToolOutput kept = (ToolOutput) exitTool.invoke(Map.of("action", "keep"), Map.of());
        ToolOutput second = (ToolOutput) enterTool.invoke(Map.of(), Map.of());

        assertThat(first.isSuccess()).isTrue();
        assertThat(defaultName).isNotBlank();
        assertThat(kept.isSuccess()).isTrue();
        assertThat(WorktreeSessionContext.getDefaultWorktreeName()).isEqualTo(defaultName);
        assertThat(data(second)).containsEntry("existed", true);
        assertThat(data(second).get("worktree_path")).isEqualTo(firstPath);
    }

    private void explicitNameDoesNotReplaceSessionDefault() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        RecordingManager manager = recordingManager(backend);
        EnterWorktreeTool enterTool = new EnterWorktreeTool(manager);
        ExitWorktreeTool exitTool = new ExitWorktreeTool(manager);

        ToolOutput defaultResult = (ToolOutput) enterTool.invoke(Map.of(), Map.of());
        String defaultName = WorktreeSessionContext.getDefaultWorktreeName();
        String defaultPath = String.valueOf(data(defaultResult).get("worktree_path"));
        exitTool.invoke(Map.of("action", "keep"), Map.of());

        ToolOutput namedResult = (ToolOutput) enterTool.invoke(Map.of("name", "named-wt"), Map.of());
        exitTool.invoke(Map.of("action", "keep"), Map.of());
        ToolOutput reusedDefault = (ToolOutput) enterTool.invoke(Map.of(), Map.of());

        assertThat(namedResult.isSuccess()).isTrue();
        assertThat(WorktreeSessionContext.getDefaultWorktreeName()).isEqualTo(defaultName);
        assertThat(Path.of(String.valueOf(data(namedResult).get("worktree_path"))).getFileName().toString())
                .isEqualTo("named-wt");
        assertThat(data(reusedDefault)).containsEntry("existed", true);
        assertThat(data(reusedDefault).get("worktree_path")).isEqualTo(defaultPath);
    }

    private RecordingManager recordingManager(RecordingBackend backend) throws IOException {
        initCwdForCase("case-" + System.nanoTime());
        return new RecordingManager(backend);
    }

    private void initCwdForCase(String name) throws IOException {
        Path root = Files.createDirectories(tempDir.resolve(name));
        Cwd.initCwd(root.toString(), root.toString(), root.toString(), null);
    }

    private Path initGitRepo(String name) throws IOException, InterruptedException {
        Path repo = Files.createDirectories(tempDir.resolve(name));
        runGit(repo, "init", "--quiet");
        runGit(repo, "symbolic-ref", "HEAD", "refs/heads/main");
        runGit(repo, "config", "user.email", "test@example.com");
        runGit(repo, "config", "user.name", "Test User");
        Files.writeString(repo.resolve("README.md"), "hello\n");
        runGit(repo, "add", "README.md");
        runGit(repo, "commit", "--quiet", "-m", "init");
        runGit(repo, "update-ref", "refs/remotes/origin/main", "HEAD");
        return repo;
    }

    private static void runGit(Path cwd, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(new String(process.getErrorStream().readAllBytes()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ToolOutput output) {
        assertThat(output.isSuccess()).isTrue();
        return (Map<String, Object>) output.getData();
    }

    private static WorktreeSession session(String slug, boolean existed) {
        WorktreeSession session = new WorktreeSession();
        session.setOriginalCwd("/tmp");
        session.setWorktreePath("/tmp/.worktrees/" + slug);
        session.setWorktreeName(slug);
        session.setWorktreeBranch("worktree-" + slug);
        session.setOriginalHeadCommit("abc123");
        session.setExisted(existed);
        return session;
    }

    private static void resetState() {
        WorktreeSessionContext.setCurrentSession(null);
        WorktreeSessionContext.setDefaultWorktreeName(null);
        Cwd.clear();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class RecordingBackend implements WorktreeBackend {
        private final Set<String> existingPaths = new HashSet<>();
        private boolean removeCalled;

        @Override
        public CompletableFuture<WorktreeCreateResult> create(String slug, String repoRoot, String targetPath) {
            boolean existed = existingPaths.contains(targetPath);
            existingPaths.add(targetPath);
            try {
                Files.createDirectories(Path.of(targetPath));
            } catch (IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }
            return CompletableFuture.completedFuture(new WorktreeCreateResult(
                    targetPath,
                    SlugUtils.worktreeBranchName(slug),
                    "abc123",
                    null,
                    existed,
                    false
            ));
        }

        @Override
        public CompletableFuture<Boolean> remove(String worktreePath, String repoRoot) {
            removeCalled = true;
            existingPaths.remove(worktreePath);
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> exists(String worktreePath) {
            return CompletableFuture.completedFuture(existingPaths.contains(worktreePath));
        }

        private void markExisting(String targetPath) throws IOException {
            existingPaths.add(targetPath);
            Files.createDirectories(Path.of(targetPath));
        }
    }

    private static final class RecordingManager extends WorktreeManager {
        private final RecordingBackend backend;
        private final List<String> calls = new ArrayList<>();
        private String lastMemberName;
        private String lastTeamName;
        private String exitFailureMessage;

        private RecordingManager(RecordingBackend backend) {
            super(new WorktreeConfig(), backend, null, null);
            this.backend = backend;
        }

        @Override
        public CompletableFuture<WorktreeSession> enter(String slug, String memberName, String teamName) {
            String targetPath = WorktreeManager.resolveTargetPath(slug);
            boolean existed = backend.existingPaths.contains(targetPath);
            backend.existingPaths.add(targetPath);
            lastMemberName = memberName;
            lastTeamName = teamName;
            calls.add(slug + "/" + memberName + "/" + teamName);

            WorktreeSession session = session(slug, existed);
            session.setOriginalCwd(Cwd.getCwd());
            session.setWorktreePath(targetPath);
            session.setMemberName(memberName);
            session.setTeamName(teamName);
            WorktreeSessionContext.setCurrentSession(session);
            return CompletableFuture.completedFuture(session);
        }

        @Override
        public CompletableFuture<Map<String, String>> exit(String action, boolean discardChanges) {
            if (exitFailureMessage != null) {
                return CompletableFuture.failedFuture(new RuntimeException(exitFailureMessage));
            }
            WorktreeSession session = WorktreeSessionContext.getCurrentSession();
            WorktreeSessionContext.setCurrentSession(null);
            if ("remove".equals(action)) {
                backend.removeCalled = true;
            }
            return CompletableFuture.completedFuture(Map.of(
                    "action", action,
                    "original_cwd", session.getOriginalCwd(),
                    "worktree_path", session.getWorktreePath(),
                    "worktree_branch", session.getWorktreeBranch()
            ));
        }

        @Override
        public CompletableFuture<WorktreeChangeSummary> countChanges(WorktreeSession session) {
            return CompletableFuture.completedFuture(new WorktreeChangeSummary(0, 0));
        }
    }

    private static final class RecordingHandler implements WorktreeEventHandler {
        private final List<WorktreeEvent> events = new ArrayList<>();

        @Override
        public CompletableFuture<Void> handle(WorktreeEvent event) {
            events.add(event);
            return CompletableFuture.completedFuture(null);
        }
    }
}
