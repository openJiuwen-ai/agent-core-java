/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Host-injected permission coordination hooks.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/security/host.py}.</p>
 */
public final class ToolPermissionHost {

    private Supplier<Map<String, Object>> permissionsSnapshotSupplier;
    private PersistAllowRuleHook persistAllowRuleHook;
    private Supplier<Path> workspaceDirResolver;
    private Path permissionYamlPath;
    private BooleanSupplier toolPermissionChecksActiveSupplier;
    private RequestPermissionConfirmationHook requestPermissionConfirmationHook;
    private PermissionSceneHook permissionSceneHook;

    public Supplier<Map<String, Object>> getPermissionsSnapshotSupplier() {
        return permissionsSnapshotSupplier;
    }

    public void setPermissionsSnapshotSupplier(Supplier<Map<String, Object>> permissionsSnapshotSupplier) {
        this.permissionsSnapshotSupplier = permissionsSnapshotSupplier;
    }

    public PersistAllowRuleHook getPersistAllowRuleHook() {
        return persistAllowRuleHook;
    }

    public void setPersistAllowRuleHook(PersistAllowRuleHook persistAllowRuleHook) {
        this.persistAllowRuleHook = persistAllowRuleHook;
    }

    public Supplier<Path> getWorkspaceDirResolver() {
        return workspaceDirResolver;
    }

    public void setWorkspaceDirResolver(Supplier<Path> workspaceDirResolver) {
        this.workspaceDirResolver = workspaceDirResolver;
    }

    public Path getPermissionYamlPath() {
        return permissionYamlPath;
    }

    public void setPermissionYamlPath(Path permissionYamlPath) {
        this.permissionYamlPath = permissionYamlPath;
    }

    public BooleanSupplier getToolPermissionChecksActiveSupplier() {
        return toolPermissionChecksActiveSupplier;
    }

    public void setToolPermissionChecksActiveSupplier(BooleanSupplier toolPermissionChecksActiveSupplier) {
        this.toolPermissionChecksActiveSupplier = toolPermissionChecksActiveSupplier;
    }

    public RequestPermissionConfirmationHook getRequestPermissionConfirmationHook() {
        return requestPermissionConfirmationHook;
    }

    public void setRequestPermissionConfirmationHook(
            RequestPermissionConfirmationHook requestPermissionConfirmationHook
    ) {
        this.requestPermissionConfirmationHook = requestPermissionConfirmationHook;
    }

    public PermissionSceneHook getPermissionSceneHook() {
        return permissionSceneHook;
    }

    public void setPermissionSceneHook(PermissionSceneHook permissionSceneHook) {
        this.permissionSceneHook = permissionSceneHook;
    }

    public record PermissionSceneHookInput(
            Object ctx,
            Object toolCall,
            Object userInput,
            String normalizedToolName,
            Map<String, Object> toolArgs,
            Object engine
    ) {
    }

    public record PermissionSceneDecision(String action, String message) {
    }

    public record PermissionConfirmationRequest(
            Object ctx,
            Object toolCall,
            PermissionResult result,
            String autoConfirmKey
    ) {
    }

    public sealed interface PermissionConfirmationResult
            permits InterruptPermissionConfirmationResult, PermissionConfirmResponseWrapper {
    }

    public record InterruptPermissionConfirmationResult() implements PermissionConfirmationResult {
    }

    public record PermissionConfirmResponseWrapper(PermissionConfirmResponse response)
            implements PermissionConfirmationResult {
    }

    @FunctionalInterface
    public interface PermissionSceneHook {
        CompletionStage<PermissionSceneDecision> apply(PermissionSceneHookInput input);
    }

    @FunctionalInterface
    public interface RequestPermissionConfirmationHook {
        CompletionStage<PermissionConfirmationResult> apply(PermissionConfirmationRequest request);
    }

    @FunctionalInterface
    public interface PersistAllowRuleHook {
        boolean apply(Map<String, Object> permissions);
    }
}
