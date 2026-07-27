/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registry for launchers and operation providers. */
public final class SandboxRegistry {
  private static final Map<String, Class<? extends SandboxLauncher>> LAUNCHERS =
      new ConcurrentHashMap<>();
  private static final Map<String, Map<String, Class<?>>> PROVIDERS = new ConcurrentHashMap<>();

  private SandboxRegistry() {}

  /** Auto-generated for codecheck compliance. */
  public static void registerLauncher(String name, Class<? extends SandboxLauncher> launcherClass) {
    LAUNCHERS.put(name, launcherClass);
  }

  /** Auto-generated for codecheck compliance. */
  public static Class<? extends SandboxLauncher> getLauncher(String name) {
    return LAUNCHERS.get(name);
  }

  /** Auto-generated for codecheck compliance. */
  public static void unregisterLauncher(String name) {
    LAUNCHERS.remove(name);
  }

  /** Auto-generated for codecheck compliance. */
  public static SandboxLauncher createLauncher(String name) {
    Class<? extends SandboxLauncher> launcherClass = getLauncher(name);
    if (launcherClass == null) {
      throw new IllegalArgumentException("Unknown launcher_type: " + name);
    }
    try {
      Constructor<? extends SandboxLauncher> constructor = launcherClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Failed to instantiate launcher_type: " + name, ex);
    }
  }

  /** Auto-generated for codecheck compliance. */
  public static void registerProvider(
      String sandboxType, String operationType, Class<?> providerClass) {
    PROVIDERS
        .computeIfAbsent(sandboxType, ignored -> new ConcurrentHashMap<>())
        .put(operationType, providerClass);
  }

  /** Auto-generated for codecheck compliance. */
  public static Class<?> getProviderClass(String sandboxType, String operationType) {
    return PROVIDERS.getOrDefault(sandboxType, Map.of()).get(operationType);
  }

  /** Auto-generated for codecheck compliance. */
  public static void unregisterProvider(String sandboxType, String operationType) {
    Map<String, Class<?>> providers = PROVIDERS.get(sandboxType);
    if (providers == null) {
      return;
    }
    providers.remove(operationType);
    if (providers.isEmpty()) {
      PROVIDERS.remove(sandboxType);
    }
  }

  /** Auto-generated for codecheck compliance. */
  public static Object createProvider(
      String sandboxType,
      String operationType,
      SandboxEndpoint endpoint,
      SandboxGatewayConfig config) {
    Class<?> providerClass = getProviderClass(sandboxType, operationType);
    if (providerClass == null) {
      throw new UnsupportedOperationException(
          "Sandbox type '" + sandboxType + "' does not support operation '" + operationType + "'");
    }
    try {
      Constructor<?> constructor =
          providerClass.getDeclaredConstructor(SandboxEndpoint.class, SandboxGatewayConfig.class);
      constructor.setAccessible(true);
      return constructor.newInstance(endpoint, config);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(
          "Failed to instantiate provider for sandbox type '"
              + sandboxType
              + "' and operation '"
              + operationType
              + "'",
          ex);
    }
  }
}
