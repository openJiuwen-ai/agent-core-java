/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.infrastructure;

/**
 * Exact repository path scope accepted by the demo local Committer.
 *
 * @since 0.1.12
 */
public final class CommitScope {
    private CommitScope() {
    }

    /**
     * Return whether a normalized path is inside the source or test edit scope.
     *
     * @param path repository-relative path
     * @return whether the path may be committed
     */
    public static boolean isAllowedRepoEditPath(String path) {
        String normalized = normalizePath(path);
        return normalized.startsWith("src/main/") || normalized.startsWith("src/test/");
    }

    static String normalizePath(String path) {
        return path == null ? "" : path.strip().replace('\\', '/');
    }
}
