/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant.workspace.store;

import com.openjiuwen.core.multitenant.workspace.WorkspaceStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Workspace store backed by the local filesystem under a configurable base path.
 *
 * @since 0.1.7
 */
public class LocalWorkspaceStore implements WorkspaceStore {
    private final String basePath;

    public LocalWorkspaceStore(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public String tierName() {
        return "local";
    }

    @Override
    public Path resolvePath(String namespace, String subDirectory) {
        if (subDirectory == null || subDirectory.isEmpty()) {
            return toRealOrAbsolutePath(Path.of(basePath, namespace));
        }
        return toRealOrAbsolutePath(Path.of(basePath, namespace, subDirectory));
    }

    @Override
    public Path resolveDefaultPath(String subDirectory) {
        if (subDirectory == null || subDirectory.isEmpty()) {
            return toRealOrAbsolutePath(Path.of(basePath));
        }
        return toRealOrAbsolutePath(Path.of(basePath, subDirectory));
    }

    private static Path toRealOrAbsolutePath(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize();
        }
    }

    @Override
    public void createDirectories(String namespace) {
        Path root = Path.of(basePath, namespace);
        try {
            Files.createDirectories(root);
            for (String subDir : new String[]{"skills", "tmp", "checkpoints", "team_memory", "todo"}) {
                Files.createDirectories(root.resolve(subDir));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create tenant directories", e);
        }
    }

    @Override
    public void createDefaultDirectories() {
        for (String subDir : new String[]{"skills", "tmp", "checkpoints", "team_memory", "todo"}) {
            try {
                Files.createDirectories(resolveDefaultPath(subDir));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create default directory: " + subDir, e);
            }
        }
    }
}
