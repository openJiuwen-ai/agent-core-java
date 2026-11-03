/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant.workspace;

import java.nio.file.Path;
import java.util.Map;

/**
 * Result of resolving a workspace path across local and remote storage tiers.
 *
 * @since 0.1.7
 */
public class WorkspaceResolution {
    private final Path localPath;
    private final Map<String, String> remotePaths;
    private final WorkspaceType type;

    public WorkspaceResolution(Path localPath, Map<String, String> remotePaths, WorkspaceType type) {
        this.localPath = localPath;
        this.remotePaths = remotePaths;
        this.type = type;
    }

    public Path getLocalPath() {
        return localPath;
    }

    public Map<String, String> getRemotePaths() {
        return remotePaths;
    }

    public WorkspaceType getType() {
        return type;
    }

    /**
     * Returns the remote path registered for the given tier name.
     *
     * @param tierName the storage tier name
     * @return the remote path for the tier, or null if absent
     * @since 0.1.7
     */
    public String getRemotePath(String tierName) {
        return remotePaths.get(tierName);
    }

    /**
     * Checks whether a remote path is registered for the given tier name.
     *
     * @param tierName the storage tier name
     * @return true if a remote path is registered, false otherwise
     * @since 0.1.7
     */
    public boolean hasRemoteStore(String tierName) {
        return remotePaths.containsKey(tierName);
    }
}
