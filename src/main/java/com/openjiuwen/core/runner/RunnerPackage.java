/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Package facade for the core runner module.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.runner} package facade in
 * {@code openjiuwen/core/runner/__init__.py}.</p>
 */
public final class RunnerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/runner/__init__.py";
    public static final String RUNNER_PYTHON_SOURCE = "openjiuwen.core.runner.runner.Runner";
    public static final String RUNNER_JAVA_TYPE_NAME = "com.openjiuwen.core.runner.Runner";
    public static final List<String> EXPORTED_SYMBOLS = List.of("Runner");
    public static final Map<String, String> EXPORT_SOURCES = Map.of("Runner", RUNNER_PYTHON_SOURCE);
    public static final Map<String, String> JAVA_SYMBOL_NAMES = Map.of("Runner", RUNNER_JAVA_TYPE_NAME);

    private RunnerPackage() {
    }

    /**
     * Mirrors the Python package's exported attribute set.
     *
     * @return exported symbol names
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Checks whether the Python package facade supports this attribute name.
     *
     * @param name attribute name
     * @return true when the attribute is exported by Python {@code __getattr__}
     */
    public static boolean exports(String name) {
        return EXPORTED_SYMBOLS.contains(name);
    }

    /**
     * Returns the Python source object imported lazily by the facade.
     *
     * @param name attribute name
     * @return dotted Python source object, or null when absent
     */
    public static String sourceFor(String name) {
        return EXPORT_SOURCES.get(name);
    }

    /**
     * Returns the Java type name corresponding to a Python facade attribute.
     *
     * @param name attribute name
     * @return Java type name, or null when absent
     */
    public static String javaSymbolNameFor(String name) {
        return JAVA_SYMBOL_NAMES.get(name);
    }

    /**
     * Resolves the Java type lazily, matching Python's deferred import behavior.
     *
     * @param name attribute name
     * @return resolved Java class, or empty when the attribute is unknown or not translated yet
     */
    public static Optional<Class<?>> resolveType(String name) {
        String javaType = JAVA_SYMBOL_NAMES.get(name);
        if (javaType == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Class.forName(javaType));
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Resolves a facade attribute or raises a Python-like missing attribute error.
     *
     * @param name attribute name
     * @return resolved Java class
     */
    public static Class<?> getAttr(String name) {
        if (!exports(name)) {
            throw missingAttribute(name);
        }
        return resolveType(name).orElseThrow(() -> new NoSuchElementException(
                "module 'openjiuwen.core.runner' has no translated attribute '" + name + "'"));
    }

    private static NoSuchElementException missingAttribute(String name) {
        return new NoSuchElementException(
                "module 'openjiuwen.core.runner' has no attribute '" + name + "'");
    }
}
