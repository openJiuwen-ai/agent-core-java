// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.registry;

import com.openjiuwen.core.sysoperation.base.BaseOperation;
import com.openjiuwen.core.sysoperation.base.OperationMode;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Operation registry, managing the operation classes.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.registry.OperationRegistry
 * 
 * <p>The registry stores operation classes indexed by name and mode.
 * It supports:
 * <ul>
 *   <li>Manual registration via {@link #register}</li>
 *   <li>Automatic registration via {@code @Operation} annotation</li>
 *   <li>Lazy loading of unregistered operations</li>
 * </ul>
 * 
 * <p>Module path convention for lazy loading:
 * {@code com.openjiuwen.core.sysoperation.{mode}.{Name}Operation}
 * 
 * <p>For example:
 * <ul>
 *   <li>fs operation in LOCAL mode: {@code com.openjiuwen.core.sysoperation.local.FsOperation}</li>
 *   <li>code operation in SANDBOX mode: {@code com.openjiuwen.core.sysoperation.sandbox.CodeOperation}</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public final class OperationRegistry {

    /**
     * The repository storing operations indexed by name and mode.
     * Structure: {name: {mode: OperationInfo}}
     */
    private static final Map<String, Map<OperationMode, OperationInfo>> REPOSITORY = new ConcurrentHashMap<>();

    private OperationRegistry() {
        // Utility class
    }

    /**
     * Registers an operation class to the repository.
     * 
     * @param operationClass the operation class
     * @param name unique identifier for the operation (e.g., "fs", "code", "shell")
     * @param mode running mode associated with the operation (LOCAL or SANDBOX)
     * @param description human-readable description of the operation
     */
    public static void register(Class<? extends BaseOperation> operationClass,
                                String name, OperationMode mode, String description) {
        REPOSITORY.computeIfAbsent(name, k -> new EnumMap<>(OperationMode.class))
            .put(mode, new OperationInfo(operationClass, description));
    }

    /**
     * Gets the operation info by the name and mode.
     * 
     * @param name unique identifier for the operation
     * @param mode running mode associated with the operation
     * @param autoLoad whether to automatically load unregistered modules (default: true)
     * @return Optional containing the operation info, or empty if not found
     */
    public static Optional<OperationInfo> getOperationInfo(String name, OperationMode mode, boolean autoLoad) {
        // 1. Get from registered repository first
        Map<OperationMode, OperationInfo> modeMap = REPOSITORY.get(name);
        OperationInfo operationInfo = modeMap != null ? modeMap.get(mode) : null;

        // 2. Lazy load module if not found and autoLoad is enabled
        if (operationInfo == null && autoLoad) {
            lazyLoadOperation(name, mode);
            // Re-fetch after lazy loading
            modeMap = REPOSITORY.get(name);
            operationInfo = modeMap != null ? modeMap.get(mode) : null;
        }

        return Optional.ofNullable(operationInfo);
    }

    /**
     * Gets the operation info by the name and mode with auto-load enabled.
     * 
     * @param name unique identifier for the operation
     * @param mode running mode associated with the operation
     * @return Optional containing the operation info, or empty if not found
     */
    public static Optional<OperationInfo> getOperationInfo(String name, OperationMode mode) {
        return getOperationInfo(name, mode, true);
    }

    /**
     * Lazy loads an operation module for specific name and mode.
     * 
     * <p>The module path follows the convention:
     * {@code com.openjiuwen.core.sysoperation.{mode.value}.{CapitalizedName}Operation}
     * 
     * @param name operation name (e.g., "fs", "code", "shell")
     * @param mode running mode (OperationMode enum)
     */
    private static void lazyLoadOperation(String name, OperationMode mode) {
        // Convert name to class name (e.g., "fs" -> "Fs", "code" -> "Code")
        String capitalizedName = name.substring(0, 1).toUpperCase() + name.substring(1);

        String modePkg = mode.getValue();
        String modePrefix = switch (mode) {
            case LOCAL -> "Local";
            case SANDBOX -> "Sandbox";
        };

        // Try legacy/classic naming first, then the actual project naming.
        String[] candidates = new String[]{
            String.format("com.openjiuwen.core.sysoperation.%s.%sOperation", modePkg, capitalizedName),
            String.format("com.openjiuwen.core.sysoperation.%s.%s%sOperation", modePkg, modePrefix, capitalizedName)
        };

        for (String className : candidates) {
            try {
                Class.forName(className);
                return;
            } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                // try next candidate
            }
        }
    }

    /**
     * Clears all registered operations.
     * 
     * <p>This method is primarily intended for testing purposes.
     */
    public static void clear() {
        REPOSITORY.clear();
    }

    /**
     * Checks if an operation is registered.
     * 
     * @param name the operation name
     * @param mode the operation mode
     * @return true if the operation is registered
     */
    public static boolean isRegistered(String name, OperationMode mode) {
        Map<OperationMode, OperationInfo> modeMap = REPOSITORY.get(name);
        return modeMap != null && modeMap.containsKey(mode);
    }
}

