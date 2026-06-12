/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox;

import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sys_operation.sandbox.launchers.SandboxLauncher;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for sandbox launchers and operation providers.
 *
 * <p>Mirrors Python's {@code SandboxRegistry} in
 * {@code openjiuwen/core/sys_operation/sandbox/sandbox_registry.py}.</p>
 */
public final class SandboxRegistry {

    private static final Map<String, Class<? extends SandboxLauncher>> LAUNCHERS = new LinkedHashMap<>();

    private static final Map<String, Map<String, Class<?>>> OPERATIONS = new LinkedHashMap<>();

    private SandboxRegistry() {
    }

    public static synchronized void registerLauncher(String name, Class<? extends SandboxLauncher> launcherClass) {
        LAUNCHERS.put(name, launcherClass);
    }

    public static synchronized Class<? extends SandboxLauncher> getLauncherCls(String name) {
        return LAUNCHERS.get(name);
    }

    public static synchronized void unregisterLauncher(String name) {
        LAUNCHERS.remove(name);
    }

    public static SandboxLauncher createLauncher(String launcherType) {
        Class<? extends SandboxLauncher> launcherClass = getLauncherCls(launcherType);
        if (launcherClass == null) {
            throw new IllegalArgumentException("Unknown launcher_type: " + launcherType);
        }
        return instantiateLauncher(launcherClass);
    }

    public static synchronized <T extends SandboxLauncher> Class<T> launcher(String name, Class<T> launcherClass) {
        registerLauncher(name, launcherClass);
        return launcherClass;
    }

    public static synchronized void registerProvider(String sandboxType, String operationType, Class<?> providerClass) {
        OPERATIONS.computeIfAbsent(sandboxType, ignored -> new LinkedHashMap<>()).put(operationType, providerClass);
    }

    public static synchronized Class<?> getProviderCls(String sandboxType, String operationType) {
        Map<String, Class<?>> providers = OPERATIONS.get(sandboxType);
        return providers == null ? null : providers.get(operationType);
    }

    public static synchronized void unregisterProvider(String sandboxType, String operationType) {
        Map<String, Class<?>> providers = OPERATIONS.get(sandboxType);
        if (providers == null) {
            return;
        }
        providers.remove(operationType);
        if (providers.isEmpty()) {
            OPERATIONS.remove(sandboxType);
        }
    }

    public static Object createProvider(
            String sandboxType,
            String operationType,
            SandboxEndpoint endpoint,
            SandboxGatewayConfig config) {
        Class<?> providerClass = getProviderCls(sandboxType, operationType);
        if (providerClass == null) {
            throw new UnsupportedOperationException(
                    "Sandbox type '" + sandboxType + "' does not support operation '" + operationType + "'");
        }
        return instantiateProvider(providerClass, endpoint, config);
    }

    public static synchronized Class<?> provider(String sandboxType, String operationType, Class<?> providerClass) {
        registerProvider(sandboxType, operationType, providerClass);
        return providerClass;
    }

    private static SandboxLauncher instantiateLauncher(Class<? extends SandboxLauncher> launcherClass) {
        try {
            Constructor<? extends SandboxLauncher> constructor = launcherClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to instantiate launcher " + launcherClass.getName(), ex);
        }
    }

    private static Object instantiateProvider(
            Class<?> providerClass,
            SandboxEndpoint endpoint,
            SandboxGatewayConfig config) {
        for (Constructor<?> constructor : providerClass.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            try {
                constructor.setAccessible(true);
                if (parameterTypes.length == 2
                        && parameterTypes[0].isAssignableFrom(SandboxEndpoint.class)
                        && parameterTypes[1].isAssignableFrom(SandboxGatewayConfig.class)) {
                    return constructor.newInstance(endpoint, config);
                }
                if (parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(SandboxEndpoint.class)) {
                    return constructor.newInstance(endpoint);
                }
                if (parameterTypes.length == 0) {
                    return constructor.newInstance();
                }
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                throw new IllegalStateException("Failed to instantiate provider " + providerClass.getName(), ex);
            }
        }
        throw new IllegalStateException(
                "Provider " + providerClass.getName() + " must expose a SandboxEndpoint constructor");
    }
}
