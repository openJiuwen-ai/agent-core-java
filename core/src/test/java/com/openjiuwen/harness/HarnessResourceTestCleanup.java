/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.harness.deep_agent.DeepAgent;

/**
 * Shared cleanup for harness tests that register global tools via {@link DeepAgent}.
 *
 * <p>DeepAgent registers {@code skill_tool}/{@code list_skill} into the process-wide
 * {@code ResourceMgr}. Without force-removal between tests, a prior registration with a
 * different card surface causes {@code definition conflict with existing resource}.</p>
 *
 * @since 0.1.18
 */
public final class HarnessResourceTestCleanup {
    private static final String[] SHARED_SKILL_TOOL_IDS = {
            "skill_tool",
            "list_skill",
            "todo_create",
            "todo_list",
            "todo_modify",
            "bash",
            "code",
            "read_file",
            "write_file",
            "edit_file",
            "list_files"
    };

    private HarnessResourceTestCleanup() {
    }

    /**
     * Force-remove shared harness tool ids from the process-global {@link Runner} resource manager.
     *
     * <p>Missing entries are ignored so tests can call this from {@code @BeforeEach}/{@code @AfterEach}
     * without caring whether a prior test registered the tool.</p>
     *
     * @since 0.1.18
     */
    public static void forceRemoveSharedHarnessTools() {
        for (String toolId : SHARED_SKILL_TOOL_IDS) {
            try {
                Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
            } catch (BaseError | IllegalArgumentException | IllegalStateException ignored) {
                // Best-effort isolation; missing entries are fine.
            }
        }
    }

    /**
     * Destroy a {@link DeepAgent} without failing the caller when destroy steps throw.
     *
     * @param agent agent to destroy; ignored when {@code null}
     * @since 0.1.18
     */
    public static void destroyQuietly(DeepAgent agent) {
        if (agent == null) {
            return;
        }
        try {
            agent.destroy();
        } catch (BaseError | IllegalArgumentException | IllegalStateException ignored) {
            // Best-effort; force-remove covers residual global entries.
        }
    }
}
