/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.GatewayConfig;
import com.openjiuwen.core.sysop.config.GatewayInvokeRequest;
import com.openjiuwen.core.sysop.config.SandboxCreateRequest;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.sandbox.SandboxRegistry;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioCodeProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioFsProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioShellProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxCodeProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxFsProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxShellProvider;
import com.openjiuwen.core.sysop.sandbox.launchers.LaunchedSandbox;
import com.openjiuwen.core.sysop.sandbox.launchers.PreDeploymentLauncher;
import com.openjiuwen.core.sysop.sandbox.launchers.SandboxLauncher;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mirrors Python's {@code SandboxGateway} in
 * {@code openjiuwen/core/sys_operation/sandbox/gateway/gateway.py}.
 */
public class SandboxGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final ObjectMapper HASH_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private static final AtomicBoolean BUILTIN_PROVIDERS_REGISTERED = new AtomicBoolean(false);

    private static SandboxGateway instance;

    private final GatewayConfig config;

    private final Map<String, Object> providerCache = new ConcurrentHashMap<>();

    private final AbstractSandboxStore store;

    public SandboxGateway() {
        this(null, new InMemorySandboxStore());
    }

    public SandboxGateway(GatewayConfig config) {
        this(config, new InMemorySandboxStore());
    }

    SandboxGateway(GatewayConfig config, AbstractSandboxStore store) {
        this.config = config == null ? GatewayConfig.builder().build() : config;
        this.store = store;
        registerBuiltinLaunchers();
        registerBuiltinProviders();
    }

    public static synchronized SandboxGateway getInstance(GatewayConfig config) {
        if (instance == null) {
            instance = new SandboxGateway(config);
        }
        return instance;
    }

    static void registerBuiltinLaunchers() {
        SandboxRegistry.registerLauncher("pre_deploy", PreDeploymentLauncher.class);
    }

    static void registerBuiltinProviders() {
        if (!BUILTIN_PROVIDERS_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        SandboxRegistry.registerProvider("aio", "fs", AioFsProvider.class);
        SandboxRegistry.registerProvider("aio", "shell", AioShellProvider.class);
        SandboxRegistry.registerProvider("aio", "code", AioCodeProvider.class);
        SandboxRegistry.registerProvider("jiuwenbox", "fs", JiuwenBoxFsProvider.class);
        SandboxRegistry.registerProvider("jiuwenbox", "shell", JiuwenBoxShellProvider.class);
        SandboxRegistry.registerProvider("jiuwenbox", "code", JiuwenBoxCodeProvider.class);
    }

    public CompletableFuture<GatewayResponse> handleRequest(
            SandboxGatewayConfig config,
            GatewayInvokeRequest request) {
        return getOrCreateProvider(config, request.getIsolationKey(), request.getOpType())
                .thenCompose(provider -> invokeRequest(provider, request))
                .exceptionally(error -> errorResponse(rootCauseMessage(error), null));
    }

    public CompletableFuture<Flow.Publisher<?>> handleStreamRequest(
            SandboxGatewayConfig config,
            GatewayInvokeRequest request) {
        return getOrCreateProvider(config, request.getIsolationKey(), request.getOpType())
                .thenCompose(provider -> invokeStreamRequest(provider, request));
    }

    CompletableFuture<Object> getOrCreateProvider(
            SandboxGatewayConfig config,
            String isolationKey,
            String opType) {
        String cacheKey = cacheKey(isolationKey, opType);
        Object cached = providerCache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return getEndpoint(config, isolationKey).thenApply(endpoint -> {
            Object provider = SandboxRegistry.createProvider(
                    config.getLauncherConfig().getSandboxType(),
                    opType,
                    endpoint,
                    config);
            providerCache.put(cacheKey, provider);
            return provider;
        });
    }

    void evictProviderCache(String isolationKey) {
        String prefix = String.valueOf(isolationKey) + ":";
        List<String> keysToRemove = new ArrayList<>();
        for (String key : providerCache.keySet()) {
            if (key.startsWith(prefix)) {
                keysToRemove.add(key);
            }
        }
        keysToRemove.forEach(providerCache::remove);
    }

    public CompletableFuture<GatewayResponse> getSandbox(SandboxCreateRequest request) {
        return getEndpoint(request.getConfig(), request.getIsolationKey())
                .thenApply(endpoint -> successResponse(endpoint))
                .exceptionally(error -> errorResponse(rootCauseMessage(error), null));
    }

    public CompletableFuture<GatewayResponse> releaseSandbox(String isolationKey, String onStop) {
        evictProviderCache(isolationKey);
        Optional<SandboxRecord> record = store.hdel(isolationKey);
        if (record.isEmpty()) {
            return CompletableFuture.completedFuture(errorResponse("Sandbox record not found", Boolean.FALSE));
        }
        CompletableFuture<Void> releaseFuture;
        if ("keep".equals(onStop)) {
            releaseFuture = CompletableFuture.completedFuture(null);
        } else if ("pause".equals(onStop)) {
            releaseFuture = pauseSandbox(record.get());
        } else {
            releaseFuture = deleteSandbox(record.get());
        }
        return releaseFuture.handle((unused, error) -> {
            if (error != null) {
                return errorResponse(rootCauseMessage(error), Boolean.FALSE);
            }
            return successResponse(Boolean.TRUE);
        });
    }

    public CompletableFuture<Void> pauseSandbox(SandboxRecord record) {
        SandboxLauncher launcher = SandboxRegistry.createLauncher(record.getLauncherType());
        return launcher.pause(record.getSandboxId());
    }

    public CompletableFuture<Void> deleteSandbox(SandboxRecord record) {
        SandboxLauncher launcher = SandboxRegistry.createLauncher(record.getLauncherType());
        return launcher.delete(record.getSandboxId());
    }

    public CompletableFuture<SandboxEndpoint> getEndpoint(
            SandboxGatewayConfig config,
            String isolationKey) {
        SandboxLauncherConfig launcherConfig = Objects.requireNonNull(config.getLauncherConfig(), "launcherConfig");
        String launcherType = launcherConfig.getLauncherType();
        String key = isolationKey;
        double now = currentEpochSeconds();

        Optional<SandboxRecord> cachedRecord = store.get(key);
        if (cachedRecord.isPresent() && cachedRecord.get().getStatus() == SandboxStatus.RUNNING) {
            SandboxRecord record = cachedRecord.get();
            record.setLastUsedTs(now);
            return CompletableFuture.completedFuture(new SandboxEndpoint(record.getBaseUrl(), record.getSandboxId()));
        }

        SandboxRecord record = store.get(key).orElse(null);
        if (record == null) {
            return createNewSandbox(key, now, config)
                    .thenApply(launched -> new SandboxEndpoint(launched.baseUrl(), launched.sandboxId()));
        }

        SandboxLauncher launcher = SandboxRegistry.createLauncher(launcherType);
        return launcher.checkStatus(record.getSandboxId()).thenCompose(realStatus -> {
            if (realStatus == SandboxStatus.RUNNING) {
                record.setStatus(SandboxStatus.RUNNING);
                record.setLastUsedTs(now);
                store.set(key, record);
                return CompletableFuture.completedFuture(new SandboxEndpoint(record.getBaseUrl(), record.getSandboxId()));
            }
            if (realStatus == SandboxStatus.PAUSED) {
                return launcher.resume(record.getSandboxId()).thenApply(unused -> {
                    record.setStatus(SandboxStatus.RUNNING);
                    record.setLastUsedTs(now);
                    store.set(key, record);
                    return new SandboxEndpoint(record.getBaseUrl(), record.getSandboxId());
                });
            }
            store.hdel(key);
            return createNewSandbox(key, now, config)
                    .thenApply(launched -> new SandboxEndpoint(launched.baseUrl(), launched.sandboxId()));
        });
    }

    CompletableFuture<LaunchedSandbox> createNewSandbox(String key, double now, SandboxGatewayConfig config) {
        SandboxLauncherConfig launcherConfig = Objects.requireNonNull(config.getLauncherConfig(), "launcherConfig");
        return evictIdle(now, config).thenCompose(unused -> {
            SandboxLauncher launcher = SandboxRegistry.createLauncher(launcherConfig.getLauncherType());
            return launcher.launch(launcherConfig, config.getTimeoutSeconds(), key).thenApply(launched -> {
                SandboxRecord record = new SandboxRecord(
                        launched.sandboxId(),
                        launched.baseUrl(),
                        SandboxStatus.RUNNING,
                        launcherConfig.getLauncherType(),
                        launcherConfig.getSandboxType(),
                        computeContainerConfigHash(launcherConfig));
                record.setLastUsedTs(now);
                store.set(key, record);
                return launched;
            });
        });
    }

    CompletableFuture<Void> evictIdle(double now, SandboxGatewayConfig config) {
        Integer idleTtl = config.getLauncherConfig().getIdleTtlSeconds();
        if (idleTtl == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (SandboxRecord record : store.evictExpired(idleTtl, now)) {
            chain = chain.thenCompose(unused -> {
                SandboxLauncher launcher = SandboxRegistry.createLauncher(record.getLauncherType());
                return launcher.delete(record.getSandboxId());
            });
        }
        return chain;
    }

    static String computeContainerConfigHash(SandboxLauncherConfig config) {
        if (config == null) {
            return "none";
        }
        Map<String, Object> containerFields = new LinkedHashMap<>();
        containerFields.put("image", readProperty(config, "image"));
        containerFields.put("env", sortMap(readProperty(config, "env")));
        containerFields.put("volumes", sortList(readProperty(config, "volumes")));
        containerFields.put("resource_limits", readProperty(config, "resourceLimits"));
        containerFields.put("network", readProperty(config, "network"));
        containerFields.put("service_port", readProperty(config, "servicePort"));
        try {
            String payload = HASH_MAPPER.writeValueAsString(containerFields);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                builder.append(String.format("%02x", hash[index]));
            }
            return builder.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to compute container config hash", ex);
        }
    }

    private CompletableFuture<GatewayResponse> invokeRequest(Object provider, GatewayInvokeRequest request) {
        Method handler = findHandler(provider.getClass(), request.getMethod(), request.getParams());
        if (handler == null) {
            return CompletableFuture.completedFuture(errorResponse(
                    "Method '" + request.getMethod() + "' not found on provider",
                    null));
        }
        try {
            Object result = invokeHandler(provider, handler, request.getParams());
            if (result instanceof CompletableFuture<?> future) {
                return future.handle((value, error) -> {
                    if (error != null) {
                        return errorResponse(rootCauseMessage(error), null);
                    }
                    return successResponse(value);
                });
            }
            return CompletableFuture.completedFuture(successResponse(result));
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(errorResponse(rootCauseMessage(ex), null));
        }
    }

    private CompletableFuture<Flow.Publisher<?>> invokeStreamRequest(Object provider, GatewayInvokeRequest request) {
        Method handler = findHandler(provider.getClass(), request.getMethod(), request.getParams());
        if (handler == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Method '" + request.getMethod() + "' not found on provider"));
        }
        try {
            Object result = invokeHandler(provider, handler, request.getParams());
            if (result instanceof CompletableFuture<?> future) {
                return future.thenApply(stream -> castPublisher(stream, request.getMethod()));
            }
            return CompletableFuture.completedFuture(castPublisher(result, request.getMethod()));
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    private static Flow.Publisher<?> castPublisher(Object stream, String methodName) {
        if (stream instanceof Flow.Publisher<?> publisher) {
            return publisher;
        }
        throw new IllegalStateException("Method '" + methodName + "' did not return a Flow.Publisher");
    }

    private static Method findHandler(Class<?> providerClass, String methodName, Map<String, Object> params) {
        Method selected = null;
        int selectedScore = Integer.MAX_VALUE;
        for (Method method : providerClass.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            int score = methodScore(method, params);
            if (selected == null
                    || method.getParameterCount() > selected.getParameterCount()
                    || method.getParameterCount() == selected.getParameterCount() && score < selectedScore) {
                selected = method;
                selectedScore = score;
            }
        }
        return selected;
    }

    private static int methodScore(Method method, Map<String, Object> params) {
        Parameter[] parameters = method.getParameters();
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        int score = 0;
        for (Parameter parameter : parameters) {
            score += conversionScore(safeParams.get(parameter.getName()), parameter.getType());
        }
        return score;
    }

    private static int conversionScore(Object value, Class<?> targetType) {
        if (value == null) {
            return targetType.isPrimitive() ? 3 : 1;
        }
        if (targetType.isInstance(value)) {
            return 0;
        }
        if (targetType.isPrimitive()) {
            return primitiveConversionScore(value, targetType);
        }
        if (targetType == String.class) {
            return value instanceof CharSequence ? 0 : 8;
        }
        if (Number.class.isAssignableFrom(targetType) && value instanceof Number) {
            return 1;
        }
        if (targetType == Boolean.class && value instanceof Boolean) {
            return 0;
        }
        if (targetType.isEnum() && value instanceof String) {
            return 2;
        }
        if (targetType == byte[].class) {
            return value instanceof byte[] ? 0 : 20;
        }
        return 10;
    }

    private static int primitiveConversionScore(Object value, Class<?> targetType) {
        if (targetType == boolean.class) {
            return value instanceof Boolean ? 0 : 8;
        }
        if (targetType == char.class) {
            return value instanceof Character ? 0 : 8;
        }
        return value instanceof Number ? 1 : 8;
    }

    private static Object invokeHandler(Object provider, Method handler, Map<String, Object> params) {
        try {
            Parameter[] parameters = handler.getParameters();
            Object[] arguments = new Object[parameters.length];
            Map<String, Object> safeParams = params == null ? Map.of() : params;
            for (int index = 0; index < parameters.length; index++) {
                Parameter parameter = parameters[index];
                arguments[index] = convertArgument(safeParams.get(parameter.getName()), parameter.getType());
            }
            return handler.invoke(provider, arguments);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to invoke provider method " + handler.getName(), ex);
        }
    }

    private static Object convertArgument(Object value, Class<?> targetType) {
        if (value == null) {
            if (targetType.isPrimitive()) {
                if (targetType == boolean.class) {
                    return false;
                }
                if (targetType == char.class) {
                    return '\0';
                }
                return 0;
            }
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
        }
        if (targetType == Long.class || targetType == long.class) {
            return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
        }
        if (targetType == Double.class || targetType == double.class) {
            return value instanceof Number number ? number.doubleValue() : Double.parseDouble(value.toString());
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return value instanceof Boolean bool ? bool : Boolean.parseBoolean(value.toString());
        }
        if (targetType.isEnum() && value instanceof String text) {
            Object fromValue = invokeEnumFromValue(targetType, text);
            if (fromValue != null) {
                return fromValue;
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object enumValue = Enum.valueOf(
                    (Class<? extends Enum>) targetType.asSubclass(Enum.class),
                    text.trim().toUpperCase());
            return enumValue;
        }
        return OBJECT_MAPPER.convertValue(value, targetType);
    }

    private static Object invokeEnumFromValue(Class<?> enumType, String text) {
        try {
            Method fromValue = enumType.getMethod("fromValue", String.class);
            return fromValue.invoke(null, text);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String cacheKey(String isolationKey, String opType) {
        return String.valueOf(isolationKey) + ":" + opType;
    }

    private static GatewayResponse successResponse(Object data) {
        return new GatewayResponse(StatusCode.SUCCESS.getCode(), StatusCode.SUCCESS.getErrmsg(), data);
    }

    private static GatewayResponse errorResponse(String message, Object data) {
        return new GatewayResponse(StatusCode.ERROR.getCode(), message, data);
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.toString() : cursor.getMessage();
    }

    private static double currentEpochSeconds() {
        return System.currentTimeMillis() / 1000.0d;
    }

    private static Object readProperty(Object source, String propertyName) {
        if (source == null) {
            return null;
        }
        String capitalized = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        for (String accessor : List.of("get" + capitalized, "is" + capitalized)) {
            try {
                Method method = source.getClass().getMethod(accessor);
                return method.invoke(source);
            } catch (NoSuchMethodException ignored) {
                // Try next accessor or field fallback.
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new IllegalStateException("Failed to read property " + propertyName, ex);
            }
        }
        try {
            Field field = source.getClass().getDeclaredField(propertyName);
            field.setAccessible(true);
            return field.get(source);
        } catch (NoSuchFieldException ignored) {
            return null;
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Failed to read property " + propertyName, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sortMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Object> sorted = new LinkedHashMap<>();
        rawMap.entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .forEach(entry -> sorted.put(String.valueOf(entry.getKey()), entry.getValue()));
        return sorted;
    }

    private static List<Object> sortList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        return rawList.stream()
                .sorted(Comparator.comparing(String::valueOf))
                .map(item -> (Object) item)
                .toList();
    }
}
