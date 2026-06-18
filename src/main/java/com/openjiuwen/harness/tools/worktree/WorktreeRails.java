/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Worktree lifecycle rails.
 *
 * <p>Mirrors Python's {@code WorktreeRail}, {@code WorktreeLifecycleRail},
 * {@code AutoSetupRail}, and {@code DiffSummaryRail} in
 * {@code openjiuwen/harness/tools/worktree/rails.py}.</p>
 */
public final class WorktreeRails {

    public static final String SESSION_STATE_KEY = "_worktree_session";
    public static final String DEFAULT_WORKTREE_NAME_KEY = "_worktree_default_name";

    private WorktreeRails() {
    }

    /**
     * Mirrors Python's {@code WorktreeRail} in
     * {@code openjiuwen/harness/tools/worktree/rails.py}.
     */
    public static class WorktreeRail extends DeepAgentRail {
        @Override
        public void beforeInvoke(CallbackContext ctx) {
            if (ctx != null && ctx.get(SESSION_STATE_KEY) == null) {
                ctx.put(SESSION_STATE_KEY, new LinkedHashMap<String, Object>());
            }
        }
    }

    /**
     * Mirrors Python's {@code WorktreeLifecycleRail} in
     * {@code openjiuwen/harness/tools/worktree/rails.py}.
     */
    public static class WorktreeLifecycleRail extends WorktreeRail {
        @Override
        public void afterInvoke(CallbackContext ctx) {
            if (ctx != null && ctx.get(SESSION_STATE_KEY) instanceof Map<?, ?> state) {
                ctx.put("_worktree_lifecycle_seen", !state.isEmpty());
            }
        }
    }

    /**
     * Mirrors Python's {@code AutoSetupRail} in
     * {@code openjiuwen/harness/tools/worktree/rails.py}.
     */
    public static class AutoSetupRail extends WorktreeLifecycleRail {
        @Override
        public void beforeInvoke(CallbackContext ctx) {
            super.beforeInvoke(ctx);
            if (ctx != null && ctx.get(DEFAULT_WORKTREE_NAME_KEY) == null) {
                ctx.put(DEFAULT_WORKTREE_NAME_KEY, "default");
            }
        }
    }

    /**
     * Mirrors Python's {@code DiffSummaryRail} in
     * {@code openjiuwen/harness/tools/worktree/rails.py}.
     */
    public static class DiffSummaryRail extends WorktreeLifecycleRail {
        @Override
        public void afterToolCall(CallbackContext ctx) {
            if (ctx != null) {
                ctx.put("_worktree_diff_summary_requested", true);
            }
        }
    }
}
