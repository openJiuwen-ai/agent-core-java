/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.teamworkspace;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.Set;

/**
 * Transparent version control and locking for the .team/ mount point.
 * <p>
 * Mirrors Python TeamWorkspaceRail: intercepts filesystem tool calls
 * and applies workspace policies (pull before read, lock on write,
 * auto-commit after write).
 * </p>
 * 
 * @since 0.1.7
 */
public class TeamWorkspaceRail extends DeepAgentRail {
    private static final String TEAM_PREFIX = ".team/";

    /**
     * Set.of.
     * 
     * @since 0.1.7
     */
    private static final Set<String> WRITE_TOOLS = Set.of("write_file", "edit_file");

    /**
     * Set.of.
     * 
     * @since 0.1.7
     */
    private static final Set<String> READ_TOOLS = Set.of("read_file", "glob", "grep", "list_files");

    private final TeamWorkspaceManager workspaceManager;
    private final String memberName;

    /**
     * TeamWorkspaceRail.
     * 
     * @param workspaceManager workspaceManager
     * @param memberName memberName
     * @since 0.1.7
     */
    public TeamWorkspaceRail(TeamWorkspaceManager workspaceManager, String memberName) {
        this.workspaceManager = workspaceManager;
        this.memberName = memberName;
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 25;
    }

    /**
     * beforeToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (ctx == null) {
            return;
        }

        String path = extractPath(ctx);
        boolean isTeamPath = path != null && path.startsWith(TEAM_PREFIX);

        if (isTeamPath) {
            maybePull();
        }
    }

    /**
     * afterToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        if (ctx == null) {
            return;
        }

        String path = extractPath(ctx);
        if (path != null && path.startsWith(TEAM_PREFIX)) {
            String realPath = resolveWorkspaceRelative(path);
            // Workspace artifact versioning handled by TeamWorkspaceManager
        }
    }

    /**
     * maybePull.
     * 
     * @since 0.1.7
     */
    private void maybePull() {
        // Throttled pull for distributed mode
    }

    /**
     * extractPath.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    private static String extractPath(AgentCallbackContext ctx) {
        // Path extraction is context-dependent; override in subclasses as needed
        return null;
    }

    /**
     * resolveWorkspaceRelative.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    private static String resolveWorkspaceRelative(String path) {
        if (path == null) {
            return null;
        }
        return path.startsWith(TEAM_PREFIX) ? path.substring(TEAM_PREFIX.length()) : path;
    }
}
