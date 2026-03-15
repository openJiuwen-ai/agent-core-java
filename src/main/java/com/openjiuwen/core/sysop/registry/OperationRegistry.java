/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.registry;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.sysop.BaseOperation;
import com.openjiuwen.core.sysop.OperationMode;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Operation registry that manages operation definitions.
 * <p>
 * Mirrors Python's {@code OperationRegistry} in {@code sys_operation/registry.py}.
 * Built-in operations are loaded lazily when first accessed for a given mode.
 */
public final class OperationRegistry {

    private static final LoggerProtocol logger = Loggers.SYS_OPERATION;

    /** Storage: mode -> name -> OperationDef. */
    private static final Map<OperationMode, Map<String, OperationDef>> REPOSITORY = new ConcurrentHashMap<>();

    private OperationRegistry() {
    }

    /**
     * Register an operation.
     *
     * @param operationCls the class implementing the operation logic
     * @param name         unique identifier for the operation (e.g., "fs", "shell", "code")
     * @param mode         running mode (LOCAL or SANDBOX)
     * @param description  human-readable description
     */
    public static void register(Class<? extends BaseOperation> operationCls,
                                String name, OperationMode mode, String description) {
        if (name == null || name.isBlank() || mode == null) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_REGISTRY_ERROR,
                    "process", "register",
                    "error_msg", "Operation name and mode must be provided for " + operationCls.getName());
        }

        // Ensure built-in operations for this mode are loaded
        loadBuiltInOperations(mode);

        OperationDef newDef = new OperationDef(operationCls, name, mode, description != null ? description : "");
        Map<String, OperationDef> modeMap = REPOSITORY.get(mode);

        // Idempotency check
        OperationDef existing = modeMap.get(name);
        if (newDef.equals(existing)) {
            return;
        }

        modeMap.put(name, newDef);
    }

    /**
     * Register an operation class that has the {@link Operation} annotation.
     *
     * @param operationCls the annotated operation class
     */
    public static void register(Class<? extends BaseOperation> operationCls) {
        Operation annotation = operationCls.getAnnotation(Operation.class);
        if (annotation == null) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_REGISTRY_ERROR,
                    "process", "register",
                    "error_msg", "Class " + operationCls.getName() + " does not have @Operation annotation");
        }
        register(operationCls, annotation.name(), annotation.mode(), annotation.description());
    }

    /**
     * Get operation information for the given name and mode.
     *
     * @param name operation name
     * @param mode operation mode
     * @return the operation definition, or empty if not found
     */
    public static Optional<OperationDef> getOperationInfo(String name, OperationMode mode) {
        loadBuiltInOperations(mode);
        return Optional.ofNullable(REPOSITORY.getOrDefault(mode, Map.of()).get(name));
    }

    /**
     * Get list of supported operation names for the given mode.
     *
     * @param mode operation mode
     * @return sorted list of operation names
     */
    public static List<String> getSupportedOperations(OperationMode mode) {
        loadBuiltInOperations(mode);
        List<String> names = new ArrayList<>(REPOSITORY.getOrDefault(mode, Map.of()).keySet());
        names.sort(String::compareTo);
        return names;
    }

    /**
     * Ensure built-in operations for the given mode are discovered and registered.
     * <p>
     * First registers explicitly known built-in classes, then performs dynamic package
     * scanning to discover any additional {@link Operation}-annotated classes, mirroring
     * Python's {@code _discover_package()} behavior.
     */
    @SuppressWarnings("unchecked")
    private static synchronized void loadBuiltInOperations(OperationMode mode) {
        if (REPOSITORY.containsKey(mode)) {
            return;
        }

        REPOSITORY.put(mode, new ConcurrentHashMap<>());

        // 1. Explicitly register known built-in operation classes
        List<String> classNames = getBuiltInClassNames(mode);
        for (String className : classNames) {
            registerClassByName(className, mode);
        }

        // 2. Dynamic package scanning: discover additional @Operation-annotated classes
        // Mirrors Python's _discover_package("openjiuwen.core.sys_operation.{mode.value}")
        String packageName = "com.openjiuwen.core.sysop." + mode.getValue();
        discoverPackage(packageName, mode);
    }

    /**
     * Discover and register operations in a package via {@link Operation} annotations.
     * <p>
     * Mirrors Python's {@code _discover_package(package_name)} — scans the given package
     * for classes annotated with {@code @Operation} and registers them automatically.
     *
     * @param packageName the fully-qualified package name to scan
     * @param mode        the operation mode to filter by
     */
    @SuppressWarnings("unchecked")
    private static void discoverPackage(String packageName, OperationMode mode) {
        String packagePath = packageName.replace('.', '/');
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = OperationRegistry.class.getClassLoader();
            }
            Enumeration<URL> resources = classLoader.getResources(packagePath);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if ("file".equals(resource.getProtocol())) {
                    scanDirectoryForClasses(new java.io.File(resource.toURI()), packageName, mode);
                } else if ("jar".equals(resource.getProtocol())) {
                    scanJarForClasses(resource, packagePath, packageName, mode);
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to discover package " + packageName + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void scanDirectoryForClasses(java.io.File directory, String packageName, OperationMode mode) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        java.io.File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (java.io.File file : files) {
            if (file.isDirectory()) {
                scanDirectoryForClasses(file, packageName + "." + file.getName(), mode);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                registerClassByName(className, mode);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void scanJarForClasses(URL resource, String packagePath,
                                          String packageName, OperationMode mode) {
        try {
            String jarPath = resource.getPath();
            if (jarPath.contains("!")) {
                jarPath = jarPath.substring(0, jarPath.indexOf("!"));
            }
            if (jarPath.startsWith("file:")) {
                jarPath = jarPath.substring(5);
            }
            try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath)) {
                Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entryName.startsWith(packagePath) && entryName.endsWith(".class")) {
                        String className = entryName.replace('/', '.').replace(".class", "");
                        registerClassByName(className, mode);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to scan JAR for operations: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerClassByName(String className, OperationMode mode) {
        try {
            Class<?> cls = Class.forName(className);
            if (BaseOperation.class.isAssignableFrom(cls)) {
                Operation annotation = cls.getAnnotation(Operation.class);
                if (annotation != null && annotation.mode() == mode) {
                    REPOSITORY.get(mode).putIfAbsent(
                            annotation.name(),
                            new OperationDef(
                                    (Class<? extends BaseOperation>) cls,
                                    annotation.name(),
                                    annotation.mode(),
                                    annotation.description()
                            ));
                }
            }
        } catch (ClassNotFoundException e) {
            logger.warning("Operation class not found: " + className);
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_REGISTRY_ERROR,
                    "process", "register",
                    "error_msg", "Failed to load operation: " + className + " - " + e.getMessage());
        }
    }

    /**
     * Get the class names of built-in operations for a mode.
     */
    private static List<String> getBuiltInClassNames(OperationMode mode) {
        String basePackage = "com.openjiuwen.core.sysop.";
        return switch (mode) {
            case LOCAL -> List.of(
                    basePackage + "local.LocalShellOperation",
                    basePackage + "local.LocalCodeOperation",
                    basePackage + "local.LocalFsOperation"
            );
            case SANDBOX -> List.of(
                    basePackage + "sandbox.SandboxShellOperation",
                    basePackage + "sandbox.SandboxCodeOperation",
                    basePackage + "sandbox.SandboxFsOperation"
            );
        };
    }

    /**
     * Clear all registered operations. (For testing purposes.)
     */
    static void clear() {
        REPOSITORY.clear();
    }
}
