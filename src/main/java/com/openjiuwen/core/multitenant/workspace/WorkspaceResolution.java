package com.openjiuwen.core.multitenant.workspace;

import java.nio.file.Path;
import java.util.Map;

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

    public String getRemotePath(String tierName) {
        return remotePaths.get(tierName);
    }

    public boolean hasRemoteStore(String tierName) {
        return remotePaths.containsKey(tierName);
    }
}
