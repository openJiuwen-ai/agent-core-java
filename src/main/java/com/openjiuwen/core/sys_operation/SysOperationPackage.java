/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation;

import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for root sys-operation exports.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/sys_operation/__init__.py}.</p>
 */
public final class SysOperationPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/sys_operation/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "OperationMode",
            "LocalWorkConfig",
            "SandboxGatewayConfig",
            "SysOperationCard",
            "SysOperation",
            "generate_isolation_key_template",
            "kill_shell_processes_for_session"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private SysOperationPackage() {
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("OperationMode", OperationMode.class);
        exports.put("LocalWorkConfig", LocalWorkConfig.class);
        exports.put("SandboxGatewayConfig", SandboxGatewayConfig.class);
        exports.put("SysOperationCard", SysOperationCard.class);
        exports.put("SysOperation", SysOperation.class);
        return Map.copyOf(exports);
    }

    public static String generateIsolationKeyTemplate(String isolationPrefix,
                                                      com.openjiuwen.core.sys_operation.config.ContainerScope scope,
                                                      String customId,
                                                      String launcherType,
                                                      String sandboxType) {
        return SysOperation.generateIsolationKeyTemplate(
                isolationPrefix,
                scope,
                customId,
                launcherType,
                sandboxType
        );
    }

    public static int killShellProcessesForSession(String sessionId) {
        return ShellProcessRegistry.killShellProcessesForSession(sessionId);
    }
}
