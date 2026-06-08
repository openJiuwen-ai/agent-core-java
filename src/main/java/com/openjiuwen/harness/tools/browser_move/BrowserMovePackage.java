/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Mirrors Python's {@code openjiuwen/harness/tools/browser_move/__init__.py}.
 */
public final class BrowserMovePackage {
    private static final String BROWSER_TOOLS_CLASS =
            "com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserTools";

    public static final String PYTHON_MODULE = "openjiuwen/harness/tools/browser_move/__init__.py";
    public static final Path REPO_ROOT = resolveRepoRoot();
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "REPO_ROOT",
            "build_browser_runtime_mcp_config",
            "register_browser_runtime_mcp_server",
            "restart_local_browser_runtime_server",
            "stop_local_browser_runtime_server"
    );

    private BrowserMovePackage() {
    }

    public static Path resolveRepoRoot() {
        try {
            Path location = Path.of(BrowserMovePackage.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath()
                    .normalize();
            if (Files.isRegularFile(location)) {
                location = location.getParent();
            }
            if (location != null && location.getFileName() != null) {
                String fileName = location.getFileName().toString();
                if ("classes".equals(fileName) || "test-classes".equals(fileName)) {
                    Path targetDir = location.getParent();
                    if (targetDir != null && targetDir.getParent() != null) {
                        return targetDir.getParent().normalize();
                    }
                }
                if ("target".equals(fileName) && location.getParent() != null) {
                    return location.getParent().normalize();
                }
            }
            return location;
        } catch (URISyntaxException | NullPointerException exception) {
            return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        }
    }

    public static Object buildBrowserRuntimeMcpConfig() {
        return invokeBrowserTools("buildBrowserRuntimeMcpConfig");
    }

    public static Object registerBrowserRuntimeMcpServer(Object agent) {
        return invokeBrowserTools("registerBrowserRuntimeMcpServer", agent);
    }

    public static Object restartLocalBrowserRuntimeServer() {
        return invokeBrowserTools("restartLocalBrowserRuntimeServer");
    }

    public static void stopLocalBrowserRuntimeServer() {
        invokeBrowserTools("stopLocalBrowserRuntimeServer");
    }

    private static Object invokeBrowserTools(String methodName, Object... args) {
        Class<?> browserToolsType = loadBrowserToolsType();
        Method method = resolveMethod(browserToolsType, methodName, args);
        try {
            return method.invoke(null, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke browser runtime bridge method: " + methodName, exception);
        }
    }

    private static Class<?> loadBrowserToolsType() {
        try {
            return Class.forName(BROWSER_TOOLS_CLASS);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "Browser runtime browser_tools translation is not available yet.",
                    exception
            );
        }
    }

    private static Method resolveMethod(Class<?> type, String methodName, Object[] args) {
        for (Method method : type.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !method.getName().equals(methodName)) {
                continue;
            }
            if (parametersMatch(method.getParameterTypes(), args)) {
                return method;
            }
        }
        throw new IllegalStateException("Browser runtime bridge method not found: " + methodName);
    }

    private static boolean parametersMatch(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int index = 0; index < parameterTypes.length; index++) {
            Object value = args[index];
            Class<?> parameterType = wrapPrimitive(parameterTypes[index]);
            if (value == null) {
                if (parameterType.isPrimitive()) {
                    return false;
                }
                continue;
            }
            if (!parameterType.isInstance(value)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        return Void.class;
    }
}
