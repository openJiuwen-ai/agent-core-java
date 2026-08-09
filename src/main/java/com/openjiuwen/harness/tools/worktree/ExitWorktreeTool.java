/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import com.openjiuwen.core.sysop.Cwd;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exits the current worktree session.
 *
 * <p>Mirrors Python's {@code ExitWorktreeTool} in
 * {@code openjiuwen/harness/tools/worktree/tools.py}.</p>
 */
public class ExitWorktreeTool extends AbstractHarnessTool {

    private final WorktreeManager manager;

    public ExitWorktreeTool(WorktreeManager manager) {
        super(toolCard("exit_worktree", "worktree.exit", "Exit the current worktree session."));
        this.manager = manager;
    }

    public ExitWorktreeTool(WorktreeManager manager, String language, String agentId) {
        super(toolCard(
                scopedToolId("exit_worktree", agentId),
                "exit_worktree",
                "Exit the current worktree session.",
                language));
        this.manager = manager;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        WorktreeSession session = WorktreeSessionContext.getCurrentSession();
        if (session == null) {
            return ToolOutput.failure("No active worktree session to exit.");
        }
        String action = stringValue(inputs == null ? null : inputs.get("action"));
        if (!"keep".equals(action) && !"remove".equals(action)) {
            return ToolOutput.failure("'action' must be 'keep' or 'remove'.");
        }
        boolean discardChanges = boolValue(inputs == null ? null : inputs.get("discard_changes"), false);
        try {
            Integer discardedFiles = null;
            Integer discardedCommits = null;
            if ("remove".equals(action) && discardChanges) {
                WorktreeChangeSummary summary = manager.countChanges(session).join();
                if (summary != null) {
                    discardedFiles = summary.getChangedFiles();
                    discardedCommits = summary.getCommits();
                }
            }
            Map<String, String> result = manager.exit(action, discardChanges).join();
            String originalCwd = result.get("original_cwd");
            if (originalCwd != null && !originalCwd.isBlank()) {
                Cwd.setCwd(originalCwd);
                Cwd.setOriginalCwd(originalCwd);
            }
            Map<String, Object> data = new LinkedHashMap<>(result);
            data.put("worktree_name", session.getWorktreeName());
            data.put("message", message(action, session, result));
            if (discardedFiles != null) {
                data.put("discarded_files", discardedFiles);
            }
            if (discardedCommits != null) {
                data.put("discarded_commits", discardedCommits);
            }
            return ToolOutput.success(data);
        } catch (RuntimeException exception) {
            return ToolOutput.failure("Failed to exit worktree: " + EnterWorktreeTool.rootMessage(exception));
        }
    }

    private static String message(String action, WorktreeSession session, Map<String, String> result) {
        String branch = result.getOrDefault("worktree_branch", "unknown");
        String originalCwd = result.get("original_cwd");
        if ("keep".equals(action)) {
            return "Kept worktree '" + session.getWorktreeName() + "' (branch " + branch
                    + "). In this session, enter_worktree without a name will re-enter it; from another session, "
                    + "pass name='" + session.getWorktreeName() + "'. Returned to " + originalCwd;
        }
        return "Removed worktree '" + session.getWorktreeName() + "' (branch " + branch
                + "). Returned to " + originalCwd;
    }

    private static String scopedToolId(String baseId, String agentId) {
        return agentId == null || agentId.isBlank() ? baseId : baseId + "-" + agentId;
    }
}
