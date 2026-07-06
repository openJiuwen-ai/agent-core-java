package com.openjiuwen.agentteams.worktree;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorktreeNoticeCompatibilityTest {

    @Test
    void shouldBuildNoticeForWorktreeContext() {
        Path parentCwd = Path.of("repo", "main").toAbsolutePath();
        Path worktreeCwd = Path.of("repo", ".worktrees", "feature-1").toAbsolutePath();

        String notice = WorktreeNotice.buildWorktreeNotice(parentCwd, worktreeCwd);

        assertThat(notice)
                .contains("Git Worktree Isolation Notice")
                .contains("repo")
                .contains("isolated")
                .contains("translate them to your worktree root");
    }

    @Test
    void shouldMentionEnterAndExitTools() {
        String notice = WorktreeNotice.buildWorktreeNotice(
                Path.of("repo", "main"), Path.of("tmp", "wt"));

        assertThat(notice).contains("EnterWorktreeTool");
        assertThat(notice).contains("ExitWorktreeTool");
    }

    @Test
    void shouldHandleNullParentPath() {
        String notice = WorktreeNotice.buildWorktreeNotice(null, Path.of("tmp", "wt"));
        assertThat(notice).contains("Git Worktree Isolation Notice");
        assertThat(notice).doesNotContain("null");
    }

    @Test
    void shouldHandleNullWorktreePath() {
        String notice = WorktreeNotice.buildWorktreeNotice(Path.of("repo", "main"), null);
        assertThat(notice).contains("Git Worktree Isolation Notice");
        assertThat(notice).doesNotContain("null");
    }

    @Test
    void shouldHandleBothNullPaths() {
        String notice = WorktreeNotice.buildWorktreeNotice(null, null);
        assertThat(notice).contains("Git Worktree Isolation Notice");
        assertThat(notice).doesNotContain("null");
    }
}
