/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers.servers;

import com.openjiuwen.harness.lsp.core.SpawnHandle;
import com.openjiuwen.harness.lsp.servers.BuiltinServerRegistry;
import com.openjiuwen.harness.lsp.servers.ServerDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Mirrors Python's builtin server definition in
 * {@code openjiuwen/harness/lsp/servers/servers/rust.py}.
 */
public final class RustBuiltinServer {

    private RustBuiltinServer() {
    }

    public static String rustRoot(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }

        Path start;
        try {
            Path file = Path.of(filePath);
            Path parent = file.toAbsolutePath().normalize().getParent();
            if (parent == null) {
                return null;
            }
            start = parent;
        } catch (RuntimeException ex) {
            return null;
        }

        Path current = start;
        String root = null;
        while (current != null) {
            if (Files.exists(current.resolve("Cargo.toml"))) {
                root = current.toString();
                break;
            }
            current = current.getParent();
        }
        if (root == null) {
            return null;
        }

        Path cargoToml = Path.of(root).resolve("Cargo.toml");
        String content = readFile(cargoToml);
        if (content != null && content.contains("[workspace]")) {
            return root;
        }

        current = Path.of(root);
        while (current.getParent() != null) {
            Path parentCargo = current.getParent().resolve("Cargo.toml");
            if (Files.exists(parentCargo)) {
                String parentContent = readFile(parentCargo);
                if (parentContent != null && parentContent.contains("[workspace]")) {
                    return current.getParent().toString();
                }
            }
            current = current.getParent();
        }
        return root;
    }

    public static SpawnHandle spawnRust(String root) {
        if (!BuiltinServerRegistry.isCommandAvailable("rust-analyzer")) {
            return null;
        }

        SpawnHandle handle = new SpawnHandle();
        handle.setCommand("rust-analyzer");
        handle.setArgs(List.of());
        return handle;
    }

    public static ServerDefinition server() {
        return new ServerDefinition(
                "rust",
                List.of(".rs"),
                "rust",
                10,
                false,
                RustBuiltinServer::rustRoot,
                RustBuiltinServer::spawnRust
        );
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            return null;
        }
    }
}
