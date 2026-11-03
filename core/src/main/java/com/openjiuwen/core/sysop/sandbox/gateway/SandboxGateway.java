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
import com.openjiuwen.core.sysop.sandbox.launchers.LaunchedSandbox;
import com.openjiuwen.core.sysop.sandbox.launchers.PreDeploymentLauncher;
import com.openjiuwen.core.sysop.sandbox.launchers.SandboxLauncher;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioCodeProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioFsProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioShellProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxCodeProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxFsProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxShellProvider;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

/**
 * Mirrors Python's {@code SandboxGateway} in
 * {@code openjiuwen/core/sys_operation/sandbox/gateway/gateway.py}.
 */
public class SandboxGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final ObjectMapper HASH_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private static SandboxGateway instance;

    private final GatewayConfig config;

    private final Map<String, Object> providerCache = new ConcurrentHashMap<>();

    private final AbstractSandboxStore store;

    // Guarded by store; acquisition waits until physical release has finished.
    private final Map<String, CompletableFuture<Void>> pendingReleases = new LinkedHashMap<>();

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
                .thenCompose(provider -> invokeRequest(provider, request).thenApply(response -> {
                    syncProviderSandboxId(provider, request.getIsolationKey());
                    return response;
                }))
                .exceptionally(error -> errorResponse(rootCauseMessage(error), null));
    }

    public CompletableFuture<Flow.Publisher<?>> handleStreamRequest(
            SandboxGatewayConfig config,
            GatewayInvokeRequest request) {
        return getOrCreateProvider(config, request.getIsolationKey(), request.getOpType())
                .thenCompose(provider -> invokeStreamRequest(provider, request).thenApply(stream -> {
                    syncProviderSandboxId(provider, request.getIsolationKey());
                    return stream;
                }));
    }

    CompletableFuture<Object> getOrCreateProvider(
            SandboxGatewayConfig config,
            String isolationKey,
            String opType) {
        String cacheKey = cacheKey(isolationKey, opType);
        synchronized (store) {
            CompletableFuture<Void> pendingRelease = pendingReleases.get(isolationKey);
            if (pendingRelease != null) {
                return pendingRelease.thenCompose(unused -> getOrCreateProvider(config, isolationKey, opType));
            }
            Object cached = providerCache.get(cacheKey);
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
        }
        return getEndpoint(config, isolationKey).thenApply(endpoint -> {
            Object provider = SandboxRegistry.createProvider(
                    config.getLauncherConfig().getSandboxType(),
                    opType,
                    endpoint,
                    providerConfig(config, isolationKey));
            providerCache.put(cacheKey, provider);
            return provider;
        });
    }

    private Map<String, Object> detachProviderCache(String isolationKey) {
        String prefix = String.valueOf(isolationKey) + ":";
        Map<String, Object> removed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : providerCache.entrySet()) {
            if (entry.getKey().startsWith(prefix) && providerCache.remove(entry.getKey(), entry.getValue())) {
                removed.put(entry.getKey(), entry.getValue());
            }
        }
        return removed;
    }

    public CompletableFuture<GatewayResponse> getSandbox(SandboxCreateRequest request) {
        return getEndpoint(request.getConfig(), request.getIsolationKey())
                .thenApply(endpoint -> successResponse(endpoint))
                .exceptionally(error -> errorResponse(rootCauseMessage(error), null));
    }

    public CompletableFuture<GatewayResponse> releaseSandbox(String isolationKey, String onStop) {
        SandboxRecord record;
        Optional<SandboxLifecycleProvider> provider;
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Map<String, Object> removedProviders;
        synchronized (store) {
            Optional<SandboxRecord> removed = detachRecordForRelease(isolationKey);
            if (removed.isEmpty()) {
                return CompletableFuture.completedFuture(errorResponse("Sandbox record not found", Boolean.FALSE));
            }
            record = removed.get();
            provider = findLifecycleProvider(record.getSandboxId(), record.getBaseUrl());
            pendingReleases.put(isolationKey, completion);
            removedProviders = detachProviderCache(isolationKey);
        }
        CompletableFuture<Void> releaseFuture = CompletableFuture.completedFuture(null)
                .thenCompose(unused -> releaseRecord(record, onStop, provider));
        return releaseFuture.handle((unused, error) -> {
            finishRelease(isolationKey, record, removedProviders, onStop, error);
            completion.complete(null);
            if (error != null) {
                return errorResponse(rootCauseMessage(error), Boolean.FALSE);
            }
            return successResponse(Boolean.TRUE);
        }).copy();
    }

    // Called while holding store, together with provider detachment and release registration.
    private Optional<SandboxRecord> detachRecordForRelease(String isolationKey) {
        if (pendingReleases.containsKey(isolationKey)) {
            return Optional.empty();
        }
        return store.hdel(isolationKey);
    }

    private void finishRelease(String isolationKey, SandboxRecord record, Map<String, Object> removedProviders,
            String onStop, Throwable error) {
        synchronized (store) {
            if (error == null && "pause".equals(onStop)) {
                record.setStatus(SandboxStatus.PAUSED);
                record.setLastUsedTs(currentEpochSeconds());
            }
            if ((error != null || "pause".equals(onStop)) && store.get(isolationKey).isEmpty()) {
                store.set(isolationKey, record);
                if (error != null) {
                    removedProviders.forEach(providerCache::putIfAbsent);
                }
            }
            pendingReleases.remove(isolationKey);
        }
    }

    private CompletableFuture<Void> releaseRecord(SandboxRecord record, String onStop,
            Optional<SandboxLifecycleProvider> provider) {
        if ("keep".equals(onStop)) {
            return CompletableFuture.completedFuture(null);
        }
        if ("pause".equals(onStop)) {
            return pauseSandbox(record);
        }
        if (provider.isPresent() && record.getSandboxId() != null && !record.getSandboxId().isBlank()) {
            return provider.get().deleteSandbox(record.getSandboxId());
        }
        return SandboxRegistry.createLauncher(record.getLauncherType()).delete(record.getSandboxId());
    }

    public CompletableFuture<Void> pauseSandbox(SandboxRecord record) {
        String sandboxId = record.getSandboxId();
        SandboxLauncher launcher = SandboxRegistry.createLauncher(record.getLauncherType());
        return launcher.pause(sandboxId);
    }

    public CompletableFuture<Void> deleteSandbox(SandboxRecord record) {
        String sandboxId = record.getSandboxId();
        if (sandboxId != null && !sandboxId.isBlank()) {
            Optional<SandboxLifecycleProvider> provider =
                    findLifecycleProvider(sandboxId, record.getBaseUrl());
            if (provider.isPresent()) {
                return provider.get().deleteSandbox(sandboxId)
                        .thenRun(() -> evictProvidersBySandboxId(sandboxId, record.getBaseUrl()));
            }
        }
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

        Optional<SandboxRecord> cachedRecord;
        synchronized (store) {
            CompletableFuture<Void> pendingRelease = pendingReleases.get(isolationKey);
            if (pendingRelease != null) {
                return pendingRelease.thenCompose(unused -> getEndpoint(config, isolationKey));
            }
            cachedRecord = store.get(key);
        }
        if (cachedRecord.isPresent() && cachedRecord.get().getStatus() == SandboxStatus.RUNNING) {
            SandboxRecord record = cachedRecord.get();
            record.setLastUsedTs(now);
            return CompletableFuture.completedFuture(
                    new SandboxEndpoint(record.getBaseUrl(), record.getSandboxId()));
        }

        SandboxRecord record = cachedRecord.orElse(null);
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
                return CompletableFuture.completedFuture(
                        new SandboxEndpoint(record.getBaseUrl(), record.getSandboxId()));
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
            chain = chain.thenCompose(unused -> deleteSandbox(record));
        }
        return chain;
    }

    private void syncProviderSandboxId(Object provider, String isolationKey) {
        if (!(provider instanceof SandboxLifecycleProvider idProvider)) {
            return;
        }
        String sandboxId = idProvider.getSandboxId();
        if (sandboxId == null || sandboxId.isBlank()) {
            return;
        }
        updateRecordSandboxId(isolationKey, sandboxId);
    }

    private Optional<SandboxLifecycleProvider> findLifecycleProvider(String sandboxId, String baseUrl) {
        return providerCache.values().stream()
                .filter(SandboxLifecycleProvider.class::isInstance)
                .map(SandboxLifecycleProvider.class::cast)
                .filter(provider -> Objects.equals(provider.getSandboxId(), sandboxId))
                .filter(provider -> Objects.equals(provider.getSandboxBaseUrl(), baseUrl))
                .findFirst();
    }

    private void evictProvidersBySandboxId(String sandboxId, String baseUrl) {
        providerCache.entrySet().removeIf(entry -> entry.getValue() instanceof SandboxLifecycleProvider provider
                && Objects.equals(provider.getSandboxId(), sandboxId)
                && Objects.equals(provider.getSandboxBaseUrl(), baseUrl));
    }

    private SandboxGatewayConfig providerConfig(SandboxGatewayConfig config, String isolationKey) {
        Map<String, Object> params = config.getParams() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(config.getParams());
        params.put(SandboxLifecycleProvider.ISOLATION_KEY_PARAM, isolationKey);
        params.put(SandboxLifecycleProvider.SANDBOX_ID_CALLBACK_PARAM, (Consumer<String>) sandboxId -> {
            if (sandboxId != null && !sandboxId.isBlank()) {
                updateRecordSandboxId(isolationKey, sandboxId);
            }
        });
        return SandboxGatewayConfig.builder()
                .isolation(config.getIsolation())
                .launcherConfig(config.getLauncherConfig())
                .timeoutSeconds(config.getTimeoutSeconds())
                .authHeaders(config.getAuthHeaders())
                .authQueryParams(config.getAuthQueryParams())
                .gatewayUrl(config.getGatewayUrl())
                .params(params)
                .build();
    }

    private void updateRecordSandboxId(String isolationKey, String sandboxId) {
        synchronized (store) {
            store.get(isolationKey).ifPresent(record -> {
                if (!Objects.equals(record.getSandboxId(), sandboxId)) {
                    record.setSandboxId(sandboxId);
                    store.set(isolationKey, record);
                }
            });
        }
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
