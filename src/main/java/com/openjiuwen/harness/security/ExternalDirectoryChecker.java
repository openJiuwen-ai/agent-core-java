/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * External directory checker for permission system.
 *
 * <p>Checks if commands access paths outside workspace, triggering external_directory
 * permission check if boundaries are crossed.
 *
 * <p>Mirrors Python's {@code ExternalDirectoryChecker} in
 * {@code openjiuwen.harness.security.checker}.
 */
public class ExternalDirectoryChecker {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalDirectoryChecker.class);

    /** Path-aware commands (need external directory check). */
    private static final Set<String> PATH_AWARE_COMMANDS = Set.of(
            "cd", "rm", "cp", "mv", "mkdir", "touch", "chmod", "chown", "cat",
            "ls", "dir", "type", "del", "rd", "copy", "move", "md",
            "head", "tail", "more", "less", "vim", "nano", "gedit", "notepad"
    );

    /** Tools that operate on paths. */
    private static final Set<String> PATH_TOOLS = Set.of(
            "read_file", "write_file", "edit_file",
            "read_text_file", "write_text_file",
            "write", "read",
            "glob_file_search", "glob", "list_dir", "list_files",
            "grep", "search_replace"
    );

    /** Path argument keys. */
    private static final Set<String> PATH_ARG_KEYS = Set.of(
            "path", "file_path", "target_file", "file", "old_path", "new_path",
            "source_path", "dest_path", "directory", "dir"
    );

    private final Map<String, Object> config;
    private final Path workspaceRoot;

    public ExternalDirectoryChecker(Map<String, Object> config, Path workspaceRoot) {
        this.config = config != null ? config : Map.of();
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * Check if tool accesses external paths.
     *
     * <p>Returns PermissionResult with DENY/ASK if path is outside workspace,
     * or null if no external path violation.
     */
    public PermissionResult checkExternalPaths(String toolName, Map<String, Object> toolArgs) {
        if (workspaceRoot == null) {
            LOG.debug("[PermissionEngine] permission.external.workspace missing; skip external_directory check");
            return null;
        }
        LOG.debug("[PermissionEngine] permission.external.workspace source=config workspace={}", workspaceRoot);

        List<Path> paths = new ArrayList<>();

        // Shell commands
        if (Set.of("mcp_exec_command", "bash", "create_terminal").contains(toolName)) {
            String workdir = toolArgs.get("workdir") != null ? toolArgs.get("workdir").toString() : "";
            Path workdirResolved = workspaceRoot;
            try {
                workdirResolved = workspaceRoot.resolve(workdir).toAbsolutePath().normalize();
            } catch (Exception e) {
                workdirResolved = workspaceRoot;
            }
            String cmd = extractCommand(toolArgs);
            LOG.debug("[PermissionEngine] permission.external.shell_input tool={} cmd={} workdir={}",
                    toolName, cmd, workdirResolved);
            paths = extractPathsFromCommand(cmd, workdirResolved);
        }
        // Path tools
        else if (PATH_TOOLS.contains(toolName)) {
            for (String key : PATH_ARG_KEYS) {
                Object value = toolArgs.get(key);
                if (value == null || value.toString().isEmpty()) continue;
                String raw = value.toString().strip().replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
                if (raw.isEmpty()) continue;
                try {
                    Path p = Path.of(raw);
                    if (!p.isAbsolute()) {
                        p = workspaceRoot.resolve(p).toAbsolutePath().normalize();
                    }
                    paths.add(p);
                } catch (Exception e) {
                    LOG.debug("[PermissionEngine] permission.external.path_parse_failed raw={}", raw);
                }
            }
        }

        if (paths.isEmpty()) {
            return null;
        }

        // Get external_directory config
        Object extDirObj = config.get("external_directory");
        Map<String, Object> extDirCfg = extDirObj instanceof Map
                ? (Map<String, Object>) extDirObj
                : Map.of("*", "ask");

        List<String> externalPaths = new ArrayList<>();
        PermissionLevel strictestLevel = PermissionLevel.ALLOW;

        for (Path p : paths) {
            try {
                Path normalized = p.toAbsolutePath().normalize();
                boolean isExternal = !PermissionPatterns.containsPath(workspaceRoot, normalized);

                if (isExternal) {
                    String pathStr = normalized.toString().replace("\\", "/");
                    externalPaths.add(pathStr);

                    // Check external_directory config for this path
                    PermissionLevel pathLevel = resolveExternalDirectoryPermission(pathStr, extDirCfg);
                    if (pathLevel.ordinal() < strictestLevel.ordinal()) {
                        strictestLevel = pathLevel;
                    }
                }
            } catch (Exception e) {
                LOG.debug("[PermissionEngine] permission.external.path_check_failed path={}", p);
            }
        }

        if (externalPaths.isEmpty()) {
            return null;
        }

        if (strictestLevel == PermissionLevel.ALLOW) {
            return null;
        }

        return new PermissionResult(
                strictestLevel,
                "external_directory",
                "Path outside workspace: " + String.join(", ", externalPaths),
                externalPaths
        );
    }

    /**
     * Extract paths from shell command.
     */
    private List<Path> extractPathsFromCommand(String command, Path workdir) {
        if (command == null || command.isEmpty()) {
            return List.of();
        }

        String[] tokens = command.strip().split("\\s+");
        if (tokens.length == 0) {
            return List.of();
        }

        String cmd = tokens[0].toLowerCase();
        LOG.debug("[PermissionEngine] permission.external.parse tool_command={} cmd={} path_aware={}",
                command, cmd, PATH_AWARE_COMMANDS.contains(cmd));

        if (!PATH_AWARE_COMMANDS.contains(cmd)) {
            return List.of();
        }

        Path base = workdir.toAbsolutePath().normalize();
        LOG.debug("[PermissionEngine] permission.external.parse_base base={}", base);

        List<Path> paths = new ArrayList<>();
        for (int i = 1; i < tokens.length; i++) {
            String tok = tokens[i].strip().replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
            if (tok.isEmpty() || tok.startsWith("-")) continue;
            if (!looksLikePath(tok)) continue;

            try {
                Path p = Path.of(tok);
                if (!p.isAbsolute()) {
                    p = base.resolve(p);
                }
                paths.add(p.toAbsolutePath().normalize());
            } catch (Exception e) {
                LOG.debug("[PermissionEngine] permission.external.path_parse_failed token={}", tok);
            }
        }

        LOG.debug("[PermissionEngine] permission.external.parse_paths extracted_paths={}", paths);
        return paths;
    }

    /**
     * Check if token looks like a path.
     */
    private boolean looksLikePath(String token) {
        if (token.startsWith("\\\\") || token.startsWith("./") || token.startsWith("../")) {
            return true;
        }
        if (Pattern.matches("^[A-Za-z]:[/\\\\].*", token)) {
            return true;
        }
        return token.contains("\\") || token.contains("/");
    }

    /**
     * Extract command from tool args.
     */
    private String extractCommand(Map<String, Object> toolArgs) {
        Object cmd = toolArgs.get("command");
        if (cmd == null) cmd = toolArgs.get("cmd");
        return cmd != null ? cmd.toString() : "";
    }

    /**
     * Resolve permission level from external_directory config.
     */
    private PermissionLevel resolveExternalDirectoryPermission(String path, Map<String, Object> extDirCfg) {
        String normalizedPath = path.replace("\\", "/").replaceAll("/+$", "");

        // Check specific path patterns
        for (Map.Entry<String, Object> entry : extDirCfg.entrySet()) {
            String pattern = entry.getKey();
            Object actionObj = entry.getValue();

            if (PermissionPatterns.matchWildcard(normalizedPath, pattern)
                    || PermissionPatterns.matchWildcard(normalizedPath + "/", pattern)
                    || PermissionPatterns.matchWildcard(normalizedPath + "/*", pattern)) {
                String action = actionObj != null ? actionObj.toString().toLowerCase() : "ask";
                return PermissionLevel.fromValue(action);
            }
        }

        // Default action
        Object defaultAction = extDirCfg.get("*");
        if (defaultAction != null) {
            return PermissionLevel.fromValue(defaultAction.toString().toLowerCase());
        }
        return PermissionLevel.ASK;
    }
}