/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant.workspace;

import java.nio.file.Path;

/**
 * Abstraction over a workspace storage tier such as local disk or object storage.
 *
 * @since 0.1.7
 */
public interface WorkspaceStore {
    /**
     * Returns the name of this storage tier.
     *
     * @return the tier name
     * @since 0.1.7
     */
    String tierName();

    /**
     * Resolves a path within a tenant namespace and sub-directory.
     *
     * @param namespace the tenant namespace
     * @param subDirectory the sub-directory within the namespace
     * @return the resolved path
     * @since 0.1.7
     */
    Path resolvePath(String namespace, String subDirectory);

    /**
     * Resolves a default path outside any tenant namespace.
     *
     * @param subDirectory the sub-directory to resolve
     * @return the resolved default path
     * @since 0.1.7
     */
    Path resolveDefaultPath(String subDirectory);

    /**
     * Creates all required directories for the given tenant namespace.
     *
     * @param namespace the tenant namespace
     * @since 0.1.7
     */
    void createDirectories(String namespace);

    /**
     * Creates all required default directories outside any tenant namespace.
     *
     * @since 0.1.7
     */
    void createDefaultDirectories();
}
