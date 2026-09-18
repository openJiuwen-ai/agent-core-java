/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant.workspace.store;

import com.openjiuwen.core.multitenant.workspace.WorkspaceStore;

import java.nio.file.Path;

/**
 * Workspace store backed by flat object storage with a tier name and bucket prefix.
 *
 * @since 0.1.7
 */
public class ObjectStorageWorkspaceStore implements WorkspaceStore {
    private final String tierName;
    private final String bucketPrefix;
    private final String endpoint;

    public ObjectStorageWorkspaceStore(String tierName, String bucketPrefix, String endpoint) {
        this.tierName = tierName;
        this.bucketPrefix = bucketPrefix;
        this.endpoint = endpoint;
    }

    @Override
    public String tierName() {
        return tierName;
    }

    @Override
    public Path resolvePath(String namespace, String subDirectory) {
        if (subDirectory == null || subDirectory.isEmpty()) {
            return Path.of(namespace);
        }
        return Path.of(namespace, subDirectory);
    }

    @Override
    public Path resolveDefaultPath(String subDirectory) {
        if (subDirectory == null || subDirectory.isEmpty()) {
            return Path.of("");
        }
        return Path.of(subDirectory);
    }

    @Override
    public void createDirectories(String namespace) {
        // Object storage is flat; no directory creation needed.
    }

    @Override
    public void createDefaultDirectories() {
        // Object storage is flat; no directory creation needed.
    }

    public String getBucketPrefix() {
        return bucketPrefix;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
