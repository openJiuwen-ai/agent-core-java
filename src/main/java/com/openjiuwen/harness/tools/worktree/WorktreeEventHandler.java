/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code WorktreeEventHandler} alias in
 * {@code openjiuwen/harness/tools/worktree/events.py}.
 */
@FunctionalInterface
public interface WorktreeEventHandler {

    CompletableFuture<Void> handle(WorktreeEvent event);
}
