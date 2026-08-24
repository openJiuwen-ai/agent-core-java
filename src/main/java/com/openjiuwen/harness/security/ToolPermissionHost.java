/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.openjiuwen.harness.security.patterns.PermissionsYamlWriter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Public class ToolPermissionHost used by the Java parity implementation.
 *
 * <p>Task 9 upgrades the host from a confirmation stub into a real host boundary:
 * {@link #requestPermissionConfirmation(PermissionConfirmationRequest)} delegates to an
 * injectable hosted-confirmation callback (returning {@code null} when unset so the rail
 * falls back to the built-in {@code ConfirmPayload} interrupt/resume path), and
 * {@link #persistAllowRule(Map)} writes an already-merged {@code permissions} snapshot
 * to disk via {@link PermissionsYamlWriter}. The legacy
 * {@link #persistAllowRule(String, Map)} entrypoint is preserved for backward
 * compatibility and delegates the write step to the new overload.
 *
 * @since 0.1.7
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolPermissionHost {
    private static final Logger logger = LoggerFactory.getLogger(ToolPermissionHost.class);

    @Builder.Default
    /**
     * =.
     *
     * @since 0.1.7
     */
    private Supplier<Path> resolveWorkspaceDir = () -> null;
    private Path permissionYamlPath;
    @Builder.Default
    private Supplier<Map<String, Object>> getPermissionsSnapshot = LinkedHashMap::new;

    /**
     * Hosted ASK confirmation callback; unset means the rail uses the built-in interrupt
     * confirmation flow.
     */
    private Function<PermissionConfirmationRequest, PermissionConfirmResponse> requestPermissionConfirmationFn;

    /**
     * resolveWorkspaceDir.
     *
     * @return the result
     * @since 0.1.7
     */
    public Path resolveWorkspaceDir() {
        return resolveWorkspaceDir != null ? resolveWorkspaceDir.get() : null;
    }

    /**
     * permissionYamlPath.
     *
     * @return the result
     * @since 0.1.7
     */
    public Path permissionYamlPath() {
        return permissionYamlPath;
    }

    /**
     * getPermissionsSnapshot.
     *
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getPermissionsSnapshot() {
        return getPermissionsSnapshot != null ? getPermissionsSnapshot.get() : new LinkedHashMap<>();
    }

    /**
     * Inject the hosted ASK confirmation callback.
     *
     * @param fn callback returning a {@link PermissionConfirmResponse}, or {@code null} to clear
     * @since 0.1.15
     */
    public void setRequestPermissionConfirmationFn(
            Function<PermissionConfirmationRequest, PermissionConfirmResponse> fn) {
        this.requestPermissionConfirmationFn = fn;
    }

    /**
     * Request user confirmation for an ASK decision via the hosted callback.
     *
     * <p>When a callback is set it is delegated to and its response returned. When no
     * callback is set this returns {@code null}, signalling the rail to fall back to the
     * built-in {@code ConfirmPayload} interrupt/resume path (mirrors Python's
     * {@code "interrupt"} literal).
     *
     * @param request confirmation request payload
     * @return the confirmation response, or {@code null} when no callback is set
     * @since 0.1.15
     */
    public PermissionConfirmResponse requestPermissionConfirmation(PermissionConfirmationRequest request) {
        if (requestPermissionConfirmationFn == null) {
            logger.debug("[ToolPermissionHost] request_permission_confirmation.no_callback fallback=interrupt");
            return null;
        }
        return requestPermissionConfirmationFn.apply(request);
    }

    /**
     * Persist an already-merged {@code permissions} snapshot to the agent YAML.
     *
     * <p>Delegates to {@link PermissionsYamlWriter#write}, which loads the target agent
     * config, replaces only the top-level {@code permissions} section and atomically
     * rewrites it. A {@code null} or missing YAML path yields {@code false} without
     * leaving a half-written file, mirroring Python's
     * {@code write_permissions_section_to_agent_config_yaml}.
     *
     * @param snapshot merged {@code permissions} section to write
     * @return {@code true} when the file was rewritten successfully
     * @since 0.1.15
     */
    public boolean persistAllowRule(Map<String, Object> snapshot) {
        return PermissionsYamlWriter.write(permissionYamlPath(), snapshot);
    }

    /**
     * persistAllowRule.
     *
     * <p>Legacy entrypoint retained for backward compatibility: it performs the simple
     * {@code tools.<toolName> = allow} merge over the live snapshot, delegates the disk
     * write to {@link #persistAllowRule(Map)}, and returns the merged snapshot. Callers
     * needing the full shell/file-guard merge should build the snapshot with
     * {@link PermissionsYamlWriter#mergeAllowRule} and call the map overload directly.
     *
     * @param toolName toolName
     * @param toolArgs toolArgs
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> persistAllowRule(String toolName, Map<String, Object> toolArgs) {
        Map<String, Object> snapshot = new LinkedHashMap<>(getPermissionsSnapshot());
        Object currentToolsRaw = snapshot.getOrDefault("tools", new LinkedHashMap<>());
        Map<String, Object> currentTools;
        if (currentToolsRaw instanceof Map<?, ?> m) {
            currentTools = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                currentTools.put(String.valueOf(e.getKey()), e.getValue());
            }
        } else {
            currentTools = new LinkedHashMap<>();
        }
        snapshot.put("tools", currentTools);
        currentTools.put(toolName, "allow");
        persistAllowRule(snapshot);
        return snapshot;
    }
}
