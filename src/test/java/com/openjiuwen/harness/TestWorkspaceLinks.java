/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.harness.workspace.Workspace;
import com.openjiuwen.harness.workspace.WorkspaceNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_workspace_links} in
 * {@code tests.unit_tests.harness.test_workspace_links}.
 */
class TestWorkspaceLinks {

    @TempDir
    Path tmpDir;

    @Test
    void testTeamLinksValue() {
        assertEquals(".team", WorkspaceNode.TEAM_LINKS.getValue());
    }

    @Test
    void testWorktreeLinksValue() {
        assertEquals(".worktree", WorkspaceNode.WORKTREE_LINKS.getValue());
    }

    @Test
    void testLinkTeamCreatesDirectoryLink() throws Exception {
        Workspace workspace = workspace();
        Path target = Files.createDirectory(tmpDir.resolve("team-a"));

        Path link = workspace.linkTeam("team-a", target.toString());

        assertTrue(Files.exists(link));
        assertTrue(workspace.isDirectoryLink(link));
        assertEquals(target.toRealPath(), link.toRealPath());
    }

    @Test
    void testLinkTeamIdempotent() throws Exception {
        Workspace workspace = workspace();
        Path target = Files.createDirectory(tmpDir.resolve("team-idem"));

        Path first = workspace.linkTeam("team-idem", target.toString());
        Path second = workspace.linkTeam("team-idem", target.toString());

        assertEquals(first, second);
        assertEquals(target.toRealPath(), second.toRealPath());
    }

    @Test
    void testLinkTeamMultipleTeams() throws Exception {
        Workspace workspace = workspace();
        Path a = Files.createDirectory(tmpDir.resolve("team-one"));
        Path b = Files.createDirectory(tmpDir.resolve("team-two"));

        workspace.linkTeam("b", b.toString());
        workspace.linkTeam("a", a.toString());

        assertEquals(List.of("a", "b"), workspace.listTeamLinks().stream().map(Map.Entry::getKey).toList());
    }

    @Test
    void testUnlinkTeamRemovesSymlink() throws Exception {
        Workspace workspace = workspace();
        Path target = Files.createDirectory(tmpDir.resolve("team-remove"));
        Path link = workspace.linkTeam("team-remove", target.toString());

        assertTrue(workspace.unlinkTeam("team-remove"));

        assertFalse(Files.exists(link));
        assertTrue(Files.exists(target));
    }

    @Test
    void testUnlinkTeamReturnsFalseWhenMissing() {
        assertFalse(workspace().unlinkTeam("missing-team"));
    }

    @Test
    void testLinkWorktreeCreatesDirectoryLink() throws Exception {
        Workspace workspace = workspace();
        Path target = Files.createDirectory(tmpDir.resolve("worktree-a"));

        Path link = workspace.linkWorktree("feature-a", target.toString());

        assertTrue(Files.exists(link));
        assertTrue(workspace.isDirectoryLink(link));
        assertEquals(target.toRealPath(), link.toRealPath());
    }

    @Test
    void testLinkWorktreeIdempotent() throws Exception {
        Workspace workspace = workspace();
        Path target = Files.createDirectory(tmpDir.resolve("worktree-idem"));

        Path first = workspace.linkWorktree("feature-idem", target.toString());
        Path second = workspace.linkWorktree("feature-idem", target.toString());

        assertEquals(first, second);
    }

    @Test
    void testUnlinkWorktreeRemovesSymlink() throws Exception {
        Workspace workspace = workspace();
        Path target = Files.createDirectory(tmpDir.resolve("worktree-remove"));
        Path link = workspace.linkWorktree("feature-remove", target.toString());

        assertTrue(workspace.unlinkWorktree("feature-remove"));

        assertFalse(Files.exists(link));
        assertTrue(Files.exists(target));
    }

    @Test
    void testUnlinkWorktreeReturnsFalseWhenMissing() {
        assertFalse(workspace().unlinkWorktree("missing-worktree"));
    }

    @Test
    void testListTeamLinksEmpty() {
        assertTrue(workspace().listTeamLinks().isEmpty());
    }

    @Test
    void testListWorktreeLinksEmpty() {
        assertTrue(workspace().listWorktreeLinks().isEmpty());
    }

    @Test
    void testListTeamLinksSorted() throws Exception {
        Workspace workspace = workspace();
        workspace.linkTeam("z-team", Files.createDirectory(tmpDir.resolve("z")).toString());
        workspace.linkTeam("a-team", Files.createDirectory(tmpDir.resolve("a")).toString());

        assertEquals(List.of("a-team", "z-team"), workspace.listTeamLinks().stream().map(Map.Entry::getKey).toList());
    }

    @Test
    void testListWorktreeLinksResolvesTarget() throws Exception {
        Workspace workspace = workspace();
        Path target = Files.createDirectory(tmpDir.resolve("resolved-target"));

        workspace.linkWorktree("resolved", target.toString());

        assertEquals(target.toRealPath().toString(), workspace.listWorktreeLinks().getFirst().getValue());
    }

    @Test
    void testListTeamLinksIncludesWindowsDirectoryLinks() throws Exception {
        Workspace workspace = workspace();
        Path target = Files.createDirectory(tmpDir.resolve("team-listed"));
        workspace.linkTeam("listed", target.toString());

        assertTrue(workspace.listTeamLinks().stream().anyMatch(entry -> entry.getKey().equals("listed")));
    }

    @Test
    void testCreateDirectoryLinkFallsBackToJunctionOnWindows1314() throws Exception {
        Workspace workspace = workspace();
        Path target = Files.createDirectory(tmpDir.resolve("junction-target"));
        Path link = tmpDir.resolve("manual-link");

        workspace.createDirectoryLink(target.toString(), link);

        assertTrue(workspace.isDirectoryLink(link));
        assertEquals(target.toRealPath(), link.toRealPath());
    }

    private Workspace workspace() {
        return new Workspace(tmpDir.resolve("workspace").toString(), "en");
    }
}
