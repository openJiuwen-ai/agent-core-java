package com.openjiuwen.core.multitenant.workspace.store;

import com.openjiuwen.core.multitenant.workspace.WorkspaceStore;
import com.openjiuwen.core.multitenant.workspace.WorkspaceStoreProvider;

import java.util.Map;

public class LocalWorkspaceStoreProvider implements WorkspaceStoreProvider {
    @Override
    public String typeName() {
        return "local";
    }

    @Override
    public WorkspaceStore create(Map<String, Object> conf) {
        String basePath = (String) conf.getOrDefault("basePath", System.getProperty("user.dir"));
        return new LocalWorkspaceStore(basePath);
    }
}
