/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Operation registry that keeps mode/name to operation-definition mappings.
 *
 * <p>Mirrors Python's {@code OperationRegistry} in
 * {@code openjiuwen/core/sys_operation/registry.py}.</p>
 */
public final class OperationRegistry {

    private static final Map<OperationMode, Map<String, OperationDef>> REPOSITORY = new EnumMap<>(OperationMode.class);
    private static final Map<OperationMode, Set<String>> CUSTOM_OPERATION_NAMES = new EnumMap<>(OperationMode.class);
    private static boolean testIsolationEnabled;

    private OperationRegistry() {
    }

    public static synchronized void register(Class<? extends BaseOperation> operationClass) {
        register(operationClass, null, null, null);
    }

    public static synchronized void register(Class<? extends BaseOperation> operationClass,
                                             String name,
                                             OperationMode mode,
                                             String description) {
        OperationDef classDef = readClassOperationDef(operationClass);
        String resolvedName = name != null ? name : classDef != null ? classDef.name() : null;
        OperationMode resolvedMode = mode != null ? mode : classDef != null ? classDef.mode() : null;
        String resolvedDescription = description != null
                ? description
                : classDef != null ? classDef.description() : "";

        if (resolvedName == null || resolvedName.isBlank() || resolvedMode == null) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_REGISTRY_ERROR,
                    "process",
                    "register",
                    "error_msg",
                    "Operation name and mode must be provided for " + operationClass.getSimpleName()
            );
        }

        loadBuiltInOperation(resolvedMode);
        Map<String, OperationDef> modeRepository = REPOSITORY.computeIfAbsent(
                resolvedMode,
                ignored -> new LinkedHashMap<>()
        );
        OperationDef newDef = new OperationDef(operationClass, resolvedDescription, resolvedName, resolvedMode);
        OperationDef existingDef = modeRepository.get(resolvedName);
        if (newDef.equals(existingDef)) {
            return;
        }
        modeRepository.put(resolvedName, newDef);
        if (!isBuiltInOperationClass(operationClass)) {
            CUSTOM_OPERATION_NAMES.computeIfAbsent(resolvedMode, ignored -> new LinkedHashSet<>()).add(resolvedName);
        }
    }

    public static synchronized OperationDef getOperationInfo(String name, OperationMode mode) {
        loadBuiltInOperation(mode);
        return REPOSITORY.getOrDefault(mode, Map.of()).get(name);
    }

    public static synchronized List<String> getSupportedOperations(OperationMode mode) {
        loadBuiltInOperation(mode);
        List<String> names = new ArrayList<>(REPOSITORY.getOrDefault(mode, Map.of()).keySet());
        names.sort(Comparator.naturalOrder());
        return names;
    }

    static synchronized void clearForTest() {
        REPOSITORY.clear();
        CUSTOM_OPERATION_NAMES.clear();
        testIsolationEnabled = true;
    }

    static synchronized List<String> getToolExtractionOperationNames(OperationMode mode) {
        if (testIsolationEnabled) {
            Set<String> customNames = CUSTOM_OPERATION_NAMES.get(mode);
            if (customNames != null && !customNames.isEmpty()) {
                List<String> names = new ArrayList<>(customNames);
                names.sort(Comparator.naturalOrder());
                return names;
            }
        }
        return getSupportedOperations(mode);
    }

    public static OperationDef operationDef(Class<? extends BaseOperation> operationClass,
                                            String name,
                                            OperationMode mode,
                                            String description) {
        return new OperationDef(operationClass, description != null ? description : "", name, mode);
    }

    private static OperationDef readClassOperationDef(Class<? extends BaseOperation> operationClass) {
        try {
            Field field = operationClass.getField("OP_DEF");
            Object value = field.get(null);
            return value instanceof OperationDef operationDef ? operationDef : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static boolean isBuiltInOperationClass(Class<? extends BaseOperation> operationClass) {
        String name = operationClass.getName();
        return name.startsWith("com.openjiuwen.core.sys_operation.local.")
                || name.startsWith("com.openjiuwen.core.sys_operation.sandbox.");
    }

    private static void loadBuiltInOperation(OperationMode mode) {
        REPOSITORY.computeIfAbsent(mode, ignored -> new LinkedHashMap<>());
        String[] classNames = mode == OperationMode.LOCAL
                ? new String[]{
                    "com.openjiuwen.core.sys_operation.local.LocalFsOperation",
                    "com.openjiuwen.core.sys_operation.local.LocalShellOperation",
                    "com.openjiuwen.core.sys_operation.local.LocalCodeOperation"
                }
                : new String[]{
                    "com.openjiuwen.core.sys_operation.sandbox.SandboxFsOperation",
                    "com.openjiuwen.core.sys_operation.sandbox.SandboxShellOperation",
                    "com.openjiuwen.core.sys_operation.sandbox.SandboxCodeOperation"
                };
        for (String className : classNames) {
            try {
                Class<?> loadedClass = Class.forName(className);
                if (BaseOperation.class.isAssignableFrom(loadedClass)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends BaseOperation> operationClass = (Class<? extends BaseOperation>) loadedClass;
                    OperationDef operationDef = readClassOperationDef(operationClass);
                    if (operationDef != null && operationDef.mode() == mode) {
                        REPOSITORY.get(mode).putIfAbsent(operationDef.name(), operationDef);
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // Built-in operation implementation is owned by its own translation task.
            }
        }
    }
}
