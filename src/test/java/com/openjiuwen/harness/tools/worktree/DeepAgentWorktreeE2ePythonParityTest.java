/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for DeepAgent worktree E2E tool sequences.
 *
 * <p>Mirrors Python's
 * {@code tests/system_tests/harness/tools/worktree/test_deep_agent_worktree_e2e.py}.</p>
 */
class DeepAgentWorktreeE2ePythonParityTest {

    private static final String SOURCE = "tests/system_tests/harness/tools/worktree/test_deep_agent_worktree_e2e.py";

    @TempDir
    Path tempDir;

    @AfterEach
    void resetState() {
        WorktreeSessionContext.setCurrentSession(null);
        WorktreeSessionContext.setDefaultWorktreeName(null);
        Cwd.clear();
    }

    @TestFactory
    Collection<DynamicTest> pythonDeepAgentWorktreeE2eCases() {
        return List.of(
                caseOf("test_enter_write_keep", this::enterWriteKeep),
                caseOf("test_enter_write_remove_discard", this::enterWriteRemoveDiscard),
                caseOf("test_remove_two_phase_confirmation", this::removeTwoPhaseConfirmation),
                caseOf("test_double_enter_rejected", this::doubleEnterRejected),
                caseOf("test_exit_without_session_recovers", this::exitWithoutSessionRecovers),
                caseOf("test_invalid_slug_then_recover", this::invalidSlugThenRecover)
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

    private void enterWriteKeep() throws Exception {
        WorktreeBed bed = worktreeBed("enter-write-keep");
        ToolOutput enter = bed.invokeEnter(Map.of("name", "wt-happy"));
        ToolOutput note = bed.invokeNote("notes.md", "first draft\n");
        ToolOutput exit = bed.invokeExit(Map.of("action", "keep"));

        assertThat(enter.isSuccess()).isTrue();
        assertThat(note.isSuccess()).isTrue();
        assertThat(exit.isSuccess()).isTrue();
        assertThat(data(exit)).containsEntry("action", "keep");

        Path worktreePath = Path.of(String.valueOf(data(enter).get("worktree_path")));
        assertThat(worktreePath).isDirectory();
        assertThat(worktreePath.resolve("notes.md")).hasContent("first draft\n");
        assertThat(bed.repoRoot().resolve("notes.md")).doesNotExist();
        assertThat(bed.workspaceRoot().resolve(".worktree")).doesNotExist();
        assertThat(WorktreeSessionContext.getCurrentSession()).isNull();
        assertThat(Cwd.getCwd()).isEqualTo(bed.workspaceRoot().toString());
        assertThat(bed.events()).singleElement().isInstanceOf(WorktreeCreatedEvent.class)
                .satisfies(event -> assertThat(((WorktreeCreatedEvent) event).getWorktreeName())
                        .isEqualTo("wt-happy"));
    }

    private void enterWriteRemoveDiscard() throws Exception {
        WorktreeBed bed = worktreeBed("enter-write-remove-discard");
        ToolOutput enter = bed.invokeEnter(Map.of("name", "wt-discard"));
        ToolOutput note = bed.invokeNote("scratch.txt", "throwaway\n");
        ToolOutput exit = bed.invokeExit(Map.of("action", "remove", "discard_changes", true));

        assertThat(enter.isSuccess()).isTrue();
        assertThat(note.isSuccess()).isTrue();
        assertThat(exit.isSuccess()).isTrue();
        assertThat(data(exit)).containsEntry("action", "remove");
        assertThat((Integer) data(exit).get("discarded_files")).isGreaterThanOrEqualTo(1);

        Path worktreePath = Path.of(String.valueOf(data(enter).get("worktree_path")));
        assertThat(worktreePath).doesNotExist();
        assertThat(bed.workspaceRoot().resolve(".worktree")).doesNotExist();
        assertThat(bed.events().stream().map(event -> event.getClass().getSimpleName()))
                .containsExactly(WorktreeCreatedEvent.class.getSimpleName(), WorktreeRemovedEvent.class.getSimpleName());
    }

    private void removeTwoPhaseConfirmation() throws Exception {
        WorktreeBed bed = worktreeBed("remove-two-phase-confirmation");
        ToolOutput enter = bed.invokeEnter(Map.of("name", "wt-twophase"));
        ToolOutput note = bed.invokeNote("dirty.txt", "uncommitted\n");
        ToolOutput firstExit = bed.invokeExit(Map.of("action", "remove"));
        ToolOutput secondExit = bed.invokeExit(Map.of("action", "remove", "discard_changes", true));

        assertThat(enter.isSuccess()).isTrue();
        assertThat(note.isSuccess()).isTrue();
        assertThat(firstExit.isSuccess()).isFalse();
        assertThat(firstExit.getError()).containsIgnoringCase("uncommitted");

        Path worktreePath = Path.of(String.valueOf(data(enter).get("worktree_path")));
        assertThat(secondExit.isSuccess()).isTrue();
        assertThat(data(secondExit)).containsEntry("action", "remove");
        assertThat(worktreePath).doesNotExist();
        assertThat(bed.events().stream().filter(WorktreeRemovedEvent.class::isInstance)).hasSize(1);
    }

    private void doubleEnterRejected() throws Exception {
        WorktreeBed bed = worktreeBed("double-enter-rejected");
        ToolOutput firstEnter = bed.invokeEnter(Map.of("name", "wt-first"));
        ToolOutput secondEnter = bed.invokeEnter(Map.of("name", "wt-second"));
        ToolOutput exit = bed.invokeExit(Map.of("action", "remove", "discard_changes", true));

        assertThat(firstEnter.isSuccess()).isTrue();
        assertThat(secondEnter.isSuccess()).isFalse();
        assertThat(secondEnter.getError()).contains("Already in worktree", "wt-first");
        assertThat(bed.workspaceRoot().resolve(".worktrees").resolve("wt-second")).doesNotExist();
        assertThat(exit.isSuccess()).isTrue();
        assertThat(Path.of(String.valueOf(data(firstEnter).get("worktree_path")))).doesNotExist();
        assertThat(bed.events().stream().filter(WorktreeCreatedEvent.class::isInstance)).singleElement()
                .satisfies(event -> assertThat(((WorktreeCreatedEvent) event).getWorktreeName())
                        .isEqualTo("wt-first"));
    }

    private void exitWithoutSessionRecovers() throws Exception {
        WorktreeBed bed = worktreeBed("exit-without-session-recovers");
        String cwdBeforeInvoke = bed.workspaceRoot().toString();

        ToolOutput stray = bed.invokeExit(Map.of("action", "keep"));
        ToolOutput enter = bed.invokeEnter(Map.of("name", "wt-recover"));
        ToolOutput realExit = bed.invokeExit(Map.of("action", "keep"));

        assertThat(stray.isSuccess()).isFalse();
        assertThat(stray.getError()).contains("No active worktree session");
        assertThat(enter.isSuccess()).isTrue();
        assertThat(realExit.isSuccess()).isTrue();
        assertThat(data(realExit)).containsEntry("action", "keep");
        assertThat(Cwd.getCwd()).isEqualTo(cwdBeforeInvoke);
        assertThat(WorktreeSessionContext.getCurrentSession()).isNull();
    }

    private void invalidSlugThenRecover() throws Exception {
        WorktreeBed bed = worktreeBed("invalid-slug-then-recover");
        ToolOutput bad = bed.invokeEnter(Map.of("name", "../escape"));
        ToolOutput good = bed.invokeEnter(Map.of("name", "safe-name"));
        ToolOutput exit = bed.invokeExit(Map.of("action", "remove", "discard_changes", true));

        assertThat(bad.isSuccess()).isFalse();
        assertThat(bad.getError()).contains("Invalid worktree name");

        Path worktreesDir = bed.workspaceRoot().resolve(".worktrees");
        if (Files.exists(worktreesDir)) {
            assertThat(Files.list(worktreesDir).map(path -> path.getFileName().toString()).toList())
                    .doesNotContain("escape", "..");
        }

        assertThat(good.isSuccess()).isTrue();
        Path goodPath = Path.of(String.valueOf(data(good).get("worktree_path")));
        assertThat(goodPath.getFileName().toString()).isEqualTo("safe-name");
        assertThat(exit.isSuccess()).isTrue();
        assertThat(goodPath).doesNotExist();
    }

    private WorktreeBed worktreeBed(String name) throws IOException, InterruptedException {
        Path root = tempDir.resolve(name);
        Path repoRoot = Files.createDirectories(root.resolve("repo")).toAbsolutePath().normalize();
        initGitRepo(repoRoot);
        Path workspaceRoot = Files.createDirectories(repoRoot.resolve("wkspc")).toAbsolutePath().normalize();
        Cwd.initCwd(workspaceRoot.toString(), repoRoot.toString(), workspaceRoot.toString(), null);

        List<WorktreeEvent> events = new ArrayList<>();
        WorktreeManager manager = new WorktreeManager(
                new WorktreeConfig(),
                null,
                event -> {
                    events.add(event);
                    return CompletableFuture.completedFuture(null);
                },
                null
        );
        return new WorktreeBed(
                repoRoot,
                workspaceRoot,
                new EnterWorktreeTool(manager),
                new ExitWorktreeTool(manager),
                new NoteWriteTool(),
                events
        );
    }

    private static void initGitRepo(Path repo) throws IOException, InterruptedException {
        runGit(repo, "init", "--quiet");
        runGit(repo, "symbolic-ref", "HEAD", "refs/heads/main");
        runGit(repo, "config", "user.email", "test@example.com");
        runGit(repo, "config", "user.name", "Test User");
        Files.writeString(repo.resolve("README.md"), "# integration test repo\n");
        runGit(repo, "add", "README.md");
        runGit(repo, "commit", "--quiet", "-m", "init");
        runGit(repo, "update-ref", "refs/remotes/origin/main", "HEAD");
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record WorktreeBed(
            Path repoRoot,
            Path workspaceRoot,
            EnterWorktreeTool enterTool,
            ExitWorktreeTool exitTool,
            NoteWriteTool noteTool,
            List<WorktreeEvent> events
    ) {
        private ToolOutput invokeEnter(Map<String, Object> inputs) throws Exception {
            return (ToolOutput) enterTool.invoke(inputs, Map.of());
        }

        private ToolOutput invokeExit(Map<String, Object> inputs) throws Exception {
            return (ToolOutput) exitTool.invoke(inputs, Map.of());
        }

        private ToolOutput invokeNote(String path, String content) throws Exception {
            return (ToolOutput) noteTool.invoke(Map.of("path", path, "content", content), Map.of());
        }
    }

    private static final class NoteWriteTool extends AbstractHarnessTool {

        private NoteWriteTool() {
            super(toolCard("note_write", "note_write", "Write a UTF-8 text file relative to current cwd."));
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws IOException {
            String path = stringValue(inputs.get("path"));
            String content = stringValue(inputs.get("content"));
            if (path.isBlank()) {
                return ToolOutput.failure("path is required");
            }
            Path target = Path.of(Cwd.getCwd()).resolve(path);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, content);
            return ToolOutput.success(Map.of("path", target.toString(), "size", content.length()));
        }
    }
}
