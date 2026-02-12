// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation;

import com.openjiuwen.core.sysoperation.base.BaseOperation;
import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.config.LocalWorkConfig;
import com.openjiuwen.core.sysoperation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysoperation.registry.OperationInfo;
import com.openjiuwen.core.sysoperation.registry.OperationRegistry;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main entry point for system operations.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.sys_operation.SysOperation
 * 
 * <p>SysOperation provides access to various system operations:
 * <ul>
 *   <li>{@link #fs()} - File system operations (read, write, list, etc.)</li>
 *   <li>{@link #code()} - Code execution operations (Python, JavaScript)</li>
 *   <li>{@link #shell()} - Shell command operations</li>
 * </ul>
 * 
 * <p>Operation instances are created lazily and cached for reuse.
 * The actual implementation depends on the mode (LOCAL or SANDBOX).
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class SysOperation {

    /**
     * The operation mode (LOCAL or SANDBOX).
     */
    private final OperationMode mode;

    /**
     * The run configuration (LocalWorkConfig or SandboxGatewayConfig).
     */
    private final Object runConfig;

    /**
     * Cache for operation instances.
     */
    private final Map<String, BaseOperation> instances = new ConcurrentHashMap<>();

    /**
     * Constructs a SysOperation from a card.
     * 
     * @param card the configuration card
     */
    public SysOperation(SysOperationCard card) {
        this.mode = card.getMode();
        if (this.mode == OperationMode.LOCAL) {
            this.runConfig = card.getWorkConfig() != null ? card.getWorkConfig() : new LocalWorkConfig();
        } else {
            this.runConfig = card.getGatewayConfig() != null ? card.getGatewayConfig() : new SandboxGatewayConfig();
        }
    }

    /**
     * Gets the operation mode.
     * 
     * @return the operation mode
     */
    public OperationMode getMode() {
        return mode;
    }

    /**
     * Gets the run configuration.
     * 
     * @return the run configuration
     */
    public Object getRunConfig() {
        return runConfig;
    }

    /**
     * Gets the file system operation.
     * 
     * @return the file system operation instance, or null if not available
     */
    public BaseOperation fs() {
        return getOperation("fs");
    }

    /**
     * Gets the code execution operation.
     * 
     * @return the code execution operation instance, or null if not available
     */
    public BaseOperation code() {
        return getOperation("code");
    }

    /**
     * Gets the shell command operation.
     * 
     * @return the shell command operation instance, or null if not available
     */
    public BaseOperation shell() {
        return getOperation("shell");
    }

    /**
     * Gets an operation by name.
     * 
     * <p>This method first checks the cache, then looks up the operation
     * in the registry and creates a new instance if needed.
     * 
     * @param name the operation name (e.g., "fs", "code", "shell")
     * @return the operation instance, or null if not found
     */
    public BaseOperation getOperation(String name) {
        // Check cache first
        BaseOperation cached = instances.get(name);
        if (cached != null) {
            return cached;
        }

        // Look up in registry
        Optional<OperationInfo> infoOpt = OperationRegistry.getOperationInfo(name, mode);
        if (infoOpt.isEmpty()) {
            return null;
        }

        OperationInfo info = infoOpt.get();
        Class<? extends BaseOperation> operationClass = info.getOperationClass();
        String description = info.getDescription();

        // Validate that operationClass is a proper class
        if (operationClass == null || !BaseOperation.class.isAssignableFrom(operationClass)) {
            return null;
        }

        // Create instance
        BaseOperation instance = createInstance(operationClass, name, description);
        if (instance != null) {
            instances.put(name, instance);
        }
        return instance;
    }

    /**
     * Creates an instance of the operation class.
     * 
     * @param operationClass the operation class
     * @param name the operation name
     * @param description the operation description
     * @return the created instance, or null if creation fails
     */
    private BaseOperation createInstance(Class<? extends BaseOperation> operationClass,
                                         String name, String description) {
        try {
            // Look for constructor: (String name, OperationMode mode, String description, Object runConfig)
            Constructor<? extends BaseOperation> constructor = operationClass.getConstructor(
                String.class, OperationMode.class, String.class, Object.class
            );
            return constructor.newInstance(name, mode, description, runConfig);
        } catch (Exception e) {
            // Try alternative constructor patterns if needed
            return null;
        }
    }

    /**
     * Checks if an operation instance is cached.
     * 
     * @param name the operation name
     * @return true if the instance is cached
     */
    public boolean hasInstance(String name) {
        return instances.containsKey(name);
    }

    @Override
    public String toString() {
        return "SysOperation{" +
            "mode=" + mode +
            ", runConfig=" + runConfig +
            ", instances=" + instances.keySet() +
            '}';
    }
}

