// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.registry;

import com.openjiuwen.core.sysoperation.base.BaseOperation;

/**
 * Holds information about a registered operation class.
 * 
 * <p>对应 Python: dict with "cls" and "description" keys in OperationRegistry
 * 
 * <p>This class encapsulates the operation class and its description
 * as stored in the OperationRegistry.
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class OperationInfo {

    /**
     * The operation class.
     */
    private final Class<? extends BaseOperation> operationClass;

    /**
     * Human-readable description of the operation.
     */
    private final String description;

    /**
     * Constructs an OperationInfo with the specified class and description.
     * 
     * @param operationClass the operation class
     * @param description the operation description
     */
    public OperationInfo(Class<? extends BaseOperation> operationClass, String description) {
        this.operationClass = operationClass;
        this.description = description != null ? description : "";
    }

    /**
     * Gets the operation class.
     * 
     * @return the operation class
     */
    public Class<? extends BaseOperation> getOperationClass() {
        return operationClass;
    }

    /**
     * Gets the operation description.
     * 
     * @return the operation description
     */
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "OperationInfo{" +
            "operationClass=" + operationClass.getName() +
            ", description='" + description + '\'' +
            '}';
    }
}

