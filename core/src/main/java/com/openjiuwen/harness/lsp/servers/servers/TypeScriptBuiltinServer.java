/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers.servers;

import com.openjiuwen.harness.lsp.core.SpawnHandle;
import com.openjiuwen.harness.lsp.servers.BuiltinServerRegistry;
import com.openjiuwen.harness.lsp.servers.ServerDefinition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's builtin server definition in
 * {@code openjiuwen/harness/lsp/servers/servers/typescript.py}.
 */
public final class TypeScriptBuiltinServer {

    private TypeScriptBuiltinServer() {
    }

    public static SpawnHandle spawnTypeScript(String root) {
        if (!BuiltinServerRegistry.isCommandAvailable("typescript-language-server")) {
            return null;
        }

        SpawnHandle handle = new SpawnHandle();
        handle.setCommand("typescript-language-server");
        handle.setArgs(buildArgs(root));
        handle.setInitializationOptions(null);
        return handle;
    }

    static List<String> buildArgs(String root) {
        List<String> args = new ArrayList<>();
        args.add("--stdio");
        if (!hasProjectConfig(root)) {
            args.add("--ignore-node-modules");
        }
        return args;
    }

    static boolean hasProjectConfig(String root) {
        if (root == null || root.isBlank()) {
            return false;
        }
        Path base = Path.of(root);
        return Files.exists(base.resolve("tsconfig.json")) || Files.exists(base.resolve("jsconfig.json"));
    }

    public static ServerDefinition server() {
        return new ServerDefinition(
                "typescript",
                List.of(".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs"),
                "typescript",
                10,
                false,
                BuiltinServerRegistry.nearestRoot(
                        List.of("package.json", "package-lock.json", "pnpm-lock.yaml", "yarn.lock"),
                        List.of("deno.json", "deno.jsonc"),
                        null
                ),
                TypeScriptBuiltinServer::spawnTypeScript
        );
    }
}
