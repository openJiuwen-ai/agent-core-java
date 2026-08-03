/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public facade for the chained session-controller package.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.session.session_controller} in
 * {@code openjiuwen/core/session/session_controller/__init__.py}.</p>
 */
public final class SessionControllerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/session/session_controller/__init__.py";
    public static final List<String> ALL = List.of(
            "Scope",
            "Subject",
            "SessionScope",
            "SessionScopeKey",
            "SessionScopeFactory",
            "DataContainer",
            "Permission",
            "SharingPolicy",
            "DataContainerFactory",
            "ChainSession",
            "SessionController",
            "GlobalSessionController"
    );
    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private SessionControllerPackage() {
    }

    public static List<String> all() {
        return ALL;
    }

    public static boolean exports(String symbolName) {
        return ALL.contains(symbolName);
    }

    public static Class<?> typeFor(String exportedName) {
        return EXPORTED_TYPES.get(exportedName);
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("Scope", Scope.class);
        exports.put("Subject", Subject.class);
        exports.put("SessionScope", SessionScope.class);
        exports.put("SessionScopeKey", SessionScopeKey.class);
        exports.put("SessionScopeFactory", SessionScopeFactory.class);
        exports.put("DataContainer", DataContainer.class);
        exports.put("Permission", Permission.class);
        exports.put("SharingPolicy", SharingPolicy.class);
        exports.put("DataContainerFactory", DataContainerFactory.class);
        exports.put("ChainSession", ChainSession.class);
        exports.put("SessionController", SessionController.class);
        exports.put("GlobalSessionController", GlobalSessionController.class);
        return Collections.unmodifiableMap(exports);
    }
}
