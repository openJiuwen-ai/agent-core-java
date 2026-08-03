/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.harness.workspace.Workspace;
import com.openjiuwen.harness.workspace.WorkspaceNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.test_workspace_links} in
 * {@code tests/unit_tests/harness/test_workspace_links.py}.
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
        assertEquals("team-a", link.getFileName().toString());
    }

    @Test
    void testLinkTeamIdempotent() throws Exception {
        Workspace workspace = workspace();
        Path target = Files.createDirectory(tmpDir.resolve("team-idem"));

        Path first = workspace.linkTeam("team-idem", target.toString());
        Path second = workspace.linkTeam("team-idem", target.toString());

        assertEquals(first, second);
        assertTrue(Files.exists(second));
    }

    @Test
    void testLinkTeamMultipleTeams() throws Exception {
        Workspace workspace = workspace();
        Path teamOne = Files.createDirectory(tmpDir.resolve("team_1"));
        Path teamTwo = Files.createDirectory(tmpDir.resolve("team_2"));
        Path teamThree = Files.createDirectory(tmpDir.resolve("team_3"));

        workspace.linkTeam("team_1", teamOne.toString());
        workspace.linkTeam("team_2", teamTwo.toString());
        workspace.linkTeam("team_3", teamThree.toString());

        assertEquals(List.of("team_1", "team_2", "team_3"),
                workspace.listTeamLinks().stream().map(Map.Entry::getKey).toList());
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
        assertEquals("feature-a", link.getFileName().toString());
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
        workspace.linkTeam("beta", Files.createDirectory(tmpDir.resolve("beta")).toString());
        workspace.linkTeam("alpha", Files.createDirectory(tmpDir.resolve("alpha")).toString());
        workspace.linkTeam("gamma", Files.createDirectory(tmpDir.resolve("gamma")).toString());

        assertEquals(List.of("alpha", "beta", "gamma"),
                workspace.listTeamLinks().stream().map(Map.Entry::getKey).toList());
    }

    @Test
    void testListWorktreeLinksResolvesTarget() throws Exception {
        Workspace workspace = workspace();
        Path target = Files.createDirectory(tmpDir.resolve("resolved-target"));

        workspace.linkWorktree("resolved", target.toString());

        List<Map.Entry<String, String>> links = workspace.listWorktreeLinks();
        assertEquals("resolved", links.get(0).getKey());
        assertEquals(target.toRealPath().toString(), links.get(0).getValue());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testListTeamLinksIncludesWindowsDirectoryLinks() throws Exception {
        Workspace workspace = new Workspace(tmpDir.resolve("workspace").toString(), "en") {
            @Override
            public boolean isDirectoryLink(Path entry) {
                return "team_junction".equals(entry.getFileName().toString());
            }
        };
        Path target = Files.createDirectory(tmpDir.resolve("team-target"));
        Path linkDirectory = Path.of(workspace.getRootPath(), ".team");
        Files.createDirectories(linkDirectory);
        Path junctionPath = linkDirectory.resolve("team_junction");
        workspace.createDirectoryLink(target.toString(), junctionPath);
        Files.createDirectory(linkDirectory.resolve("team_regular"));

        assertEquals(List.of(Map.entry("team_junction", target.toRealPath().toString())), workspace.listTeamLinks());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testCreateDirectoryLinkFallsBackToJunctionOnWindows1314() throws Exception {
        Workspace workspace = workspace();
        Path target = Files.createDirectory(tmpDir.resolve("junction-target"));
        Path link = tmpDir.resolve("manual-link");

        workspace.createDirectoryLink(target.toString(), link);

        assertTrue(workspace.isDirectoryLink(link));
        assertTrue(Files.exists(link));
    }

    private Workspace workspace() {
        return new Workspace(tmpDir.resolve("workspace").toString(), "en");
    }
}
