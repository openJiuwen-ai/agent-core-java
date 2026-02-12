// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.base;

import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.Collections;
import java.util.List;

/**
 * Base class for file, code, shell and other operations.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.base.BaseOperation
 * 
 * <p>This abstract class serves as the base for all specific operation types:
 * <ul>
 *   <li>File system operations (read, write, list, etc.)</li>
 *   <li>Code execution operations (Python, JavaScript, etc.)</li>
 *   <li>Shell command operations</li>
 * </ul>
 * 
 * <p>Each operation has:
 * <ul>
 *   <li>{@code name} - Unique identifier (e.g., "fs", "code", "shell")</li>
 *   <li>{@code mode} - Running mode (LOCAL or SANDBOX)</li>
 *   <li>{@code description} - Human-readable description</li>
 *   <li>{@code runConfig} - Configuration for the specific mode</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public abstract class BaseOperation {

    /**
     * Unique identifier for the operation.
     */
    protected final String name;

    /**
     * Running mode (LOCAL or SANDBOX).
     */
    protected final OperationMode mode;

    /**
     * Human-readable description of the operation.
     */
    protected final String description;

    /**
     * Configuration for the specific mode (LocalWorkConfig or SandboxGatewayConfig).
     */
    protected final Object runConfig;

    /**
     * Constructs a BaseOperation with the specified parameters.
     * 
     * @param name unique identifier for the operation
     * @param mode running mode (LOCAL or SANDBOX)
     * @param description human-readable description
     * @param runConfig configuration for the specific mode
     */
    public BaseOperation(String name, OperationMode mode, String description, Object runConfig) {
        this.name = name;
        this.mode = mode;
        this.description = description != null ? description : "";
        this.runConfig = runConfig;
    }

    /**
     * Gets the operation name.
     * 
     * @return the operation name
     */
    public String getName() {
        return name;
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
     * Gets the operation description.
     * 
     * @return the operation description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the run configuration.
     * 
     * @return the run configuration object
     */
    public Object getRunConfig() {
        return runConfig;
    }

    /**
     * Lists the tools provided by this operation.
     * 
     * <p>Subclasses should override this method to return the list of
     * ToolCards representing the tools available for this operation.
     * 
     * @return list of ToolCard objects, empty list by default
     */
    public List<ToolCard> listTools() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
            "name='" + name + '\'' +
            ", mode=" + mode +
            ", description='" + description + '\'' +
            '}';
    }
}

