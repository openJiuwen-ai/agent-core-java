package com.openjiuwen.core.multitenant.workspace;

import java.util.Map;

public interface WorkspaceStoreProvider {
    String typeName();

    WorkspaceStore create(Map<String, Object> conf);
}
