/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.registry.OperationDef;
import com.openjiuwen.core.sysop.registry.OperationRegistry;

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

    private final OperationMode mode;
    private final Object runConfig;
    private final Map<String, BaseOperation> instances = new ConcurrentHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public SysOperation(SysOperationCard card) {
        this.mode = card.getMode() != null ? card.getMode() : OperationMode.LOCAL;
        if (this.mode == OperationMode.LOCAL) {
            this.runConfig = card.getWorkConfig() != null ? card.getWorkConfig() : new LocalWorkConfig();
        } else {
            this.runConfig = validateSandboxGatewayConfig(card.getGatewayConfig());
        }
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public OperationMode getMode() {
        return mode;
    }

    private static SandboxGatewayConfig validateSandboxGatewayConfig(SandboxGatewayConfig gatewayConfig) {
        SandboxGatewayConfig config = gatewayConfig != null ? gatewayConfig : new SandboxGatewayConfig();
        if (config.getLauncherConfig() == null) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_CARD_PARAM_ERROR,
                    "error_msg", "sandbox mode requires launcher_config"
            );
        }
        if (config.getLauncherConfig().getLauncherType() == null
                || config.getLauncherConfig().getLauncherType().isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_CARD_PARAM_ERROR,
                    "error_msg", "sandbox mode requires launcher_type"
            );
        }
        if (config.getLauncherConfig().getSandboxType() == null
                || config.getLauncherConfig().getSandboxType().isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_CARD_PARAM_ERROR,
                    "error_msg", "sandbox mode requires sandbox_type"
            );
        }
        return config;
    }
}
