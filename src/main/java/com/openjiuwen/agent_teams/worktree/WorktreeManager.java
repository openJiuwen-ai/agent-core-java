/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.worktree;

import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.events.TeamTopic;

import java.nio.file.Path;
import java.util.Map;

/**
 * Minimal worktree lifecycle manager.
 *
 * <p>Mirrors Python's {@code WorktreeManager} in
 * {@code openjiuwen.agent_teams.worktree.manager}.</p>
 */
public class WorktreeManager {

    private final WorktreeConfig config;
    private final Messager messager;
    private final String workspaceRoot;

    public WorktreeManager(WorktreeConfig config, Messager messager, String workspaceRoot) {
        this.config = config != null ? config : new WorktreeConfig();
        this.messager = messager;
        this.workspaceRoot = workspaceRoot;
    }

    public WorktreeSession enter(String slug, String memberName, String teamName) {
        String root = workspaceRoot != null ? workspaceRoot : System.getProperty("user.dir");
        String worktreeRoot = config.getBaseDir() != null ? config.getBaseDir() : root + "/.agent_teams/worktrees";
        String worktreePath = Path.of(worktreeRoot, slug).toString();
        WorktreeSession session = new WorktreeSession(
                root,
                root,
                teamName,
                memberName,
                slug,
                worktreePath,
                "worktree-" + slug
        );
        WorktreeSessionHolder.setCurrentSession(session);
        publish("worktree_created", teamName, Map.of(
                "worktree_name", slug,
                "worktree_path", worktreePath,
                "existed", false
        ));
        return session;
    }

    public boolean removeCurrent(boolean force) {
        WorktreeSession session = WorktreeSessionHolder.getCurrentSession();
        if (session == null) {
            return false;
        }
        publish("worktree_removed", session.getTeamName(), Map.of(
                "worktree_name", session.getSlug(),
                "worktree_path", session.getWorktreePath(),
                "force", force
        ));
        WorktreeSessionHolder.setCurrentSession(null);
        return true;
    }

    public WorktreeChangeSummary summarizeChanges() {
        WorktreeSession session = WorktreeSessionHolder.getCurrentSession();
        if (session == null) {
            return new WorktreeChangeSummary(false, 0, null);
        }
        return new WorktreeChangeSummary(false, 0, session.getBranchName());
    }

    private void publish(String eventType, String teamName, Map<String, Object> payload) {
        if (messager == null || teamName == null) {
            return;
        }
        messager.publish(TeamTopic.TEAM.build("shared", teamName), new EventMessage(eventType, payload));
    }
}
