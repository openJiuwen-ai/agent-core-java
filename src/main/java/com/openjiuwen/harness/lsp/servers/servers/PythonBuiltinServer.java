/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers.servers;

import com.openjiuwen.harness.lsp.core.SpawnHandle;
import com.openjiuwen.harness.lsp.servers.BuiltinServerRegistry;
import com.openjiuwen.harness.lsp.servers.ServerDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mirrors Python's builtin server definition in
 * {@code openjiuwen/harness/lsp/servers/servers/python.py}.
 */
public final class PythonBuiltinServer {

    private PythonBuiltinServer() {
    }

    public static SpawnHandle spawnPython(String root) {
        ResolvedCommand resolved = resolvePyrightCommand();
        if (resolved == null) {
            return null;
        }

        SpawnHandle handle = new SpawnHandle();
        handle.setCommand(resolved.command());
        handle.setArgs(resolved.args());
        handle.setInitializationOptions(buildInitializationOptions(root));
        return handle;
    }

    static Map<String, Object> buildInitializationOptions(String root) {
        Map<String, Object> initializationOptions = new LinkedHashMap<>();
        String virtualEnv = System.getenv("VIRTUAL_ENV");

        List<String> potentialVenvPaths = new ArrayList<>();
        if (virtualEnv != null && !virtualEnv.isBlank()) {
            potentialVenvPaths.add(virtualEnv);
        }
        if (root != null && !root.isBlank()) {
            potentialVenvPaths.add(Path.of(root, ".venv").toString());
            potentialVenvPaths.add(Path.of(root, "venv").toString());
        }

        for (String venvPath : potentialVenvPaths) {
            String pythonPath = pythonPathForVenv(venvPath);
            String candidate = candidatePythonPathForVenv(venvPath);
            if (Files.exists(Path.of(candidate))) {
                initializationOptions.put("pythonPath", pythonPath);
                break;
            }
        }

        return initializationOptions.isEmpty() ? null : initializationOptions;
    }

    static String pythonPathForVenv(String venvPath) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path path = Path.of(venvPath, isWindows ? "Scripts" : "bin", "python");
        return path.toString().replace('\\', '/');
    }

    static String candidatePythonPathForVenv(String venvPath) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String pythonPath = pythonPathForVenv(venvPath);
        if (!isWindows) {
            return pythonPath;
        }
        String candidate = pythonPath.replace("/bin/", "/Scripts/");
        Path candidatePath = Path.of(candidate);
        if (Files.exists(candidatePath)) {
            return candidate;
        }
        return candidate + ".exe";
    }

    static ResolvedCommand resolvePyrightCommand() {
        ResolvedCommand npmResolved = resolveViaNpm();
        if (npmResolved != null) {
            return npmResolved;
        }

        Optional<String> raw = BuiltinServerRegistry.resolveCommand("pyright-langserver");
        if (raw.isEmpty()) {
            return null;
        }

        String rawCommand = raw.get();
        if (!rawCommand.toUpperCase().endsWith(".CMD")) {
            return new ResolvedCommand(rawCommand, List.of("--stdio"));
        }

        try {
            String content = Files.readString(Path.of(rawCommand), StandardCharsets.UTF_8);
            for (String line : content.split("\\R")) {
                if (!line.contains("node_modules") || !line.contains(".js")) {
                    continue;
                }
                String jsPath = extractQuotedPath(line.strip());
                if (jsPath == null) {
                    continue;
                }

                String cmdDir = Path.of(rawCommand).getParent().toString();
                if (jsPath.startsWith("%dp0%\\") || jsPath.startsWith("%dp0%/")
                        || jsPath.startsWith("%~dp0\\") || jsPath.startsWith("%~dp0/")) {
                    jsPath = jsPath
                            .replace("%dp0%", cmdDir)
                            .replace("%~dp0", cmdDir);
                } else if (!Path.of(jsPath).isAbsolute()) {
                    jsPath = Path.of(cmdDir, jsPath).toString();
                }

                Optional<String> nodePath = BuiltinServerRegistry.resolveCommand("node");
                if (nodePath.isEmpty()) {
                    return null;
                }
                return new ResolvedCommand(nodePath.get(), List.of(jsPath, "--stdio"));
            }
        } catch (IOException ignored) {
            return null;
        }

        return null;
    }

    private static ResolvedCommand resolveViaNpm() {
        Optional<String> npmPath = BuiltinServerRegistry.resolveCommand("npm");
        if (npmPath.isEmpty()) {
            return null;
        }
        Optional<String> nodePath = BuiltinServerRegistry.resolveCommand("node");
        if (nodePath.isEmpty()) {
            return null;
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                npmPath.get(),
                "list",
                "-g",
                "--depth=0",
                "pyright");
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return null;
            }

            List<String> lines = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            String prefix = null;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("pyright@")) {
                    if (i > 0) {
                        prefix = lines.get(i - 1).trim();
                    }
                    break;
                }
            }

            if (prefix == null || prefix.isBlank()) {
                return null;
            }

            Path jsPath = Path.of(prefix, "node_modules", "pyright", "langserver.index.js");
            if (!Files.exists(jsPath)) {
                return null;
            }
            return new ResolvedCommand(nodePath.get(), List.of(jsPath.toString(), "--stdio"));
        } catch (IOException | InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    static String extractQuotedPath(String line) {
        int firstQuote = line.indexOf('"');
        int secondQuote = line.indexOf('"', firstQuote + 1);
        if (firstQuote < 0 || secondQuote <= firstQuote) {
            return null;
        }
        return line.substring(firstQuote + 1, secondQuote);
    }

    public static ServerDefinition server() {
        return new ServerDefinition(
                "pyright",
                List.of(".py", ".pyi"),
                "python",
                10,
                false,
                BuiltinServerRegistry.nearestRoot(
                        List.of("pyproject.toml", "setup.py", "setup.cfg", "requirements.txt", "Pipfile", "pyrightconfig.json"),
                        List.of(".git"),
                        null
                ),
                PythonBuiltinServer::spawnPython
        );
    }

    record ResolvedCommand(String command, List<String> args) {
    }
}
