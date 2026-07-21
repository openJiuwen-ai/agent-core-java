/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.sandbox.SandboxEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.OkHttpClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Core sandbox management mixin class using composition pattern.
 * Holds endpoint, config, client, sandbox_id, and provides all
 * sandbox lifecycle management including shared caching, auto-recreate,
 * lifecycle hooks, and idle timeout deduplication.
 * 
 * @version 1.0
 * @since 0.1.7
 */
public class JiuwenBoxProviderMixin {
    private static final Logger logger = LoggerFactory.getLogger(JiuwenBoxProviderMixin.class);
    private static final int DEFAULT_SANDBOX_RECREATE_RETRIES = 3;
    private static final int SANDBOX_RECREATE_RETRY_SLEEP_SECONDS = 1;

    private static final ConcurrentHashMap<String, String> SHARED_SANDBOX_IDS = new ConcurrentHashMap<>();
    private static final ReentrantLock SHARED_LOCK = new ReentrantLock();
    private static final ConcurrentHashMap<String, LifecycleHook> LIFECYCLE_HOOKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, int[]> IDLE_TIMEOUT_CACHE = new ConcurrentHashMap<>();
    private static final ReentrantLock IDLE_TIMEOUT_CACHE_LOCK = new ReentrantLock();
    private static final ReentrantLock RECREATE_LOCK = new ReentrantLock();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // --- instance fields (separated from static fields by a blank line) ---
    private final SandboxEndpoint endpoint;
    private final SandboxGatewayConfig config;
    private JiuwenBoxClient client;
    private String sandboxId;
    private final int timeoutSeconds;

    /**
     * Construct the mixin with endpoint and config.
     * 
     * @param endpoint the sandbox endpoint providing base URL and sandbox ID
     * @param config the sandbox gateway configuration, may be null (defaults to empty config)
     * @since 0.1.7
     */
    public JiuwenBoxProviderMixin(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        this.endpoint = endpoint;
        this.config = config != null ? config : SandboxGatewayConfig.builder().build();
        this.sandboxId = endpointValue(endpoint, config, "sandbox_id").orElse(null);
        String envId = System.getenv("JIUWENBOX_SANDBOX_ID");
        if (envId != null && !envId.isBlank()) {
            this.sandboxId = envId;
        }
        this.timeoutSeconds = config != null && config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 30;
    }

    /**
     * Functional interface for sandbox retry operations.
     * 
     * @since 0.1.7
     */
    @FunctionalInterface
    public interface SandboxRetryOperation<T> {
        /**
         * apply.
         * 
         * @param sandboxId sandboxId
         * @return the result
         * @since 0.1.7
         */
        T apply(String sandboxId);
    }

    /**
     * Lazily create the JiuwenBox HTTP client from endpoint baseUrl.
     * 
     * @return the initialized JiuwenBoxClient instance
     * @since 0.1.7
     */
    public JiuwenBoxClient getClient() {
        if (client == null) {
            Optional<String> baseUrlOpt = endpointValue(endpoint, config, "base_url");
            if (baseUrlOpt.isEmpty() || baseUrlOpt.get().isBlank()) {
                throw new IllegalArgumentException("jiuwenbox provider requires endpoint.base_url");
            }
            OkHttpClient injectedClient = resolveOkHttpClient(config);
            client = new JiuwenBoxClient(baseUrlOpt.get(), timeoutSeconds, injectedClient);
        }
        return client;
    }

    private static OkHttpClient resolveOkHttpClient(SandboxGatewayConfig config) {
        if (config == null || config.getParams() == null) {
            return null;
        }
        Object injected = config.getParams().get("_ojw_okhttp_client");
        if (injected instanceof OkHttpClient okHttpClient) {
            return okHttpClient;
        }
        return null;
    }

    /**
     * Get extra_params from config.launcherConfig; if create=true and null, initialize it.
     * 
     * @param isCreate whether to initialize extra_params if it is currently null
     * @return the extra_params map, or an empty map if not available
     * @since 0.1.7
     */
    public Map<String, Object> launcherExtraParams(boolean isCreate) {
        SandboxLauncherConfig launcherConfig = config != null ? config.getLauncherConfig() : null;
        if (launcherConfig == null) {
            return Map.of();
        }
        Map<String, Object> extraParams = launcherConfig.getExtraParams();
        if (extraParams instanceof Map) {
            return extraParams;
        }
        if (!isCreate) {
            return Map.of();
        }
        extraParams = new LinkedHashMap<>();
        launcherConfig.setExtraParams(extraParams);
        return extraParams;
    }

    /**
     * Extract policy/policy_mode from extra_params as sandbox create options.
     * 
     * @return a map containing policy and/or policy_mode entries; empty map if none found
     * @since 0.1.7
     */
    public Map<String, Object> sandboxCreateOptionsFromLauncherExtraParams() {
        Map<String, Object> extraParams = launcherExtraParams(false);
        Map<String, Object> options = new HashMap<>();
        Object policy = extraParams.get("policy");
        if (policy instanceof Map) {
            options.put("policy", policy);
        }
        Object policyMode = extraParams.get("policy_mode");
        if (policyMode instanceof String && !((String) policyMode).isEmpty()) {
            options.put("policy_mode", policyMode);
        }
        return options;
    }

    /**
     * Build shared scope key: base_url + create_options_json.
     * 
     * @return the shared scope key string used for cache lookups
     * @since 0.1.7
     */
    public String sharedScopeKey() {
        Optional<String> baseUrlOpt = endpointValue(endpoint, config, "base_url");
        if (baseUrlOpt.isEmpty() || baseUrlOpt.get().isBlank()) {
            throw new IllegalArgumentException("jiuwenbox provider requires endpoint.base_url");
        }
        String key = baseUrlOpt.get().replaceAll("/+$", "");
        Map<String, Object> createOptions = sandboxCreateOptionsFromLauncherExtraParams();
        if (createOptions.isEmpty()) {
            return key;
        }
        try {
            String optionsKey = OBJECT_MAPPER.writeValueAsString(createOptions);
            return key + "|" + optionsKey;
        } catch (JsonProcessingException exc) {
            logger.warn("[jiuwenbox] failed to serialize create options for shared key", exc);
            return key;
        }
    }

    /**
     * Extract sandbox_id from launcher extra_params.
     * 
     * @return Optional containing the sandbox_id string, or empty if not found
     * @since 0.1.7
     */
    public Optional<String> sandboxIdFromLauncherExtraParams() {
        Map<String, Object> extraParams = launcherExtraParams(false);
        Object value = extraParams.get("sandbox_id");
        return value instanceof String && !((String) value).isEmpty() ? Optional.of((String) value) : Optional.empty();
    }

    /**
     * Extract lifecycle_hook from extra_params (if it is a LifecycleHook instance).
     * 
     * @return Optional containing the LifecycleHook, or empty if not found
     * @since 0.1.7
     */
    public Optional<LifecycleHook> lifecycleHook() {
        Object hook = launcherExtraParams(false).get("lifecycle_hook");
        return hook instanceof LifecycleHook ? Optional.of((LifecycleHook) hook) : Optional.empty();
    }

    /**
     * Return [idleTimeout, idleCheckInterval] from launcherConfig.idleTtlSeconds and extra_params.
     * 
     * @return an int array where index 0 is idleTimeout (-1 if absent) and index 1 is idleCheckInterval (-1 if absent)
     * @since 0.1.7
     */
    public int[] idleTimeoutFromLauncher() {
        SandboxLauncherConfig launcherConfig = config != null ? config.getLauncherConfig() : null;
        Integer idleTimeout = launcherConfig != null ? launcherConfig.getIdleTtlSeconds() : null;
        Map<String, Object> extraParams = launcherExtraParams(false);
        Object rawCheck = extraParams instanceof Map ? extraParams.get("idle_check_interval") : null;
        Integer idleCheckInterval = null;
        if (rawCheck instanceof Integer && !(rawCheck instanceof Boolean)) {
            idleCheckInterval = (Integer) rawCheck;
        } else if (rawCheck instanceof Number) {
            idleCheckInterval = ((Number) rawCheck).intValue();
        } else {
            logger.debug("[jiuwenbox] non-numeric idle_check_interval value ignored: {}", rawCheck);
        }
        return new int[]{idleTimeout != null ? idleTimeout : -1, idleCheckInterval != null ? idleCheckInterval : -1};
    }

    /**
     * Configure idle timeout on jiuwenbox server; dedupe PUT /api/v1/timeout calls.
     * 
     * @since 0.1.7
     */
    public void configureServerIdleTimeout() {
        int[] timeouts = idleTimeoutFromLauncher();
        Integer idleTimeout = timeouts[0] >= 0 ? timeouts[0] : null;
        Integer idleCheckInterval = timeouts[1] >= 0 ? timeouts[1] : null;
        if (idleTimeout == null && idleCheckInterval == null) {
            return;
        }
        String baseUrl = endpointValue(endpoint, config, "base_url").orElse(null);
        String cacheKey = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "";
        int[] target =
            new int[]{idleTimeout != null ? idleTimeout : -1, idleCheckInterval != null ? idleCheckInterval : -1};
        IDLE_TIMEOUT_CACHE_LOCK.lock();
        try {
            int[] cached = IDLE_TIMEOUT_CACHE.get(cacheKey);
            if (cached != null && Arrays.equals(cached, target)) {
                return;
            }
        } finally {
            IDLE_TIMEOUT_CACHE_LOCK.unlock();
        }
        try {
            getClient().setIdleTimeout(idleTimeout, idleCheckInterval);
            logger.info("[jiuwenbox] PUT /api/v1/timeout: idle_timeout={} idle_check_interval={}", idleTimeout,
                    idleCheckInterval);
        } catch (SandboxOperationException exc) {
            logger.warn("[jiuwenbox] PUT /api/v1/timeout failed (idle_timeout={}, idle_check_interval={})", idleTimeout,
                    idleCheckInterval, exc);
            return;
        }
        IDLE_TIMEOUT_CACHE_LOCK.lock();
        try {
            IDLE_TIMEOUT_CACHE.put(cacheKey, target);
        } finally {
            IDLE_TIMEOUT_CACHE_LOCK.unlock();
        }
    }

    /**
     * Register sandbox_id in the cross-instance shared cache under sharedKey.
     * 
     * @param sharedKey the shared scope key (base_url + create_options)
     * @param sandboxId the sandbox ID to register
     * @since 0.1.7
     */
    public static void registerSharedSandboxId(String sharedKey, String sandboxId) {
        SHARED_LOCK.lock();
        try {
            SHARED_SANDBOX_IDS.put(sharedKey, sandboxId);
        } finally {
            SHARED_LOCK.unlock();
        }
    }

    /**
     * Cache the lifecycle hook for baseUrl so teardown can reuse it.
     * 
     * @param baseUrl the jiuwenBox server base URL to associate with the hook
     * @param hook the LifecycleHook instance to cache, may be null (no-op)
     * @since 0.1.7
     */
    public static void registerLifecycleHook(String baseUrl, LifecycleHook hook) {
        if (hook == null) {
            return;
        }
        SHARED_LOCK.lock();
        try {
            LIFECYCLE_HOOKS.put(baseUrl.replaceAll("/+$", ""), hook);
        } finally {
            SHARED_LOCK.unlock();
        }
    }

    /**
     * Pop the cached lifecycle hook for baseUrl.
     * 
     * @param baseUrl the jiuwenBox server base URL whose hook should be removed
     * @return the previously cached LifecycleHook, or null if none was cached
     * @since 0.1.7
     */
    public static LifecycleHook popLifecycleHook(String baseUrl) {
        SHARED_LOCK.lock();
        try {
            return LIFECYCLE_HOOKS.remove(baseUrl.replaceAll("/+$", ""));
        } finally {
            SHARED_LOCK.unlock();
        }
    }

    /**
     * Return base_urls that currently hold cached sandbox IDs.
     * 
     * @return the list of distinct base URL strings from the shared cache
     * @since 0.1.7
     */
    public static List<String> cachedBaseUrls() {
        SHARED_LOCK.lock();
        try {
            List<String> urls = new ArrayList<>();
            for (String key : SHARED_SANDBOX_IDS.keySet()) {
                String url = key.split("\\|", 2)[0];
                if (!urls.contains(url)) {
                    urls.add(url);
                }
            }
            return urls;
        } finally {
            SHARED_LOCK.unlock();
        }
    }

    /**
     * Remove all cached sandbox IDs for baseUrl and return removed sandbox_ids.
     * 
     * @param baseUrl the base URL whose cached sandbox IDs should be removed
     * @return the list of distinct sandbox IDs that were removed
     * @since 0.1.7
     */
    public static List<String> clearSharedSandbox(String baseUrl) {
        String sharedKey = baseUrl.replaceAll("/+$", "");
        List<String> removed = new ArrayList<>();
        SHARED_LOCK.lock();
        try {
            List<String> keysToDelete = new ArrayList<>();
            for (String key : SHARED_SANDBOX_IDS.keySet()) {
                if (key.startsWith(sharedKey)) {
                    keysToDelete.add(key);
                }
            }
            for (String key : keysToDelete) {
                String value = SHARED_SANDBOX_IDS.remove(key);
                if (value != null && !value.isEmpty() && !removed.contains(value)) {
                    removed.add(value);
                }
            }
        } finally {
            SHARED_LOCK.unlock();
        }
        return removed;
    }

    /**
     * THE CORE METHOD. Resolve sandbox ID from multiple sources with priority chain:
     * env var → launcher extra_params → endpoint → SHARED_SANDBOX_IDS cache → create new sandbox.
     * 
     * @return the resolved sandbox ID string
     * @since 0.1.7
     */
    public String getSandboxId() {
        String envSandboxId = System.getenv("JIUWENBOX_SANDBOX_ID");
        if (envSandboxId != null && !envSandboxId.isBlank() && !envSandboxId.equals(sandboxId)) {
            sandboxId = envSandboxId;
        }
        sandboxIdFromLauncherExtraParams().filter(id -> !id.equals(sandboxId)).ifPresent(id -> sandboxId = id);
        if (sandboxId == null) {
            String endpointSandboxId = endpoint != null ? endpoint.getSandboxId() : null;
            if (endpointSandboxId != null && !endpointSandboxId.isEmpty()) {
                sandboxId = endpointSandboxId;
            }
        }
        if (sandboxId == null) {
            return createNewSandbox();
        } else {
            registerExistingSandboxId();
        }
        return sandboxId;
    }

    /**
     * createNewSandbox.
     * 
     * @return the result
     * @since 0.1.7
     */
    private String createNewSandbox() {
        LifecycleHook lifecycleHook = lifecycleHook().orElse(null);
        String sharedKey = sharedScopeKey();
        registerLifecycleHook(endpointValue(endpoint, config, "base_url").orElse(null), lifecycleHook);
        boolean isNewlyCreated = false;
        SHARED_LOCK.lock();
        try {
            sandboxId = SHARED_SANDBOX_IDS.get(sharedKey);
            if (sandboxId == null) {
                invokeLifecycleHook(lifecycleHook, "before_create", Map.of("reason", "initial"));
                configureServerIdleTimeout();
                Map<String, Object> createOptions = sandboxCreateOptionsFromLauncherExtraParams();
                sandboxId = getClient().createSandbox(createOptions);
                isNewlyCreated = true;
            }
            SHARED_SANDBOX_IDS.put(sharedKey, sandboxId);
            launcherExtraParams(true).put("sandbox_id", sandboxId);
        } finally {
            SHARED_LOCK.unlock();
        }
        if (isNewlyCreated) {
            PreserveFilesUpload.uploadPreserveFiles(getClient(), sandboxId,
                    launcherExtraParams(false).get("preserve_files_upload"));
            invokeLifecycleHook(lifecycleHook, "after_create", Map.of("reason", "initial", "sandbox_id", sandboxId));
        }
        return sandboxId;
    }

    /**
     * registerExistingSandboxId.
     * 
     * @since 0.1.7
     */
    private void registerExistingSandboxId() {
        String sharedKey = sharedScopeKey();
        SHARED_LOCK.lock();
        try {
            SHARED_SANDBOX_IDS.put(sharedKey, sandboxId);
            launcherExtraParams(true).put("sandbox_id", sandboxId);
        } finally {
            SHARED_LOCK.unlock();
        }
    }

    /**
     * Run op with auto sandbox recreate on sandbox-not-found 404.
     * 
     * @param op the sandbox retry operation to execute
     * @return the operation result
     * @since 0.1.7
     */
    public <T> T executeWithSandboxRetry(SandboxRetryOperation<T> op) {
        int maxRetries = resolveRecreateRetries();
        String staleSandboxId = getSandboxId();
        RuntimeException lastExc = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            String currentSandboxId;
            if (attempt == 0) {
                currentSandboxId = staleSandboxId;
            } else {
                try {
                    Thread.sleep(SANDBOX_RECREATE_RETRY_SLEEP_SECONDS * 1000L);
                } catch (InterruptedException ie) {
                    logger.warn("[jiuwenbox] sandbox retry loop interrupted", ie);
                    break;
                }
                logger.info("[jiuwenbox] sandbox-not-found auto-recreate attempt {}/{} (stale={})", attempt, maxRetries,
                        staleSandboxId);
                try {
                    currentSandboxId = recreateSandboxAfterLoss(staleSandboxId);
                } catch (SandboxOperationException exc) {
                    logger.warn("[jiuwenbox] sandbox recreate failed (attempt {}/{})", attempt, maxRetries, exc);
                    continue;
                }
            }
            try {
                return op.apply(currentSandboxId);
            } catch (SandboxNotFoundException e) {
                lastExc = e;
                staleSandboxId = currentSandboxId;
                logger.warn("[jiuwenbox] sandbox {} not found (attempt {}/{})", currentSandboxId, attempt, maxRetries);
            } catch (SandboxOperationException e) {
                if (isSandboxNotFoundError(e)) {
                    lastExc = e;
                    staleSandboxId = currentSandboxId;
                    logger.warn("[jiuwenbox] sandbox {} not found (attempt {}/{})", currentSandboxId, attempt,
                            maxRetries);
                } else {
                    throw e;
                }
            }
        }
        throw new SandboxRecreateExhaustedException("Sandbox recreate exhausted after " + maxRetries + " retries",
                maxRetries, staleSandboxId);
    }

    /**
     * Recreate sandbox under lock; double-check launcher/cache before creating.
     * 
     * @param staleSandboxId the sandbox ID that was lost or stale
     * @return the new or existing sandbox ID after recreation
     * @since 0.1.7
     */
    public String recreateSandboxAfterLoss(String staleSandboxId) {
        Optional<String> baseUrlOpt = endpointValue(endpoint, config, "base_url");
        if (baseUrlOpt.isEmpty() || baseUrlOpt.get().isBlank()) {
            throw new IllegalArgumentException("jiuwenbox provider requires endpoint.base_url");
        }
        String baseUrl = baseUrlOpt.get();
        Map<String, Object> createOptions = sandboxCreateOptionsFromLauncherExtraParams();
        Map<String, Object> extraParams = launcherExtraParams(false);
        Object preserveFilesUpload = extraParams instanceof Map ? extraParams.get("preserve_files_upload") : null;
        RECREATE_LOCK.lock();
        try {
            String current = sandboxIdFromLauncherExtraParams().orElse(null);
            String sharedKey = sharedScopeKey();
            String cached;
            SHARED_LOCK.lock();
            try {
                cached = SHARED_SANDBOX_IDS.get(sharedKey);
            } finally {
                SHARED_LOCK.unlock();
            }
            for (String candidate : new String[]{current, cached}) {
                if (candidate != null && !candidate.equals(staleSandboxId)) {
                    sandboxId = candidate;
                    return candidate;
                }
            }
            LifecycleHook lifecycleHook = lifecycleHook().orElse(null);
            invokeLifecycleHook(lifecycleHook, "before_recreate",
                    Map.of("reason", "sandbox_lost", "stale_sandbox_id", staleSandboxId));

            String policyMode =
                createOptions.get("policy_mode") instanceof String ? (String) createOptions.get("policy_mode") : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> policy =
                createOptions.get("policy") instanceof Map ? (Map<String, Object>) createOptions.get("policy") : null;
            String newId = SandboxLifecycleHelper.forceRecreateJiuwenBoxSandbox(baseUrl, policy, policyMode,
                    timeoutSeconds, preserveFilesUpload, List.of(staleSandboxId), lifecycleHook, "sandbox_lost");

            sandboxId = newId;
            launcherExtraParams(true).put("sandbox_id", newId);
            invokeLifecycleHook(lifecycleHook, "after_recreate",
                    Map.of("reason", "sandbox_lost", "sandbox_id", newId, "stale_sandbox_id", staleSandboxId));
            return newId;
        } finally {
            RECREATE_LOCK.unlock();
        }
    }

    /**
     * invokeLifecycleHook.
     * 
     * @param hook hook
     * @param eventName eventName
     * @param context context
     * @since 0.1.7
     */
    private static void invokeLifecycleHook(LifecycleHook hook, String eventName, Map<String, Object> context) {
        if (hook == null) {
            return;
        }
        try {
            hook.onEvent(eventName, new HashMap<>(context));
        } catch (RuntimeException exc) {
            logger.warn("[jiuwenbox] lifecycle_hook invocation failed for event {}", eventName, exc);
        }
    }

    /**
     * resolveRecreateRetries.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static int resolveRecreateRetries() {
        String raw = System.getenv("JIUWENBOX_SANDBOX_RECREATE_RETRIES");
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_SANDBOX_RECREATE_RETRIES;
        }
        try {
            int value = Integer.parseInt(raw);
            return Math.max(value, 0);
        } catch (NumberFormatException exc) {
            logger.warn("[jiuwenbox] JIUWENBOX_SANDBOX_RECREATE_RETRIES={} invalid, falling back to default {}", raw,
                    DEFAULT_SANDBOX_RECREATE_RETRIES);
            return DEFAULT_SANDBOX_RECREATE_RETRIES;
        }
    }

    /**
     * isSandboxNotFoundError.
     * 
     * @param exc exc
     * @return the result
     * @since 0.1.7
     */
    private static boolean isSandboxNotFoundError(Exception exc) {
        if (exc instanceof SandboxNotFoundException) {
            return true;
        }
        return false;
    }

    /**
     * endpointValue.
     * 
     * @param endpoint endpoint
     * @param config config
     * @param field field
     * @return the result
     * @since 0.1.7
     */
    private static Optional<String> endpointValue(SandboxEndpoint endpoint, SandboxGatewayConfig config, String field) {
        if ("base_url".equals(field)) {
            String value = endpoint != null ? endpoint.getBaseUrl() : null;
            if (value != null) {
                return Optional.of(value);
            }
            SandboxLauncherConfig launcherConfig = config != null ? config.getLauncherConfig() : null;
            return Optional.ofNullable(launcherConfig != null ? launcherConfig.getBaseUrl() : null);
        }
        if ("sandbox_id".equals(field)) {
            String value = endpoint != null ? endpoint.getSandboxId() : null;
            if (value != null) {
                return Optional.of(value);
            }
            SandboxLauncherConfig launcherConfig = config != null ? config.getLauncherConfig() : null;
            if (launcherConfig != null) {
                Object ep = launcherConfig.getExtraParams();
                if (ep instanceof Map) {
                    Object sid = ((Map<?, ?>) ep).get("sandbox_id");
                    return sid instanceof String ? Optional.of((String) sid) : Optional.empty();
                }
            }
            return Optional.empty();
        }
        return Optional.empty();
    }
}
