/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code ExternalDirectoryChecker} in
 * {@code openjiuwen/harness/security/checker.py}.
 */
public final class ExternalDirectoryChecker {

    static final Pattern SHELL_OPERATORS_PATTERN = Pattern.compile("[;&|`<>]|\\$[({]|\\r?\\n");
    static final Set<String> COMMAND_EXEC_TOOLS = Set.of("mcp_exec_command");

    private static final Set<String> PATH_AWARE_COMMANDS = Set.of(
            "cd", "rm", "cp", "mv", "mkdir", "touch", "chmod", "chown", "cat",
            "ls", "dir", "type", "del", "rd", "copy", "move", "md",
            "head", "tail", "more", "less", "vim", "nano", "gedit", "notepad"
    );

    private final Map<String, Object> config;
    private final Path workspaceRoot;

    public ExternalDirectoryChecker(Map<String, Object> config, Path workspaceRoot) {
        this.config = config == null ? Map.of() : config;
        this.workspaceRoot = workspaceRoot;
    }

    public PermissionResult checkExternalPaths(String toolName, Map<String, Object> toolArgs) {
        if (workspaceRoot == null) {
            return null;
        }

        List<Path> paths = new ArrayList<>();
        if (Set.of("mcp_exec_command", "bash", "create_terminal").contains(toolName)) {
            String workdir = toolArgs.get("workdir") == null ? "" : String.valueOf(toolArgs.get("workdir"));
            Path workdirResolved;
            try {
                workdirResolved = workspaceRoot.resolve(workdir).toAbsolutePath().normalize();
            } catch (Exception ignored) {
                workdirResolved = workspaceRoot;
            }
            String command = toolArgs.get("command") == null
                    ? String.valueOf(toolArgs.getOrDefault("cmd", ""))
                    : String.valueOf(toolArgs.get("command"));
            paths = extractPathsFromCommand(command, workdirResolved);
        } else if (TieredPolicy.PATH_TOOLS.contains(toolName)) {
            for (String value : TieredPolicy.iterPathStrings(toolName, toolArgs)) {
                try {
                    Path path = Path.of(value);
                    paths.add(path.isAbsolute() ? path.normalize() : workspaceRoot.resolve(path).normalize());
                } catch (Exception ignored) {
                    // Skip unparseable path tokens just like the Python helper.
                }
            }
        } else {
            return null;
        }

        List<String> external = new ArrayList<>();
        for (Path path : paths) {
            if (!PermissionPatterns.containsPath(workspaceRoot, path)) {
                external.add(path.toString().replace("\\", "/"));
            }
        }
        if (external.isEmpty()) {
            return null;
        }

        Object externalConfig = config.get("external_directory");
        String action = "ask";
        if (externalConfig instanceof String stringValue) {
            action = stringValue;
        } else if (externalConfig instanceof Map<?, ?> map) {
            Object wildcard = map.get("*");
            if (wildcard != null) {
                action = String.valueOf(wildcard);
            }
            if (allPathsAllowedBySpecificRules(map, external)) {
                action = "allow";
            }
        }

        PermissionLevel level = PermissionLevel.fromValue(action);
        if (level == PermissionLevel.ALLOW) {
            return null;
        }
        String reasonPrefix = level == PermissionLevel.DENY
                ? "Access to paths outside workspace is denied: "
                : "Access to paths outside workspace requires approval: ";
        return new PermissionResult(level, "external_directory.*", reasonPrefix + external.get(0), external);
    }

    static List<Path> extractPathsFromCommand(String command, Path workdir) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
        String[] tokens = command.strip().split("\\s+");
        if (tokens.length == 0) {
            return List.of();
        }
        String cmd = tokens[0].toLowerCase();
        if (!PATH_AWARE_COMMANDS.contains(cmd)) {
            return List.of();
        }
        List<Path> paths = new ArrayList<>();
        for (int index = 1; index < tokens.length; index++) {
            String token = tokens[index].strip().replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
            if (token.isEmpty() || token.startsWith("-") || !looksLikePath(token)) {
                continue;
            }
            try {
                Path path = Path.of(token);
                if (!path.isAbsolute()) {
                    path = workdir.resolve(path);
                }
                paths.add(path.toAbsolutePath().normalize());
            } catch (Exception ignored) {
                // Skip path parsing failures.
            }
        }
        return paths;
    }

    static boolean looksLikePath(String token) {
        if (token.startsWith("\\\\") || token.startsWith("./") || token.startsWith("../")) {
            return true;
        }
        if (Pattern.matches("^[A-Za-z]:[/\\\\].*", token)) {
            return true;
        }
        return token.contains("\\") || token.contains("/");
    }

    private static boolean allPathsAllowedBySpecificRules(Map<?, ?> config, List<String> externalPaths) {
        for (String path : externalPaths) {
            boolean covered = false;
            for (Map.Entry<?, ?> entry : config.entrySet()) {
                if ("*".equals(entry.getKey()) || !"allow".equalsIgnoreCase(String.valueOf(entry.getValue()))) {
                    continue;
                }
                String configuredPath = String.valueOf(entry.getKey()).replace("\\", "/").replaceAll("/+$", "");
                if (!configuredPath.contains("/")) {
                    continue;
                }
                if (PermissionPatterns.containsPath(configuredPath, path)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                return false;
            }
        }
        return true;
    }
}
