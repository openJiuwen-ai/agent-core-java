/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Host injection for permission system: permission snapshot, host-side confirmation,
 * persistence, and workspace path resolution.
 *
 * <p>Injected by Agent service or CLI when constructing DeepAgent / PermissionInterruptRail.
 *
 * <p>Mirrors Python's {@code ToolPermissionHost} in
 * {@code openjiuwen.harness.security.host}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolPermissionHost {

    /**
     * Returns a dict with the same structure as config['permissions'],
     * used for hot-syncing disk configuration.
     */
    private Supplier<Map<String, Object>> getPermissionsSnapshot;

    /**
     * Custom "always allow" write to disk.
     * Input is the merged permissions dict (with external_directory etc.).
     * Returns false to trigger rollback of memory config.
     * When unset, uses writePermissionsSectionToAgentConfigYaml.
     */
    private Function<Map<String, Object>, Boolean> persistAllowRule;

    /**
     * Workspace root directory for external path validation.
     */
    private Supplier<Path> resolveWorkspaceDir;

    /**
     * Agent config file path; used for permission section persistence.
     * File may not exist, but parent directory must exist.
     */
    private Path permissionYamlPath;

    /**
     * Whether permission checks are active.
     * When false, all tool calls pass without permission checks.
     */
    private Supplier<Boolean> toolPermissionChecksActive;

    /**
     * Host scene hook: intercept before generic tiered evaluation (e.g., digital twin / owner_scopes).
     * Returns null to continue tiered evaluation;
     * returns "approve" to approve directly;
     * returns "reject" with message to deny.
     */
    private Function<PermissionSceneHookInput, CompletableFuture<SceneHookOutput>> permissionSceneHook;

    /**
     * Request user confirmation for PermissionLevel.ASK.
     * Returns PermissionConfirmResponse (same semantics as internal interrupt recovery);
     * returns "interrupt" to fallback to internal ConfirmInterrupt flow;
     * returns null when host confirmation failed (tool call will be rejected).
     */
    private Function<PermissionConfirmationRequest, CompletableFuture<ConfirmationResult>> requestPermissionConfirmation;

    /**
     * Result types for hooks.
     */
    public enum ConfirmationResultType {
        RESPONSE,
        INTERRUPT,
        FAILED
    }

    /**
     * Confirmation result wrapper.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmationResult {
        private ConfirmationResultType type;
        private PermissionConfirmResponse response;

        public static ConfirmationResult response(PermissionConfirmResponse response) {
            return ConfirmationResult.builder()
                    .type(ConfirmationResultType.RESPONSE)
                    .response(response)
                    .build();
        }

        public static ConfirmationResult interrupt() {
            return ConfirmationResult.builder()
                    .type(ConfirmationResultType.INTERRUPT)
                    .build();
        }

        public static ConfirmationResult failed() {
            return ConfirmationResult.builder()
                    .type(ConfirmationResultType.FAILED)
                    .build();
        }
    }

    /**
     * Scene hook output wrapper.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SceneHookOutput {
        private String action; // "approve" or "reject"
        private String message;

        public static SceneHookOutput approve() {
            return SceneHookOutput.builder()
                    .action("approve")
                    .build();
        }

        public static SceneHookOutput reject(String message) {
            return SceneHookOutput.builder()
                    .action("reject")
                    .message(message != null ? message : "[PERMISSION_DENIED]")
                    .build();
        }
    }

    /**
     * Create a default host with no hooks.
     */
    public static ToolPermissionHost defaultHost() {
        return ToolPermissionHost.builder().build();
    }
}