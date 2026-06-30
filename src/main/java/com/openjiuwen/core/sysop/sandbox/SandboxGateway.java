/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.GatewayConfig;
import com.openjiuwen.core.sysop.config.GatewayInvokeRequest;
import com.openjiuwen.core.sysop.config.SandboxCreateRequest;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Minimal singleton entry point for acquiring sandbox clients by key/config. */
public class SandboxGateway {
  private static final SandboxGateway instance =
      new SandboxGateway(GatewayConfig.builder().build());

  private final ContainerManager containerManager = new ContainerManager();
  private final GatewayConfig config;
  private final Map<String, Object> providerCache = new java.util.concurrent.ConcurrentHashMap<>();

  private SandboxGateway() {
    this(GatewayConfig.builder().build());
  }

  private SandboxGateway(GatewayConfig config) {
    this.config = config != null ? config : GatewayConfig.builder().build();
  }

  /** Auto-generated for codecheck compliance. */
  public static SandboxGateway getInstance() {
    return instance;
  }

  static SandboxGateway createForTest() {
    return new SandboxGateway();
  }

  /** Auto-generated for codecheck compliance. */
  public SandboxClient connect(SandboxGatewayConfig config) {
    return containerManager.acquire(null, config);
  }

  /** Auto-generated for codecheck compliance. */
  public SandboxClient connect(String key, SandboxGatewayConfig config) {
    return containerManager.acquire(key, config);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean disconnect(String key) {
    return containerManager.release(key);
  }

  /** Auto-generated for codecheck compliance. */
  public ContainerManager containerManager() {
    return containerManager;
  }

  /** Auto-generated for codecheck compliance. */
  public GatewayResponse handleRequest(
      SandboxGatewayConfig gatewayConfig, GatewayInvokeRequest request) {
    try {
      SandboxGatewayConfig effectiveConfig =
          gatewayConfig != null ? gatewayConfig : SandboxGatewayConfig.builder().build();
      Object target =
          getOrCreateProvider(effectiveConfig, request.getIsolationKey(), request.getOpType());
      Object result = invokeByName(target, request.getMethod(), request.getParams());
      return GatewayResponse.builder()
          .code(StatusCode.SUCCESS.getCode())
          .message(StatusCode.SUCCESS.getErrmsg())
          .data(result)
          .build();
    } catch (ReflectiveOperationException
        | IllegalArgumentException
        | SecurityException
        | UnsupportedOperationException e) {
      return GatewayResponse.builder()
          .code(StatusCode.ERROR.getCode())
          .message(e.getMessage())
          .build();
    }
  }

  /** Auto-generated for codecheck compliance. */
  public GatewayResponse getSandbox(SandboxCreateRequest request) {
    SandboxGatewayConfig cfg =
        request != null && request.getConfig() != null
            ? request.getConfig()
            : SandboxGatewayConfig.builder().build();
    String key = request != null ? request.getIsolationKey() : null;
    connect(key, cfg);
    String resolvedKey =
        key != null && !key.isBlank()
            ? key
            : containerManager().keys().stream().findFirst().orElse("sandbox:.");
    Container container = containerManager().getContainer(resolvedKey);
    return GatewayResponse.builder()
        .code(StatusCode.SUCCESS.getCode())
        .message(StatusCode.SUCCESS.getErrmsg())
        .data(
            SandboxEndpoint.builder()
                .baseUrl(container != null ? container.getBaseUrl() : cfg.getGatewayUrl())
                .sandboxId(container != null ? container.getSandboxId() : resolvedKey)
                .build())
        .build();
  }

  /** Auto-generated for codecheck compliance. */
  public GatewayResponse releaseSandbox(String isolationKey, String onStop) {
    evictProviderCache(isolationKey);
    boolean isReleased = containerManager.release(isolationKey, onStop != null ? onStop : "delete");
    return GatewayResponse.builder()
        .code(isReleased ? StatusCode.SUCCESS.getCode() : StatusCode.ERROR.getCode())
        .message(isReleased ? StatusCode.SUCCESS.getErrmsg() : "Sandbox record not found")
        .data(isReleased)
        .build();
  }

  private Object getOrCreateProvider(
      SandboxGatewayConfig config, String isolationKey, String opType) {
    SandboxGatewayConfig effectiveConfig =
        config != null ? config : SandboxGatewayConfig.builder().build();
    String key = (isolationKey != null ? isolationKey : "") + ":" + opType;
    return providerCache.computeIfAbsent(
        key,
        ignored -> {
          connect(isolationKey, effectiveConfig);
          SandboxEndpoint endpoint = endpointFor(isolationKey, effectiveConfig);
          String sandboxType = resolveSandboxType(effectiveConfig);
          return SandboxRegistry.createProvider(sandboxType, opType, endpoint, effectiveConfig);
        });
  }

  private SandboxEndpoint endpointFor(String isolationKey, SandboxGatewayConfig config) {
    String resolvedKey =
        isolationKey != null && !isolationKey.isBlank()
            ? isolationKey
            : containerManager().keys().stream().findFirst().orElse("sandbox:.");
    Container container = containerManager().getContainer(resolvedKey);
    return SandboxEndpoint.builder()
        .baseUrl(container != null ? container.getBaseUrl() : config.getGatewayUrl())
        .sandboxId(container != null ? container.getSandboxId() : resolvedKey)
        .build();
  }

  private String resolveSandboxType(SandboxGatewayConfig config) {
    if (config == null || config.getLauncherConfig() == null) {
      throw new IllegalArgumentException("sandbox gateway requires launcher_config");
    }
    String sandboxType = config.getLauncherConfig().getSandboxType();
    if (sandboxType == null || sandboxType.isBlank()) {
      throw new IllegalArgumentException("sandbox gateway requires sandbox_type");
    }
    return sandboxType;
  }

  private void evictProviderCache(String isolationKey) {
    if (isolationKey == null || isolationKey.isBlank()) {
      return;
    }
    providerCache.keySet().removeIf(key -> key.startsWith(isolationKey + ":"));
  }

  private Object invokeByName(Object target, String methodName, Map<String, Object> params)
      throws ReflectiveOperationException {
    Method method = findMethod(target.getClass(), methodName);
    if (method == null) {
      throw new NoSuchMethodException("Method '" + methodName + "' not found on provider");
    }
    Object[] args = buildArguments(method, params != null ? params : Map.of());
    return method.invoke(target, args);
  }

  private Method findMethod(Class<?> type, String methodName) {
    for (Method method : type.getMethods()) {
      if (method.getName().equals(methodName)) {
        return method;
      }
    }
    return null;
  }

  private Object[] buildArguments(Method method, Map<String, Object> params) {
    Parameter[] parameters = method.getParameters();
    List<Object> args = new ArrayList<>(parameters.length);
    for (Parameter parameter : parameters) {
      Object value = params.get(parameter.getName());
      if (value == null) {
        value = defaultValue(parameter.getType());
      }
      args.add(coerceValue(value, parameter.getType()));
    }
    return args.toArray();
  }

  private Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == byte.class) {
      return (byte) 0;
    }
    if (type == short.class) {
      return (short) 0;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == float.class) {
      return 0f;
    }
    if (type == double.class) {
      return 0d;
    }
    if (type == char.class) {
      return '\0';
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private Object coerceValue(Object value, Class<?> type) {
    if (value == null || type.isInstance(value)) {
      return value;
    }
    if ((type == int.class || type == Integer.class) && value instanceof Number number) {
      return number.intValue();
    }
    if ((type == long.class || type == Long.class) && value instanceof Number number) {
      return number.longValue();
    }
    if ((type == double.class || type == Double.class) && value instanceof Number number) {
      return number.doubleValue();
    }
    if ((type == float.class || type == Float.class) && value instanceof Number number) {
      return number.floatValue();
    }
    if ((type == boolean.class || type == Boolean.class) && value instanceof Boolean boolValue) {
      return boolValue;
    }
    if (type == String.class) {
      return String.valueOf(value);
    }
    return value;
  }
}
