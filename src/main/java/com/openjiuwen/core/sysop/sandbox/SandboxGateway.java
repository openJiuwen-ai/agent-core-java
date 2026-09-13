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

/**
 * Minimal singleton entry point for acquiring sandbox clients by key/config.
 * 
 * @version 1.0
 * @since 0.1.7
 */
public class SandboxGateway {
    private static final SandboxGateway INSTANCE = new SandboxGateway(GatewayConfig.builder().build());

    /**
     * ContainerManager.
     * 
     * @since 0.1.7
     */
    private final ContainerManager containerManager = new ContainerManager();
    private final GatewayConfig config;

    /**
     * java.util.concurrent.ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Object> providerCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * SandboxGateway.
     * 
     * @since 0.1.7
     */
    private SandboxGateway() {
        this(GatewayConfig.builder().build());
    }

    /**
     * SandboxGateway.
     * 
     * @param config config
     * @since 0.1.7
     */
    private SandboxGateway(GatewayConfig config) {
        this.config = config != null ? config : GatewayConfig.builder().build();
    }

    /**
     * Returns the singleton SandboxGateway instance.
     * 
     * @return the shared SandboxGateway instance
     * @since 0.1.7
     */
    public static SandboxGateway getInstance() {
        return INSTANCE;
    }

    static SandboxGateway createForTest() {
        return new SandboxGateway();
    }

    /**
     * Connect to a sandbox without an explicit isolation key.
     * 
     * @param config the sandbox gateway configuration
     * @return a SandboxClient for the acquired sandbox
     * @since 0.1.7
     */
    public SandboxClient connect(SandboxGatewayConfig config) {
        return containerManager.acquire(null, config);
    }

    /**
     * Connect to a sandbox with an explicit isolation key.
     * 
     * @param key the isolation key for sandbox identification, may be null
     * @param config the sandbox gateway configuration
     * @return a SandboxClient for the acquired sandbox
     * @since 0.1.7
     */
    public SandboxClient connect(String key, SandboxGatewayConfig config) {
        return containerManager.acquire(key, config);
    }

    /**
     * Disconnect and release a sandbox by its isolation key.
     * 
     * @param key the isolation key of the sandbox to release
     * @return true if the sandbox was successfully released, false otherwise
     * @since 0.1.7
     */
    public boolean disconnect(String key) {
        return containerManager.release(key);
    }

    /**
     * Returns the container manager for sandbox lifecycle management.
     * 
     * @return the ContainerManager instance
     * @since 0.1.7
     */
    public ContainerManager containerManager() {
        return containerManager;
    }

    /**
     * Handles an invocation request by routing it to the appropriate provider and method.
     * 
     * @param gatewayConfig the sandbox gateway configuration, may be null (defaults to empty config)
     * @param request the gateway invocation request containing isolation key, op type, and method
     * @return a GatewayResponse containing the invocation result or error information
     * @since 0.1.7
     */
    public GatewayResponse handleRequest(SandboxGatewayConfig gatewayConfig, GatewayInvokeRequest request) {
        try {
            SandboxGatewayConfig effectiveConfig =
                gatewayConfig != null ? gatewayConfig : SandboxGatewayConfig.builder().build();
            Object target = getOrCreateProvider(effectiveConfig, request.getIsolationKey(), request.getOpType());
            Object result = invokeByName(target, request.getMethod(), request.getParams());
            return GatewayResponse.builder().code(StatusCode.SUCCESS.getCode()).message(StatusCode.SUCCESS.getErrmsg())
                    .data(result).build();
        } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException
                | UnsupportedOperationException e) {
            Throwable cause = e;
            if (e instanceof ReflectiveOperationException && e.getCause() != null) {
                cause = e.getCause();
            }
            String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            return GatewayResponse.builder().code(StatusCode.ERROR.getCode()).message(msg).build();
        }
    }

    /**
     * Acquires a sandbox and returns its endpoint information.
     * 
     * @param request the sandbox create request containing isolation key and config
     * @return a GatewayResponse containing the SandboxEndpoint data (baseUrl, sandboxId)
     * @since 0.1.7
     */
    public GatewayResponse getSandbox(SandboxCreateRequest request) {
        SandboxGatewayConfig cfg = request != null && request.getConfig() != null
                ? request.getConfig()
                : SandboxGatewayConfig.builder().build();
        String key = request != null ? request.getIsolationKey() : null;
        connect(key, cfg);
        String resolvedKey =
            key != null && !key.isBlank() ? key : containerManager().keys().stream().findFirst().orElse("sandbox:.");
        Container container = containerManager().getContainer(resolvedKey);
        return GatewayResponse.builder().code(StatusCode.SUCCESS.getCode()).message(StatusCode.SUCCESS.getErrmsg())
                .data(SandboxEndpoint.builder()
                        .baseUrl(container != null ? container.getBaseUrl() : cfg.getGatewayUrl())
                        .sandboxId(container != null ? container.getSandboxId() : resolvedKey).build())
                .build();
    }

    /**
     * Releases a sandbox by its isolation key and optionally triggers an on-stop action.
     * 
     * @param isolationKey the isolation key of the sandbox to release
     * @param onStop the stop action (e.g., "delete"), may be null (defaults to "delete")
     * @return a GatewayResponse indicating success or failure of the release operation
     * @since 0.1.7
     */
    public GatewayResponse releaseSandbox(String isolationKey, String onStop) {
        evictProviderCache(isolationKey);
        boolean isReleased = containerManager.release(isolationKey, onStop != null ? onStop : "delete");
        return GatewayResponse.builder().code(isReleased ? StatusCode.SUCCESS.getCode() : StatusCode.ERROR.getCode())
                .message(isReleased ? StatusCode.SUCCESS.getErrmsg() : "Sandbox record not found").data(isReleased)
                .build();
    }

    /**
     * getOrCreateProvider.
     * 
     * @param config config
     * @param isolationKey isolationKey
     * @param opType opType
     * @return the result
     * @since 0.1.7
     */
    private Object getOrCreateProvider(SandboxGatewayConfig config, String isolationKey, String opType) {
        SandboxGatewayConfig effectiveConfig = config != null ? config : SandboxGatewayConfig.builder().build();
        String key = (isolationKey != null ? isolationKey : "") + ":" + opType;
        return providerCache.computeIfAbsent(key, ignored -> {
            connect(isolationKey, effectiveConfig);
            SandboxEndpoint endpoint = endpointFor(isolationKey, effectiveConfig);
            String sandboxType = resolveSandboxType(effectiveConfig);
            return SandboxRegistry.createProvider(sandboxType, opType, endpoint, effectiveConfig);
        });
    }

    /**
     * endpointFor.
     * 
     * @param isolationKey isolationKey
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private SandboxEndpoint endpointFor(String isolationKey, SandboxGatewayConfig config) {
        String resolvedKey = isolationKey != null && !isolationKey.isBlank()
                ? isolationKey
                : containerManager().keys().stream().findFirst().orElse("sandbox:.");
        Container container = containerManager().getContainer(resolvedKey);
        return SandboxEndpoint.builder().baseUrl(container != null ? container.getBaseUrl() : config.getGatewayUrl())
                .sandboxId(container != null ? container.getSandboxId() : resolvedKey).build();
    }

    /**
     * resolveSandboxType.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * evictProviderCache.
     * 
     * @param isolationKey isolationKey
     * @since 0.1.7
     */
    private void evictProviderCache(String isolationKey) {
        if (isolationKey == null || isolationKey.isBlank()) {
            return;
        }
        providerCache.keySet().removeIf(key -> key.startsWith(isolationKey + ":"));
    }

    /**
     * invokeByName.
     * 
     * @param target target
     * @param methodName methodName
     * @param params params
     * @return the result
     * @throws ReflectiveOperationException ReflectiveOperationException
     * @since 0.1.7
     */
    private Object invokeByName(Object target, String methodName, Map<String, Object> params)
            throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName);
        if (method == null) {
            throw new NoSuchMethodException("Method '" + methodName + "' not found on provider");
        }
        Object[] args = buildArguments(method, params != null ? params : Map.of());
        return method.invoke(target, args);
    }

    /**
     * findMethod.
     * 
     * @param type type
     * @param methodName methodName
     * @return the result
     * @since 0.1.7
     */
    private Method findMethod(Class<?> type, String methodName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    /**
     * buildArguments.
     * 
     * @param method method
     * @param params params
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * defaultValue.
     * 
     * @param type type
     * @return the result
     * @since 0.1.7
     */
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
    /**
     * coerceValue.
     * 
     * @param value value
     * @param type type
     * @return the result
     * @since 0.1.7
     */
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
        if ((type == boolean.class || type == Boolean.class) && value instanceof Boolean isBool) {
            return isBool;
        }
        if (type == String.class) {
            return String.valueOf(value);
        }
        return value;
    }
}
