/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.config.ContainerScope;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.config.SandboxIsolationConfig;
import com.openjiuwen.core.sys_operation.config.SandboxLauncherConfig;
import com.openjiuwen.core.sys_operation.sandbox.SandboxRunConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sys operation facade.
 *
 * <p>Mirrors Python's {@code SysOperation} and
 * {@code generate_isolation_key_template} in
 * {@code openjiuwen/core/sys_operation/sys_operation.py}.</p>
 */
public class SysOperation {

    public static final String TEMPLATE_SESSION_PLACEHOLDER = "{session_id}";

    private final String id;
    private final OperationMode mode;
    private final Object runConfig;
    private final Map<String, BaseOperation> instances = new LinkedHashMap<>();

    public SysOperation(SysOperationCard card) {
        this.id = card.getId();
        this.mode = card.getMode();
        if (mode == OperationMode.LOCAL) {
            this.runConfig = card.getWorkConfig() != null ? card.getWorkConfig() : new LocalWorkConfig();
        } else {
            SandboxGatewayConfig config = validateSandboxGatewayConfig(card.getGatewayConfig());
            SandboxIsolationConfig isolation = config.getIsolation() != null
                    ? config.getIsolation()
                    : new SandboxIsolationConfig();
            SandboxLauncherConfig launcher = config.getLauncherConfig();
            String isolationKeyTemplate = generateIsolationKeyTemplate(
                    isolation.getPrefix(),
                    isolation.getContainerScope(),
                    isolation.getCustomId(),
                    launcher.getLauncherType(),
                    launcher.getSandboxType()
            );
            this.runConfig = SandboxRunConfig.builder()
                    .config(config)
                    .isolationKeyTemplate(isolationKeyTemplate)
                    .build();
        }
    }

    public String getId() {
        return id;
    }

    public OperationMode getMode() {
        return mode;
    }

    public Object getRunConfig() {
        return runConfig;
    }

    public String getIsolationKeyTemplate() {
        if (runConfig instanceof SandboxRunConfig sandboxRunConfig) {
            return sandboxRunConfig.getIsolationKeyTemplate();
        }
        return null;
    }

    public BaseFsOperation fs() {
        return (BaseFsOperation) getOperation("fs");
    }

    public BaseCodeOperation code() {
        return (BaseCodeOperation) getOperation("code");
    }

    public BaseShellOperation shell() {
        return (BaseShellOperation) getOperation("shell");
    }

    public BaseOperation getOperation(String name) {
        if (instances.containsKey(name)) {
            return instances.get(name);
        }
        OperationDef operationDef = OperationRegistry.getOperationInfo(name, mode);
        if (operationDef == null) {
            return null;
        }
        BaseOperation instance = operationDef.createInstance(runConfig);
        instances.put(name, instance);
        return instance;
    }

    public static SandboxGatewayConfig validateSandboxGatewayConfig(SandboxGatewayConfig gatewayConfig) {
        SandboxGatewayConfig config = gatewayConfig != null ? gatewayConfig : new SandboxGatewayConfig();
        SandboxLauncherConfig launcherConfig = config.getLauncherConfig();
        if (launcherConfig == null) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_CARD_PARAM_ERROR,
                    "error_msg",
                    "sandbox mode requires launcher_config"
            );
        }
        if (launcherConfig.getLauncherType() == null || launcherConfig.getLauncherType().isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_CARD_PARAM_ERROR,
                    "error_msg",
                    "sandbox mode requires launcher_type"
            );
        }
        if (launcherConfig.getSandboxType() == null || launcherConfig.getSandboxType().isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_CARD_PARAM_ERROR,
                    "error_msg",
                    "sandbox mode requires sandbox_type"
            );
        }
        return config;
    }

    public static String generateIsolationKeyTemplate(String isolationPrefix,
                                                      ContainerScope containerScope,
                                                      String customId,
                                                      String launcherType,
                                                      String sandboxType) {
        String prefix = isolationPrefix == null || isolationPrefix.isBlank() ? "" : isolationPrefix + "_";
        ContainerScope resolvedScope = containerScope != null ? containerScope : ContainerScope.SESSION;
        String identity;
        if (resolvedScope == ContainerScope.SYSTEM) {
            identity = "system";
        } else if (resolvedScope == ContainerScope.CUSTOM) {
            if (customId == null || customId.isBlank()) {
                throw new IllegalArgumentException("container_scope is CUSTOM but custom_id is None");
            }
            identity = customId;
        } else if (resolvedScope == ContainerScope.SESSION) {
            identity = TEMPLATE_SESSION_PLACEHOLDER;
        } else {
            identity = "default";
        }
        String resolvedLauncherType = launcherType == null || launcherType.isBlank() ? "pre_deploy" : launcherType;
        String resolvedSandboxType = sandboxType == null || sandboxType.isBlank() ? "aio" : sandboxType;
        return resolvedScope.value() + "_" + resolvedLauncherType + "_" + resolvedSandboxType + "_" + prefix
                + identity;
    }
}
