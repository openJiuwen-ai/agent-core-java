package com.openjiuwen.core.multitenant.workspace.store;

import com.openjiuwen.core.multitenant.workspace.WorkspaceStore;

import java.nio.file.Files;
import java.nio.file.Path;

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
            return Path.of(basePath, namespace).toAbsolutePath().normalize();
        }
        return Path.of(basePath, namespace, subDirectory).toAbsolutePath().normalize();
    }

    @Override
    public Path resolveDefaultPath(String subDirectory) {
        if (subDirectory == null || subDirectory.isEmpty()) {
            return Path.of(basePath).toAbsolutePath().normalize();
        }
        return Path.of(basePath, subDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void createDirectories(String namespace) {
        Path root = Path.of(basePath, namespace);
        try {
            Files.createDirectories(root);
            for (String subDir : new String[]{"skills", "tmp", "checkpoints", "team_memory", "todo"}) {
                Files.createDirectories(root.resolve(subDir));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create tenant directories", e);
        }
    }

    @Override
    public void createDefaultDirectories() {
        for (String subDir : new String[]{"skills", "tmp", "checkpoints", "team_memory", "todo"}) {
            try {
                Files.createDirectories(resolveDefaultPath(subDir));
            } catch (Exception e) {
                throw new RuntimeException("Failed to create default directory: " + subDir, e);
            }
        }
    }
}
