package com.openjiuwen.core.session.store;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionStoreFactory {
    private static final Map<String, SessionStoreProvider> REGISTRY = new ConcurrentHashMap<>();

    static {
        for (SessionStoreProvider provider : ServiceLoader.load(SessionStoreProvider.class)) {
            REGISTRY.putIfAbsent(provider.typeName(), provider);
        }
    }

    private SessionStoreFactory() {
    }

    public static void register(String type, SessionStoreProvider provider) {
        REGISTRY.put(type, provider);
    }

    public static Store create(String type, Map<String, Object> conf) {
        SessionStoreProvider provider = REGISTRY.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No SessionStore provider registered for type: " + type);
        }
        return provider.createStore(conf != null ? conf : Map.of());
    }

    public static boolean hasProvider(String type) {
        return type != null && REGISTRY.containsKey(type);
    }
}
