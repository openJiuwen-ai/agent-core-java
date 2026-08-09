/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production (async {@link RemoteClient}) factory for remote-client implementations.
 *
 * <p>Built-in MQ/A2A paths stay here. Additional SPI providers are discovered from
 * {@code com.openjiuwen.core.runner.drunner.remoteclient.RemoteClientProvider}
 * (ServiceLoader) and mapped onto this async API for known protocols.</p>
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/core/runner/drunner/remote_client/__init__.py}.</p>
 */
public final class RemoteClientFactory {

    public static final String REMOTE_CLIENTS_ENTRY_POINT_GROUP = "openjiuwen.remote_clients";
    private static final Set<String> BUILTIN_REMOTE_CLIENT_NAMES = Set.of(ProtocolEnum.MQ.name());
    private static final Map<String, RemoteClientCreator> CUSTOM_REMOTE_CLIENTS = new ConcurrentHashMap<>();
    private static final Map<String, String> OFFICIAL_REMOTE_CLIENT_BOOTSTRAP = Map.of(
            ProtocolEnum.A2A.name(), "com.openjiuwen.extensions.a2a.A2ARemoteClient");

    private RemoteClientFactory() {
    }

    public static void registerRemoteClient(String name, RemoteClientCreator factory) {
        CUSTOM_REMOTE_CLIENTS.put(name, factory);
    }

    public static void clearCustomRemoteClientsForTest() {
        CUSTOM_REMOTE_CLIENTS.clear();
    }

    public static RemoteClient createRemoteClient(ProtocolEnum protocol, RemoteClientConfig config) {
        return createRemoteClient(protocol == null ? null : protocol.name(), config);
    }

    public static RemoteClient createRemoteClient(String protocol, RemoteClientConfig config) {
        if (protocol == null) {
            return null;
        }
        String normalizedProtocol = normalizeProtocol(protocol);
        if (BUILTIN_REMOTE_CLIENT_NAMES.contains(normalizedProtocol)) {
            return resolveBuiltin(normalizedProtocol, config);
        }

        if (!CUSTOM_REMOTE_CLIENTS.containsKey(normalizedProtocol)) {
            bootstrapOfficialRemoteClient(normalizedProtocol);
        }

        RemoteClientCreator customFactory = CUSTOM_REMOTE_CLIENTS.get(normalizedProtocol);
        if (customFactory != null) {
            return customFactory.create(config);
        }

        RemoteClient fromSpi = resolveRemoteclientSpi(normalizedProtocol, config);
        if (fromSpi != null) {
            return fromSpi;
        }

        return resolveEntryPoint(normalizedProtocol, config);
    }

    private static RemoteClient resolveBuiltin(String protocol, RemoteClientConfig config) {
        if (ProtocolEnum.MQ.name().equals(protocol)) {
            return new MqRemoteClient(config);
        }
        return null;
    }

    private static void bootstrapOfficialRemoteClient(String protocol) {
        String className = OFFICIAL_REMOTE_CLIENT_BOOTSTRAP.get(protocol);
        if (className == null) {
            return;
        }
        try {
            Class<?> implementation = Class.forName(className);
            if (RemoteClient.class.isAssignableFrom(implementation)) {
                CUSTOM_REMOTE_CLIENTS.putIfAbsent(protocol, config -> newOfficialRemoteClient(implementation, config));
            }
        } catch (Exception ignored) {
            // Python deliberately treats optional plugin bootstrap failures as best-effort.
        }
    }

    private static RemoteClient newOfficialRemoteClient(Class<?> implementation, RemoteClientConfig config) {
        try {
            return (RemoteClient) implementation.getConstructor(RemoteClientConfig.class).newInstance(config);
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof java.lang.reflect.InvocationTargetException
                    && exception.getCause() != null
                    ? exception.getCause()
                    : exception;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    /**
     * Bridge SPI providers under {@code remoteclient} onto the production async API.
     * Known protocols create native {@code remote_client} implementations; unknown
     * protocols remain on the SPI package's own factory.
     */
    private static RemoteClient resolveRemoteclientSpi(String protocol, RemoteClientConfig config) {
        try {
            for (com.openjiuwen.core.runner.drunner.remoteclient.RemoteClientProvider provider
                    : ServiceLoader.load(com.openjiuwen.core.runner.drunner.remoteclient.RemoteClientProvider.class)) {
                if (!protocol.equals(normalizeProtocol(provider.typeName()))) {
                    continue;
                }
                if (ProtocolEnum.MQ.name().equals(protocol)) {
                    return new MqRemoteClient(config);
                }
                if (ProtocolEnum.A2A.name().equals(protocol)) {
                    bootstrapOfficialRemoteClient(protocol);
                    RemoteClientCreator creator = CUSTOM_REMOTE_CLIENTS.get(protocol);
                    if (creator != null) {
                        return creator.create(config);
                    }
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static RemoteClient resolveEntryPoint(String protocol, RemoteClientConfig config) {
        try {
            ServiceLoader<RemoteClientProvider> loader = ServiceLoader.load(RemoteClientProvider.class);
            for (RemoteClientProvider provider : loader) {
                if (!protocol.equals(normalizeProtocol(provider.name()))) {
                    continue;
                }
                try {
                    return provider.create(config);
                } catch (Exception ignored) {
                    return null;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String normalizeProtocol(String protocol) {
        return protocol.toUpperCase(Locale.ROOT);
    }

    @FunctionalInterface
    public interface RemoteClientCreator {
        RemoteClient create(RemoteClientConfig config);
    }

    public interface RemoteClientProvider {
        String name();

        RemoteClient create(RemoteClientConfig config);
    }
}
