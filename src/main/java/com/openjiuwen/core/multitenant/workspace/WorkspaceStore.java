package com.openjiuwen.core.multitenant.workspace;

import java.nio.file.Path;

public interface WorkspaceStore {
    String tierName();

    Path resolvePath(String namespace, String subDirectory);

    Path resolveDefaultPath(String subDirectory);

    void createDirectories(String namespace);

    void createDefaultDirectories();
}
