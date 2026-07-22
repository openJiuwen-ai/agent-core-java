package com.openjiuwen.core.multitenant.workspace.store;

import com.openjiuwen.core.multitenant.workspace.WorkspaceStore;
import com.openjiuwen.core.multitenant.workspace.WorkspaceStoreProvider;

import java.util.Map;

public class ObjectStorageWorkspaceStoreProvider implements WorkspaceStoreProvider {
    private final String typeName;

    public ObjectStorageWorkspaceStoreProvider(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public String typeName() {
        return typeName;
    }

    @Override
    public WorkspaceStore create(Map<String, Object> conf) {
        String bucketPrefix = (String) conf.getOrDefault("bucketPrefix", "deepagent-workspace");
        String endpoint = (String) conf.getOrDefault("endpoint", "");
        return new ObjectStorageWorkspaceStore(typeName, bucketPrefix, endpoint);
    }
}
