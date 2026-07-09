/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Static utility class mirroring Python's clear_jiuwenbox_shared_sandbox,
 * force_recreate_jiuwenbox_sandbox, and delete_jiuwenbox_sandbox global functions.
 * 
 * @version 1.0
 * @since 0.1.7
 */
public final class SandboxLifecycleHelper {
    private static final Logger logger = LoggerFactory.getLogger(SandboxLifecycleHelper.class);

    /**
     * SandboxLifecycleHelper.
     * 
     * @since 0.1.7
     */
    private SandboxLifecycleHelper() {
    }

    /**
     * Clears the shared sandbox ID cache for the given base URL.
     * 
     * @param baseUrl the jiuwenBox server base URL whose cached sandbox IDs should be cleared
     * @return the list of sandbox IDs that were removed from the cache
     * @since 0.1.7
     */
    public static List<String> clearJiuwenBoxSharedSandbox(String baseUrl) {
        return JiuwenBoxProviderMixin.clearSharedSandbox(baseUrl);
    }

    /**
     * Force-recreates a jiuwenbox sandbox: clears cache, creates new sandbox,
     * uploads preserve files, and deletes stale sandbox IDs.
     * 
     * @param baseUrl the jiuwenBox server base URL
     * @param policy the sandbox policy configuration map, may be null
     * @param policyMode the sandbox policy mode, may be null
     * @param timeoutSeconds the HTTP request timeout in seconds
     * @param preserveFilesUpload the preserve files upload configuration, may be null
     * @param extraStaleSandboxIds additional stale sandbox IDs to delete, may be null
     * @param lifecycleHook the lifecycle event callback hook, may be null
     * @param reason the reason for recreation (e.g., "sandbox_lost")
     * @return the newly created sandbox ID
     * @since 0.1.7
     */
    public static String forceRecreateJiuwenBoxSandbox(String baseUrl, Map<String, Object> policy, String policyMode,
            int timeoutSeconds, Object preserveFilesUpload, List<String> extraStaleSandboxIds,
            LifecycleHook lifecycleHook, String reason) {
        // clear shared cache early (side-effect); stale IDs consumed later
        Map<String, Object> context = new HashMap<>();
        context.put("reason", reason);
        context.put("base_url", baseUrl);

        if (lifecycleHook != null) {
            lifecycleHook.onEvent("before_recreate", context);
        }

        JiuwenBoxClient client = new JiuwenBoxClient(baseUrl, timeoutSeconds);
        Optional<Integer> idleTimeoutOpt = extractIdleTimeout(policy, context);
        Optional<Integer> idleCheckIntervalOpt = extractIdleCheckInterval(policy, context);
        if (idleTimeoutOpt.isPresent() || idleCheckIntervalOpt.isPresent()) {
            client.setIdleTimeout(idleTimeoutOpt.orElse(null), idleCheckIntervalOpt.orElse(null));
        }

        Map<String, Object> createOptions = new LinkedHashMap<>(policy != null ? policy : Map.of());
        if (policyMode != null && !policyMode.isEmpty()) {
            createOptions.put("policy_mode", policyMode);
        }
        String newId = client.createSandbox(createOptions);

        String sharedKey = baseUrl.replaceAll("/+$", "");
        JiuwenBoxProviderMixin.registerSharedSandboxId(sharedKey, newId);

        if (preserveFilesUpload != null) {
            PreserveFilesUpload.uploadPreserveFiles(client, newId, preserveFilesUpload);
        }

        context.put("sandbox_id", newId);
        if (lifecycleHook != null) {
            lifecycleHook.onEvent("after_recreate", context);
        }

        List<String> staleIds = clearJiuwenBoxSharedSandbox(baseUrl);
        List<String> allStaleIds = new ArrayList<>(extraStaleSandboxIds != null ? extraStaleSandboxIds : List.of());
        for (String id : staleIds) {
            if (!id.equals(newId) && !allStaleIds.contains(id)) {
                allStaleIds.add(id);
            }
        }

        for (String staleId : allStaleIds) {
            try {
                client.deleteSandbox(staleId);
                logger.info("[jiuwenbox] deleted stale sandbox {}", staleId);
            } catch (SandboxOperationException exc) {
                logger.warn("[jiuwenbox] failed to delete stale sandbox {}", staleId, exc);
            }
            if (lifecycleHook != null) {
                Map<String, Object> deleteContext = new HashMap<>();
                deleteContext.put("reason", reason);
                deleteContext.put("sandbox_id", staleId);
                deleteContext.put("new_sandbox_id", newId);
                lifecycleHook.onEvent("after_delete", deleteContext);
            }
        }

        return newId;
    }

    /**
     * Deletes all jiuwenbox sandboxes: iterates cached base URLs, invokes lifecycle hooks,
     * and deletes each sandbox via the REST API.
     * 
     * @param reason the reason for deletion (e.g., "shutdown")
     * @param timeoutSeconds the HTTP request timeout in seconds
     * @return the list of all sandbox IDs that were deleted
     * @since 0.1.7
     */
    public static List<String> deleteJiuwenBoxSandbox(String reason, int timeoutSeconds) {
        List<String> allDeleted = new ArrayList<>();
        List<String> baseUrls = JiuwenBoxProviderMixin.cachedBaseUrls();

        for (String baseUrl : baseUrls) {
            LifecycleHook hook = JiuwenBoxProviderMixin.popLifecycleHook(baseUrl);

            if (hook != null) {
                Map<String, Object> context = new HashMap<>();
                context.put("reason", reason);
                context.put("base_url", baseUrl);
                hook.onEvent("before_delete", context);
            }

            JiuwenBoxClient client = new JiuwenBoxClient(baseUrl, timeoutSeconds);

            List<String> sandboxIds = JiuwenBoxProviderMixin.clearSharedSandbox(baseUrl);

            for (String sandboxId : sandboxIds) {
                try {
                    client.deleteSandbox(sandboxId);
                    logger.info("[jiuwenbox] deleted sandbox {}", sandboxId);
                } catch (SandboxOperationException exc) {
                    logger.warn("[jiuwenbox] failed to delete sandbox {}", sandboxId, exc);
                }
            }

            allDeleted.addAll(sandboxIds);

            if (hook != null) {
                Map<String, Object> context = new HashMap<>();
                context.put("reason", reason);
                context.put("base_url", baseUrl);
                context.put("deleted_sandbox_ids", sandboxIds);
                hook.onEvent("after_delete", context);
            }
        }

        return allDeleted;
    }

    /**
     * Extract idle timeout value from policy or context.
     * 
     * @param policy the sandbox policy configuration map, may be null
     * @param context the event context map, may contain idle_timeout
     * @return Optional containing the idle timeout in seconds, or empty if not found
     * @since 0.1.7
     */
    private static Optional<Integer> extractIdleTimeout(Map<String, Object> policy, Map<String, Object> context) {
        Object value = context.get("idle_timeout");
        if (value instanceof Number) {
            return Optional.of(((Number) value).intValue());
        }
        if (policy != null) {
            value = policy.get("idle_timeout");
            if (value instanceof Number) {
                return Optional.of(((Number) value).intValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Extract idle check interval value from policy or context.
     * 
     * @param policy the sandbox policy configuration map, may be null
     * @param context the event context map, may contain idle_check_interval
     * @return Optional containing the idle check interval in seconds, or empty if not found
     * @since 0.1.7
     */
    private static Optional<Integer> extractIdleCheckInterval(Map<String, Object> policy, Map<String, Object> context) {
        Object value = context.get("idle_check_interval");
        if (value instanceof Number) {
            return Optional.of(((Number) value).intValue());
        }
        if (policy != null) {
            value = policy.get("idle_check_interval");
            if (value instanceof Number) {
                return Optional.of(((Number) value).intValue());
            }
        }
        return Optional.empty();
    }
}
