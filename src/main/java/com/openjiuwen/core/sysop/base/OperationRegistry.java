/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.base;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Operation registry that manages operations by mode and name.
 *
 * <p>Mirrors Python's {@code OperationRegistry} in
 * {@code openjiuwen.core.sys_operation.registry}.</p>
 */
public class OperationRegistry {

    /** Storage: mode -> name -> OperationDef. */
    private static final Map<OperationMode, Map<String, OperationDef>> repository = new HashMap<>();

    /**
     * Register an operation.
     *
     * @param operationCls the class implementing the operation logic
     * @param name         unique identifier for the operation
     * @param mode         running mode
     * @param description  human-readable description
     */
    public static void register(
            Class<? extends BaseOperation> operationCls,
            String name,
            OperationMode mode,
            String description
    ) {
        if (name == null) {
            throw new IllegalArgumentException("Operation name is required");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Operation mode is required");
        }

        OperationDef def = OperationDef.builder()
                .cls(operationCls)
                .name(name)
                .mode(mode)
                .description(description != null ? description : "")
                .build();

        repository.computeIfAbsent(mode, k -> new HashMap<>()).put(name, def);
    }

    /**
     * Get operation definition by mode and name.
     *
     * @param mode operation mode
     * @param name operation name
     * @return operation definition, or null if not found
     */
    public static OperationDef get(OperationMode mode, String name) {
        Map<String, OperationDef> modeMap = repository.get(mode);
        if (modeMap == null) {
            return null;
        }
        return modeMap.get(name);
    }

    /**
     * Check if operation exists.
     *
     * @param mode operation mode
     * @param name operation name
     * @return true if operation exists
     */
    public static boolean exists(OperationMode mode, String name) {
        return get(mode, name) != null;
    }

    /**
     * Get all operations for a mode.
     *
     * @param mode operation mode
     * @return list of operation definitions
     */
    public static List<OperationDef> listByMode(OperationMode mode) {
        Map<String, OperationDef> modeMap = repository.get(mode);
        if (modeMap == null) {
            return List.of();
        }
        return List.copyOf(modeMap.values());
    }

    /**
     * Clear all registered operations.
     */
    public static void clear() {
        repository.clear();
    }
}