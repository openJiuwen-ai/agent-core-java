/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.launchers.SandboxLauncher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registry for sandbox launchers and operation providers.
 * <p>
 * Manages registration and creation of launcher instances and operation providers
 * for different sandbox types.
 * <p>
 * Mirrors Python's {@code SandboxRegistry} in {@code sandbox/sandbox_registry.py}.
 */
public final class SandboxRegistry {

    /** Storage: launcher name -> Supplier<SandboxLauncher>. */
    private static final Map<String, Supplier<SandboxLauncher>> LAUNCHERS = new ConcurrentHashMap<>();

    /** Storage: sandbox_type -> operation_type -> ProviderFactory. */
    private static final Map<String, Map<String, ProviderFactory>> OPERATIONS = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface ProviderFactory {
        Object create(SandboxEndpoint endpoint, Object config);
    }

    private SandboxRegistry() {
        // Singleton utility class
    }

    /**
     * Register a launcher class by name.
     *
     * @param name            unique launcher name (e.g., "pre_deploy", "docker")
     * @param launcherSupplier supplier that creates new launcher instances
     */
    public static void registerLauncher(String name, Supplier<SandboxLauncher> launcherSupplier) {
        LAUNCHERS.put(name, launcherSupplier);
    }

    /**
     * Get the launcher supplier by name.
     *
     * @param name the launcher name
     * @return the launcher supplier, or null if not found
     */
    public static Supplier<SandboxLauncher> getLauncherSupplier(String name) {
        return LAUNCHERS.get(name);
    }

    /**
     * Unregister a launcher by name.
     *
     * @param name the launcher name
     */
    public static void unregisterLauncher(String name) {
        LAUNCHERS.remove(name);
    }

    /**
     * Create a new launcher instance by type.
     *
     * @param launcherType the launcher type name
     * @return a new launcher instance
     * @throws IllegalArgumentException if launcher type is unknown
     */
    public static SandboxLauncher createLauncher(String launcherType) {
        Supplier<SandboxLauncher> supplier = LAUNCHERS.get(launcherType);
        if (supplier == null) {
            throw new IllegalArgumentException("Unknown launcher_type: " + launcherType);
        }
        return supplier.get();
    }

    /**
     * Register an operation provider for a sandbox type and operation type.
     *
     * @param sandboxType      the sandbox type (e.g., "aio", "e2b", "mock")
     * @param operationType    the operation type (e.g., "fs", "shell", "code")
     * @param providerSupplier supplier that creates provider instances
     */
    public static void registerProvider(String sandboxType, String operationType, Supplier<Object> providerSupplier) {
        registerProvider(sandboxType, operationType, (endpoint, config) -> providerSupplier.get());
    }

    /**
     * Register an operation provider factory for a sandbox type and operation type.
     *
     * @param sandboxType      the sandbox type (e.g., "aio", "e2b", "mock")
     * @param operationType    the operation type (e.g., "fs", "shell", "code")
     * @param providerFactory  factory that receives endpoint/config and creates provider instances
     */
    public static void registerProvider(String sandboxType, String operationType, ProviderFactory providerFactory) {
        OPERATIONS.computeIfAbsent(sandboxType, k -> new ConcurrentHashMap<>())
                .put(operationType, providerFactory);
    }

    /**
     * Get the provider supplier for a sandbox type and operation type.
     *
     * @param sandboxType   the sandbox type
     * @param operationType the operation type
     * @return the provider supplier, or null if not found
     */
    public static Supplier<Object> getProviderSupplier(String sandboxType, String operationType) {
        Map<String, ProviderFactory> typeOps = OPERATIONS.get(sandboxType);
        if (typeOps == null) {
            return null;
        }
        ProviderFactory factory = typeOps.get(operationType);
        return factory == null ? null : () -> factory.create(null, null);
    }

    /**
     * Unregister a provider for a sandbox type and operation type.
     *
     * @param sandboxType   the sandbox type
     * @param operationType the operation type
     */
    public static void unregisterProvider(String sandboxType, String operationType) {
        Map<String, ProviderFactory> typeOps = OPERATIONS.get(sandboxType);
        if (typeOps != null) {
            typeOps.remove(operationType);
            if (typeOps.isEmpty()) {
                OPERATIONS.remove(sandboxType);
            }
        }
    }

    /**
     * Create a provider instance for the given sandbox and operation type.
     *
     * @param sandboxType   the sandbox type
     * @param operationType the operation type
     * @param endpoint      the sandbox endpoint to connect to
     * @param config        optional configuration object
     * @return a new provider instance
     * @throws UnsupportedOperationException if provider type is not registered
     */
    public static Object createProvider(String sandboxType, String operationType, 
                                         SandboxEndpoint endpoint, Object config) {
        Supplier<Object> supplier = getProviderSupplier(sandboxType, operationType);
        Map<String, ProviderFactory> typeOps = OPERATIONS.get(sandboxType);
        ProviderFactory factory = typeOps != null ? typeOps.get(operationType) : null;
        if (factory == null) {
            throw new UnsupportedOperationException(
                    "No provider registered for sandbox_type=" + sandboxType + 
                    ", operation_type=" + operationType);
        }
        return factory.create(endpoint, config);
    }

    /**
     * Clear all registered launchers and providers.
     * <p>
     * Useful for testing or resetting state.
     */
    public static void clear() {
        LAUNCHERS.clear();
        OPERATIONS.clear();
    }
}
