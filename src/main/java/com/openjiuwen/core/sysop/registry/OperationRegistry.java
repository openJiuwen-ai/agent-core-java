/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.registry;

import com.openjiuwen.core.sysop.BaseOperation;
import com.openjiuwen.core.sysop.OperationMode;

import java.util.Optional;

/**
 * Backward-compatible operation registry bridge for the moved sys-operation package.
 *
 * <p>Mirrors Python's {@code OperationRegistry} in
 * {@code openjiuwen/core/sys_operation/registry.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.OperationRegistry}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public final class OperationRegistry {

    private OperationRegistry() {
    }

    /**
     * Get operation definition by name and mode.
     *
     * @param name operation name
     * @param mode operation mode
     * @return operation definition if found
     */
    public static Optional<OperationDef> getOperationInfo(String name, OperationMode mode) {
        com.openjiuwen.core.sys_operation.OperationMode newMode = mode.toNewMode();
        com.openjiuwen.core.sys_operation.OperationDef newDef =
                com.openjiuwen.core.sys_operation.OperationRegistry.getOperationInfo(name, newMode);
        if (newDef == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Class<? extends BaseOperation> compatClass =
                (Class<? extends BaseOperation>) (Class<?>) newDef.operationClass();
        return Optional.of(new OperationDef(
                compatClass,
                newDef.name(),
                mode,
                newDef.description()
        ));
    }

    /**
     * Register an operation class.
     *
     * @param operationClass operation class to register
     */
    public static void register(Class<? extends BaseOperation> operationClass) {
        @SuppressWarnings("unchecked")
        Class<? extends com.openjiuwen.core.sys_operation.BaseOperation> newClass =
                (Class<? extends com.openjiuwen.core.sys_operation.BaseOperation>) (Class<?>) operationClass;
        com.openjiuwen.core.sys_operation.OperationRegistry.register(
                newClass, null, null, null);
    }

    /**
     * Register an operation class with explicit parameters.
     *
     * @param operationClass operation class to register
     * @param name operation name
     * @param mode operation mode
     * @param description operation description
     */
    public static void register(Class<? extends BaseOperation> operationClass,
                                String name,
                                OperationMode mode,
                                String description) {
        com.openjiuwen.core.sys_operation.OperationMode newMode = mode != null ? mode.toNewMode() : null;
        @SuppressWarnings("unchecked")
        Class<? extends com.openjiuwen.core.sys_operation.BaseOperation> newClass =
                (Class<? extends com.openjiuwen.core.sys_operation.BaseOperation>) (Class<?>) operationClass;
        com.openjiuwen.core.sys_operation.OperationRegistry.register(newClass, name, newMode, description);
    }

    /**
     * Get supported operation names for a mode.
     *
     * @param mode operation mode
     * @return list of supported operation names
     */
    public static java.util.List<String> getSupportedOperations(OperationMode mode) {
        return com.openjiuwen.core.sys_operation.OperationRegistry.getSupportedOperations(mode.toNewMode());
    }
}
