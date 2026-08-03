/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import java.util.List;

/**
 * Package marker for worktree tools.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.worktree} in
 * {@code openjiuwen/harness/tools/worktree/__init__.py}.</p>
 */
public final class WorktreeToolsPackage {

    public static final List<Class<?>> EXPORTED_TYPES = List.of(
            EnterWorktreeTool.class,
            ExitWorktreeTool.class
    );

    private WorktreeToolsPackage() {
    }
}
