/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import java.lang.reflect.Constructor;

/**
 * Definition and factory for a sys operation.
 *
 * <p>Mirrors Python's {@code OperationDef} in
 * {@code openjiuwen/core/sys_operation/registry.py}.</p>
 */
public record OperationDef(
        Class<? extends BaseOperation> operationClass,
        String description,
        String name,
        OperationMode mode) {

    public BaseOperation createInstance(Object runConfig) {
        try {
            Constructor<? extends BaseOperation> constructor = operationClass.getConstructor(
                    String.class,
                    OperationMode.class,
                    String.class,
                    Object.class
            );
            return constructor.newInstance(name, mode, description, runConfig);
        } catch (ReflectiveOperationException ignored) {
            // Try the declared constructor used by strongly typed implementations.
        }

        for (Constructor<?> rawConstructor : operationClass.getConstructors()) {
            Class<?>[] parameterTypes = rawConstructor.getParameterTypes();
            if (parameterTypes.length == 4
                    && parameterTypes[0] == String.class
                    && parameterTypes[1] == OperationMode.class
                    && parameterTypes[2] == String.class
                    && (runConfig == null || parameterTypes[3].isInstance(runConfig))) {
                try {
                    return (BaseOperation) rawConstructor.newInstance(name, mode, description, runConfig);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot instantiate operation " + operationClass.getName(),
                            exception);
                }
            }
        }
        throw new IllegalStateException("No compatible constructor found for " + operationClass.getName());
    }
}
