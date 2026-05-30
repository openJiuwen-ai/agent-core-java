/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.ContainerScope;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.config.SandboxIsolationConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.registry.OperationDef;
import com.openjiuwen.core.sysop.registry.OperationRegistry;
import com.openjiuwen.core.sysop.sandbox.SandboxRunConfig;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SysOperation — facade for accessing system operations.
 * <p>
 * Mirrors Python's {@code SysOperation} class in {@code sys_operation/sys_operation.py}.
 *
 * <p>Usage:
 * <pre>
 *   SysOperationCard card = SysOperationCard.builder()
 *       .id("sys_op")
 *       .mode(OperationMode.LOCAL)
 *       .build();
 *   SysOperation sysOp = new SysOperation(card);
 *
 *   // Access operations
 *   BaseFsOperation fs = sysOp.fs();
 *   BaseShellOperation shell = sysOp.shell();
 *   BaseCodeOperation code = sysOp.code();
 * </pre>
 */
public class SysOperation {

    private static final String TEMPLATE_SESSION_PLACEHOLDER = "{session_id}";

    private final OperationMode mode;
    private final Object runConfig;
    private final Map<String, BaseOperation> instances = new ConcurrentHashMap<>();

    public SysOperation(SysOperationCard card) {
        this.mode = card.getMode() != null ? card.getMode() : OperationMode.LOCAL;
        if (this.mode == OperationMode.LOCAL) {
            this.runConfig = card.getWorkConfig() != null ? card.getWorkConfig() : new LocalWorkConfig();
        } else {
            SandboxGatewayConfig gatewayConfig = validateSandboxGatewayConfig(card.getGatewayConfig());
            this.runConfig = new SandboxRunConfig(
                    gatewayConfig,
                    generateIsolationKeyTemplate(
                            gatewayConfig.getIsolation().getPrefix(),
                            gatewayConfig.getIsolation().getContainerScope(),
                            gatewayConfig.getIsolation().getCustomId(),
                            gatewayConfig.getLauncherConfig().getLauncherType(),
                            gatewayConfig.getLauncherConfig().getSandboxType()));
        }
    }

    private static SandboxGatewayConfig validateSandboxGatewayConfig(SandboxGatewayConfig gatewayConfig) {
        SandboxGatewayConfig config = gatewayConfig != null ? gatewayConfig : new SandboxGatewayConfig();
        SandboxLauncherConfig launcherConfig = config.getLauncherConfig();
        if (launcherConfig == null) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_CARD_PARAM_ERROR,
                    "error_msg", "sandbox mode requires launcher_config");
        }
        if (launcherConfig.getLauncherType() == null || launcherConfig.getLauncherType().isBlank()) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_CARD_PARAM_ERROR,
                    "error_msg", "sandbox mode requires launcher_type");
        }
        if (launcherConfig.getSandboxType() == null || launcherConfig.getSandboxType().isBlank()) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_CARD_PARAM_ERROR,
                    "error_msg", "sandbox mode requires sandbox_type");
        }
        if (config.getIsolation() == null) {
            config.setIsolation(new SandboxIsolationConfig());
        }
        return config;
    }

    private static String generateIsolationKeyTemplate(String isolationPrefix, ContainerScope containerScope,
                                                       String customId, String launcherType, String sandboxType) {
        String prefix = isolationPrefix == null || isolationPrefix.isBlank() ? "" : isolationPrefix + "_";
        ContainerScope scope = containerScope != null ? containerScope : ContainerScope.SESSION;

        String identity;
        if (scope == ContainerScope.SYSTEM) {
            identity = "system";
        } else if (scope == ContainerScope.CUSTOM) {
            if (customId == null || customId.isBlank()) {
                throw new IllegalArgumentException("container_scope is CUSTOM but custom_id is null");
            }
            identity = customId;
        } else if (scope == ContainerScope.SESSION) {
            identity = TEMPLATE_SESSION_PLACEHOLDER;
        } else {
            identity = "default";
        }

        return scope.getValue() + "_" + launcherType + "_" + sandboxType + "_" + prefix + identity;
    }

    /**
     * Get the file system operation instance.
     */
    public BaseFsOperation fs() {
        return (BaseFsOperation) getOperation("fs");
    }

    /**
     * Get the code execution operation instance.
     */
    public BaseCodeOperation code() {
        return (BaseCodeOperation) getOperation("code");
    }

    /**
     * Get the shell command operation instance.
     */
    public BaseShellOperation shell() {
        return (BaseShellOperation) getOperation("shell");
    }

    /**
     * Get an operation by name. Returns null if the operation is not registered.
     *
     * @param name operation name (e.g., "fs", "shell", "code")
     * @return the operation instance, or null
     */
    public BaseOperation getOperation(String name) {
        return instances.computeIfAbsent(name, n -> {
            Optional<OperationDef> def = OperationRegistry.getOperationInfo(n, mode);
            return def.map(operationDef -> operationDef.createInstance(runConfig)).orElse(null);
        });
    }

    public OperationMode getMode() {
        return mode;
    }

    public String getWorkDir() {
        if (runConfig instanceof LocalWorkConfig localWorkConfig) {
            return localWorkConfig.getWorkDir();
        }
        return null;
    }
}
