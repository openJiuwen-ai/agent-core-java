/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Definition and factory for an operation.
 *
 * <p>Mirrors Python's {@code OperationDef} in
 * {@code openjiuwen.core.sys_operation.registry}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationDef {

    /** Operation class. */
    private Class<? extends BaseOperation> cls;

    /** Operation description. */
    private String description;

    /** Operation name. */
    private String name;

    /** Operation mode. */
    private OperationMode mode;

    /**
     * Create an operation instance with the given configuration.
     *
     * @param runConfig run configuration
     * @return new operation instance
     */
    public BaseOperation createInstance(Object runConfig) {
        try {
            return cls.getDeclaredConstructor(String.class, OperationMode.class, String.class, Object.class)
                    .newInstance(name, mode, description, runConfig);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create operation instance: " + name, e);
        }
    }
}