/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Workspace metadata tool for lock management and version history.
 * <p>
 * File I/O goes through standard read_file/write_file/glob via the .team/
 * mount point. This tool ONLY handles lock management and version history
 * queries that have no filesystem equivalent.
 * <p>
 * Mirrors Python's {@code WorkspaceMetaTool} in
 * {@code openjiuwen.agent_teams.team_workspace.tools}.
 */
public class WorkspaceTools {

    private static final Logger logger = Logger.getLogger(WorkspaceTools.class.getName());

    private final WorkspaceManager workspace;
    private final String toolId = "team.workspace_meta";
    private final String toolName = "workspace_meta";

    /**
     * Create WorkspaceTools.
     *
     * @param workspace Workspace manager instance
     */
    public WorkspaceTools(WorkspaceManager workspace) {
        this.workspace = workspace;
    }

    /**
     * Execute a workspace metadata action.
     *
     * @param inputs    Tool inputs with required "action" and optional "path"
     * @param memberId  Member identifier from caller context
     * @param memberName Display name from caller context
     * @return ToolOutput with action-specific result data
     */
    public CompletableFuture<ToolOutput> invoke(Map<String, Object> inputs, String memberId, String memberName) {
        String action = (String) inputs.getOrDefault("action", "");
        String path = (String) inputs.getOrDefault("path", "");

        if (memberName == null) memberName = "unknown";

        switch (action) {
            case "lock":
                if (path.isEmpty()) {
                    return CompletableFuture.completedFuture(
                        new ToolOutput(false, "'path' is required for lock action", null)
                    );
                }
                return workspace.acquireLock(path, memberId, memberName)
                    .thenApply(acquired -> {
                        if (!acquired) {
                            WorkspaceManager.WorkspaceFileLock lock = workspace.getLock(path);
                            String error = lock != null ? 
                                "Locked by " + lock.getHolderName() : "Lock failed";
                            return new ToolOutput(false, error, null);
                        }
                        Map<String, Object> data = new HashMap<>();
                        data.put("locked", path);
                        return new ToolOutput(true, null, data);
                    });

            case "unlock":
                if (path.isEmpty()) {
                    return CompletableFuture.completedFuture(
                        new ToolOutput(false, "'path' is required for unlock action", null)
                    );
                }
                boolean released = workspace.releaseLock(path, memberId);
                Map<String, Object> unlockData = new HashMap<>();
                unlockData.put("released", released);
                return CompletableFuture.completedFuture(new ToolOutput(true, null, unlockData));

            case "locks":
                List<WorkspaceManager.WorkspaceFileLock> locks = workspace.listLocks();
                List<Map<String, Object>> locksData = new java.util.ArrayList<>();
                for (WorkspaceManager.WorkspaceFileLock lock : locks) {
                    Map<String, Object> lockMap = new HashMap<>();
                    lockMap.put("path", lock.getPath());
                    lockMap.put("holder_id", lock.getHolderId());
                    lockMap.put("holder_name", lock.getHolderName());
                    lockMap.put("acquired_at", lock.getAcquiredAt());
                    locksData.add(lockMap);
                }
                Map<String, Object> data = new HashMap<>();
                data.put("locks", locksData);
                return CompletableFuture.completedFuture(new ToolOutput(true, null, data));

            case "history":
                if (path.isEmpty()) {
                    return CompletableFuture.completedFuture(
                        new ToolOutput(false, "'path' is required for history action", null)
                    );
                }
                return workspace.getHistory(path)
                    .thenApply(history -> {
                        Map<String, Object> histData = new HashMap<>();
                        histData.put("history", history);
                        return new ToolOutput(true, null, histData);
                    });

            default:
                return CompletableFuture.completedFuture(
                    new ToolOutput(false, "Unknown action '" + action + "'", null)
                );
        }
    }

    /**
     * Get tool ID.
     */
    public String getToolId() { return toolId; }

    /**
     * Get tool name.
     */
    public String getToolName() { return toolName; }

    // ── Inner class ───────────────────────────────────────

    /**
     * Tool output result.
     */
    public static class ToolOutput {
        private final boolean success;
        private final String error;
        private final Map<String, Object> data;

        public ToolOutput(boolean success, String error, Map<String, Object> data) {
            this.success = success;
            this.error = error;
            this.data = data;
        }

        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public Map<String, Object> getData() { return data; }
    }
}