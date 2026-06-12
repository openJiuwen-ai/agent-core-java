/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers.servers;

import com.openjiuwen.harness.lsp.core.SpawnHandle;
import com.openjiuwen.harness.lsp.servers.BuiltinServerRegistry;
import com.openjiuwen.harness.lsp.servers.ServerDefinition;

import java.util.List;

/**
 * Mirrors Python's builtin server definition in
 * {@code openjiuwen/harness/lsp/servers/servers/java.py}.
 */
public final class JavaBuiltinServer {

    private JavaBuiltinServer() {
    }

    public static SpawnHandle spawnJava(String root) {
        String command = BuiltinServerRegistry.resolveCommand("jdtls").orElse(null);
        if (command == null) {
            return null;
        }

        SpawnHandle handle = new SpawnHandle();
        handle.setCommand(command);
        handle.setArgs(List.of());
        handle.setInitializationOptions(null);
        return handle;
    }

    public static ServerDefinition server() {
        return new ServerDefinition(
                "jdtls",
                List.of(".java"),
                "java",
                10,
                false,
                BuiltinServerRegistry.nearestRoot(
                        List.of("pom.xml", "build.gradle", "build.gradle.kts", ".project"),
                        List.of(".git"),
                        null
                ),
                JavaBuiltinServer::spawnJava
        );
    }
}
