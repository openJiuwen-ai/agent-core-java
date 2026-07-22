package com.openjiuwen.core.multitenant.workspace;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkspaceStoreFactory {
    private static final Map<String, WorkspaceStoreProvider> REGISTRY = new ConcurrentHashMap<>();

    static {
        for (WorkspaceStoreProvider provider : ServiceLoader.load(WorkspaceStoreProvider.class)) {
            REGISTRY.putIfAbsent(provider.typeName(), provider);
        }
    }

    private WorkspaceStoreFactory() {}

    public static void register(String type, WorkspaceStoreProvider provider) {
        REGISTRY.put(type, provider);
    }

    public static WorkspaceStore create(String type, Map<String, Object> conf) {
        WorkspaceStoreProvider provider = REGISTRY.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No workspace store provider registered for type: " + type);
        }
        return provider.create(conf != null ? conf : Map.of());
    }

    public static boolean hasProvider(String type) {
        return type != null && REGISTRY.containsKey(type);
    }
}
