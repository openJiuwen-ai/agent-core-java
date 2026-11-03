/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers.servers;

import com.openjiuwen.harness.lsp.core.SpawnHandle;
import com.openjiuwen.harness.lsp.servers.BuiltinServerRegistry;
import com.openjiuwen.harness.lsp.servers.ServerDefinition;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's builtin server definition in
 * {@code openjiuwen/harness/lsp/servers/servers/go.py}.
 */
public final class GoBuiltinServer {

    private GoBuiltinServer() {
    }

    public static String goRoot(String filePath) {
        String workspaceRoot = BuiltinServerRegistry.nearestRoot(List.of("go.work")).apply(filePath);
        if (workspaceRoot != null) {
            return workspaceRoot;
        }
        return BuiltinServerRegistry.nearestRoot(List.of("go.mod", "go.sum")).apply(filePath);
    }

    public static SpawnHandle spawnGo(String root) {
        if (!BuiltinServerRegistry.isCommandAvailable("gopls")) {
            return null;
        }

        SpawnHandle handle = new SpawnHandle();
        handle.setCommand("gopls");
        handle.setArgs(List.of());
        handle.setInitializationOptions(Map.of("staticcheck", true));
        handle.setStartupTimeout(60_000);
        return handle;
    }

    public static ServerDefinition server() {
        return new ServerDefinition(
                "gopls",
                List.of(".go"),
                "go",
                10,
                false,
                GoBuiltinServer::goRoot,
                GoBuiltinServer::spawnGo
        );
    }
}
