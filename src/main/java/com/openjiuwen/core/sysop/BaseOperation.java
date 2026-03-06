/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.SysOperationEvent;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

import java.util.List;
import java.util.Map;

/**
 * Base class for all system operations (file, code, shell, etc.).
 * <p>
 * Mirrors Python's {@code BaseOperation} in {@code sys_operation/base.py}.
 */
public abstract class BaseOperation {

    protected static final LoggerProtocol logger = Loggers.SYS_OPERATION;

    private final String name;
    private final OperationMode mode;
    private final String description;
    private final Object runConfig;

    /**
     * Create a base operation.
     *
     * @param name        operation name
     * @param mode        operation mode (LOCAL or SANDBOX)
     * @param description human-readable description
     * @param runConfig   runtime configuration (LocalWorkConfig or SandboxGatewayConfig)
     */
    protected BaseOperation(String name, OperationMode mode, String description, Object runConfig) {
        this.name = name;
        this.mode = mode;
        this.description = description;
        this.runConfig = runConfig;
    }

    public String getName() {
        return name;
    }

    public OperationMode getMode() {
        return mode;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get the run configuration as LocalWorkConfig.
     */
    protected LocalWorkConfig getLocalConfig() {
        return (LocalWorkConfig) runConfig;
    }

    /**
     * Get the run configuration as SandboxGatewayConfig.
     */
    protected SandboxGatewayConfig getSandboxConfig() {
        return (SandboxGatewayConfig) runConfig;
    }

    protected Object getRunConfig() {
        return runConfig;
    }

    /**
     * Retrieve a list of tool cards describing available operations.
     *
     * @return list of ToolCard objects
     */
    public abstract List<ToolCard> listTools();

    /**
     * Generate tool cards for the specified method names using reflection.
     *
     * @param methodNames list of public method names to expose as tools
     * @return list of ToolCard objects
     */
    protected List<ToolCard> generateToolCards(List<String> methodNames) {
        return methodNames.stream()
                .map(methodName -> (ToolCard) ToolCard.builder()
                        .name(methodName)
                        .description(name + "." + methodName)
                        .build())
                .toList();
    }

    /**
     * Create a SysOperationEvent for logging.
     *
     * @param eventType       type of the system operation event
     * @param methodName      name of the method being logged
     * @param methodParams    parameters passed to the method
     * @param methodResult    results returned by the method
     * @param methodExecTimeMs execution time in milliseconds
     * @return created SysOperationEvent, or null
     */
    protected SysOperationEvent createSysOperationEvent(
            LogEventType eventType,
            String methodName,
            Map<String, Object> methodParams,
            Map<String, Object> methodResult,
            Double methodExecTimeMs) {
        return SysOperationEvent.builder()
                .eventType(eventType)
                .moduleId("sys_operation")
                .moduleName("sys_operation")
                .operationName(name)
                .operationMode(mode != null ? mode.getValue() : null)
                .operationDesc(description)
                .methodName(methodName)
                .methodParams(methodParams)
                .methodResult(methodResult)
                .methodExecTimeMs(methodExecTimeMs)
                .build();
    }
}
