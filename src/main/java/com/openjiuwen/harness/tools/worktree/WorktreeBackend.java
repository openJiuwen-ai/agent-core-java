/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code WorktreeBackend} protocol in
 * {@code openjiuwen/harness/tools/worktree/backend.py}.
 */
public interface WorktreeBackend {

    CompletableFuture<WorktreeCreateResult> create(String slug, String repoRoot, String targetPath);

    CompletableFuture<Boolean> remove(String worktreePath, String repoRoot);

    CompletableFuture<Boolean> exists(String worktreePath);
}
