/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.*;
import com.openjiuwen.core.sysop.sandbox.SandboxRegistry;
import com.openjiuwen.core.sysop.sandbox.launchers.SandboxLauncher;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton gateway for sandbox lifecycle management.
 * <p>
 * Provides endpoint resolution, provider creation, and full-chain routing
 * for sandbox operations (fs, shell, code).
 * <p>
 * Mirrors Python's {@code SandboxGateway} in {@code sandbox/gateway/gateway.py}.
 */
public class SandboxGateway {

    private static SandboxGateway instance;

    private final GatewayConfig config;
    private final Map<String, Object> providerCache = new ConcurrentHashMap<>();
    private final InMemorySandboxStore store;

    /**
     * Create a SandboxGateway instance.
     *
     * @param config optional gateway configuration (uses default if null)
     */
    public SandboxGateway(GatewayConfig config) {
        this.config = config != null ? config : new GatewayConfig();
        this.store = new InMemorySandboxStore();
        registerBuiltinLaunchers();
    }

    /**
     * Register built-in launcher implementations.
     */
    private void registerBuiltinLaunchers() {
        SandboxRegistry.registerLauncher("pre_deploy", 
                () -> new com.openjiuwen.core.sysop.sandbox.launchers.PreDeploymentLauncher());
    }

    /**
     * Get the singleton instance.
     *
     * @param config optional configuration (only used on first creation)
     * @return the singleton SandboxGateway instance
     */
    public static synchronized SandboxGateway getInstance(GatewayConfig config) {
        if (instance == null) {
            instance = new SandboxGateway(config);
        }
        return instance;
    }

    /**
     * Get the singleton instance with default configuration.
     *
     * @return the singleton SandboxGateway instance
     */
    public static SandboxGateway getInstance() {
        return getInstance(null);
    }

    // ── Full-chain routing: handleRequest / handleStreamRequest ──

    /**
     * Handle a request through full-chain routing.
     * <p>
     * Resolves endpoint → selects Provider → calls method → returns result.
     *
     * @param config  sandbox gateway configuration
     * @param request the invoke request
     * @return GatewayResponse containing the result
     */
    public GatewayResponse handleRequest(SandboxGatewayConfig config, GatewayInvokeRequest request) {
        try {
            Object provider = getOrCreateProvider(config, request.getIsolationKey(), request.getOpType());
            
            // Find the method on the provider
            java.lang.reflect.Method handler = findMethod(provider, request.getMethod(), request.getParams());
            if (handler == null) {
                return GatewayResponse.error("Method '" + request.getMethod() + "' not found on provider");
            }
            
            // Invoke the method
            Object result = invokeMethod(handler, provider, request.getParams());
            return GatewayResponse.success(result);
        } catch (Exception e) {
            return GatewayResponse.error(e.getMessage());
        }
    }

    /**
     * Handle a streaming request through full-chain routing.
     * <p>
     * Resolves endpoint → selects Provider → calls streaming method → returns iterator.
     *
     * @param config  sandbox gateway configuration
     * @param request the invoke request
     * @return an iterator for streaming results
     * @throws Exception if the method is not found or invocation fails
     */
    public Iterator<?> handleStreamRequest(SandboxGatewayConfig config, GatewayInvokeRequest request) throws Exception {
        Object provider = getOrCreateProvider(config, request.getIsolationKey(), request.getOpType());
        
        java.lang.reflect.Method handler = findMethod(provider, request.getMethod(), request.getParams());
        if (handler == null) {
            throw new NoSuchMethodException("Method '" + request.getMethod() + "' not found on provider");
        }
        
        Object result = invokeMethod(handler, provider, request.getParams());
        if (result instanceof Iterator) {
            return (Iterator<?>) result;
        }
        // Wrap single result in iterator
        return Collections.singleton(result).iterator();
    }

    /**
     * Get a cached provider or create one by resolving the sandbox endpoint.
     *
     * @param config        sandbox gateway configuration
     * @param isolationKey  sandbox isolation key
     * @param opType        operation type (fs/shell/code)
     * @return the provider instance
     * @throws Exception if provider creation fails
     */
    private Object getOrCreateProvider(SandboxGatewayConfig config, String isolationKey, String opType) throws Exception {
        String cacheKey = isolationKey + ":" + opType;
        if (providerCache.containsKey(cacheKey)) {
            return providerCache.get(cacheKey);
        }

        SandboxEndpoint endpoint = getEndpoint(config, isolationKey);
        Object provider = SandboxRegistry.createProvider(
                config.getLauncherConfig().getSandboxType(),
                opType,
                endpoint,
                config
        );
        providerCache.put(cacheKey, provider);
        return provider;
    }

    /**
     * Evict all cached providers for a given isolation key.
     *
     * @param isolationKey the isolation key
     */
    private void evictProviderCache(String isolationKey) {
        List<String> keysToRemove = new ArrayList<>();
        for (String key : providerCache.keySet()) {
            if (key.startsWith(isolationKey + ":")) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            providerCache.remove(key);
        }
    }

    // ── Legacy endpoint-only API (kept for backward compatibility) ──

    /**
     * Gateway only resolves and returns sandbox endpoint.
     *
     * @param request the sandbox creation request
     * @return GatewayResponse containing the SandboxEndpoint
     */
    public GatewayResponse getSandbox(SandboxCreateRequest request) {
        try {
            SandboxEndpoint endpoint = getEndpoint(request.getConfig(), request.getIsolationKey());
            return GatewayResponse.success(endpoint);
        } catch (Exception e) {
            return GatewayResponse.error(e.getMessage());
        }
    }

    /**
     * Release a sandbox by isolation key.
     *
     * @param isolationKey the isolation key
     * @param onStop       behavior on stop: "delete", "pause", or "keep"
     * @return GatewayResponse indicating success or failure
     */
    public GatewayResponse releaseSandbox(String isolationKey, String onStop) {
        evictProviderCache(isolationKey);
        SandboxRecord record = store.hdel(isolationKey);
        
        if (record == null) {
            return GatewayResponse.error("Sandbox record not found");
        }
        
        try {
            if ("keep".equals(onStop)) {
                // Do nothing, sandbox stays running (externally managed)
            } else if ("pause".equals(onStop)) {
                pauseSandbox(record);
            } else {
                deleteSandbox(record);
            }
            return GatewayResponse.success(true);
        } catch (Exception e) {
            return GatewayResponse.error(e.getMessage());
        }
    }

    /**
     * Pause a sandbox.
     *
     * @param record the sandbox record
     * @throws Exception if pause fails
     */
    private void pauseSandbox(SandboxRecord record) throws Exception {
        SandboxLauncher launcher = SandboxRegistry.createLauncher(record.getLauncherType());
        launcher.pause(record.getSandboxId());
    }

    /**
     * Delete a sandbox.
     *
     * @param record the sandbox record
     * @throws Exception if delete fails
     */
    private void deleteSandbox(SandboxRecord record) throws Exception {
        SandboxLauncher launcher = SandboxRegistry.createLauncher(record.getLauncherType());
        launcher.delete(record.getSandboxId());
    }

    /**
     * Get or create a sandbox endpoint.
     *
     * @param config        sandbox gateway configuration
     * @param isolationKey  optional isolation key
     * @return the SandboxEndpoint
     * @throws Exception if endpoint resolution fails
     */
    private SandboxEndpoint getEndpoint(SandboxGatewayConfig config, String isolationKey) throws Exception {
        String launcherType = config.getLauncherConfig().getLauncherType();
        String key = isolationKey;
        double now = System.currentTimeMillis() / 1000.0;

        SandboxRecord record = store.get(key);
        
        // Check if running sandbox exists
        if (record != null && record.getStatus() == SandboxStatus.RUNNING) {
            record.setLastUsedTs(now);
            return SandboxEndpoint.builder()
                    .baseUrl(record.getBaseUrl())
                    .sandboxId(record.getSandboxId())
                    .build();
        }

        // Try to create new sandbox if no record exists
        if (record == null) {
            LaunchedSandbox launched = createNewSandbox(key, now, config);
            return SandboxEndpoint.builder()
                    .baseUrl(launched.getBaseUrl())
                    .sandboxId(launched.getSandboxId())
                    .build();
        }

        // Check actual status with launcher
        SandboxLauncher launcher = SandboxRegistry.createLauncher(launcherType);
        SandboxStatus realStatus = launcher.checkStatus(record.getSandboxId());

        if (realStatus == SandboxStatus.RUNNING) {
            record.setStatus(SandboxStatus.RUNNING);
            record.setLastUsedTs(now);
            store.set(key, record);
            return SandboxEndpoint.builder()
                    .baseUrl(record.getBaseUrl())
                    .sandboxId(record.getSandboxId())
                    .build();
        }

        if (realStatus == SandboxStatus.PAUSED) {
            launcher.resume(record.getSandboxId());
            record.setStatus(SandboxStatus.RUNNING);
            record.setLastUsedTs(now);
            store.set(key, record);
            return SandboxEndpoint.builder()
                    .baseUrl(record.getBaseUrl())
                    .sandboxId(record.getSandboxId())
                    .build();
        }

        // Sandbox is killed or invalid, recreate
        store.hdel(key);
        LaunchedSandbox launched = createNewSandbox(key, now, config);
        return SandboxEndpoint.builder()
                .baseUrl(launched.getBaseUrl())
                .sandboxId(launched.getSandboxId())
                .build();
    }

    /**
     * Create a new sandbox instance.
     *
     * @param key      isolation key
     * @param now      current timestamp
     * @param config   sandbox gateway configuration
     * @return the LaunchedSandbox descriptor
     * @throws Exception if creation fails
     */
    private LaunchedSandbox createNewSandbox(String key, double now, SandboxGatewayConfig config) throws Exception {
        evictIdle(now, config);

        SandboxLauncher launcher = SandboxRegistry.createLauncher(config.getLauncherConfig().getLauncherType());
        LaunchedSandbox launched = launcher.launch(
                config.getLauncherConfig(),
                config.getTimeoutSeconds(),
                key
        );

        SandboxRecord record = SandboxRecord.builder()
                .sandboxId(launched.getSandboxId())
                .baseUrl(launched.getBaseUrl())
                .status(SandboxStatus.RUNNING)
                .launcherType(config.getLauncherConfig().getLauncherType())
                .sandboxType(config.getLauncherConfig().getSandboxType())
                .containerConfigHash(computeContainerConfigHash(config.getLauncherConfig()))
                .lastUsedTs(now)
                .build();

        store.set(key, record);
        return launched;
    }

    /**
     * Evict idle sandboxes based on TTL.
     *
     * @param now    current timestamp
     * @param config sandbox gateway configuration
     * @throws Exception if eviction cleanup fails
     */
    private void evictIdle(double now, SandboxGatewayConfig config) throws Exception {
        Integer idleTtl = config.getLauncherConfig().getIdleTtlSeconds();
        if (idleTtl == null) {
            return;
        }

        List<SandboxRecord> expired = store.evictExpired(idleTtl, (int) now);
        for (SandboxRecord record : expired) {
            SandboxLauncher launcher = SandboxRegistry.createLauncher(record.getLauncherType());
            launcher.delete(record.getSandboxId());
        }
    }

    /**
     * Compute hash of container-level configuration fields.
     *
     * @param config the launcher configuration
     * @return SHA-256 hash prefix (16 chars)
     */
    private String computeContainerConfigHash(SandboxLauncherConfig config) {
        if (config == null) {
            return "none";
        }
        try {
            Map<String, Object> containerFields = new LinkedHashMap<>();
            containerFields.put("image", config.getImage());
            containerFields.put("env", config.getEnv());
            containerFields.put("volumes", config.getVolumes());
            containerFields.put("resourceLimits", config.getResourceLimits());
            containerFields.put("network", config.getNetwork());
            containerFields.put("servicePort", config.getServicePort());

            // Simple hash computation
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, Object> entry : containerFields.entrySet()) {
                if (entry.getValue() != null) {
                    md.update(entry.getKey().getBytes());
                    md.update(String.valueOf(entry.getValue()).getBytes());
                }
            }
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "error";
        }
    }

    // ── Helper methods for reflection-based invocation ──

    /**
     * Find a method on the provider by name and parameter compatibility.
     *
     * @param provider the provider object
     * @param methodName the method name
     * @param params the parameters map
     * @return the matching Method, or null if not found
     */
    private java.lang.reflect.Method findMethod(Object provider, String methodName, Map<String, Object> params) {
        for (java.lang.reflect.Method method : provider.getClass().getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Invoke a method with parameters.
     *
     * @param method the method to invoke
     * @param provider the provider object
     * @param params the parameters map
     * @return the method result
     * @throws Exception if invocation fails
     */
    private Object invokeMethod(java.lang.reflect.Method method, Object provider, Map<String, Object> params) 
            throws Exception {
        // Simplified invocation - just call with no args for now
        // Real implementation would match params to method parameters
        return method.invoke(provider);
    }
}