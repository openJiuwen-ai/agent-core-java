/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import java.util.List;
import java.util.Set;

/**
 * Mirrors Python's permission pipeline in
 * {@code openjiuwen/harness/tools/shell/powershell/_permission.py}.
 */
public final class PowerShellPermission {

    private static final Set<String> FILE_OP_COMMANDS = Set.of(
            "new-item", "ni", "remove-item", "ri", "rm",
            "move-item", "mi", "mv", "copy-item", "cp", "cpi",
            "rename-item", "rni", "set-content", "sc", "add-content", "ac",
            "clear-content", "clc"
    );

    private static final Set<String> KNOWN_SAFE_COMMANDS = Set.of(
            "get-childitem", "gci", "dir", "ls",
            "get-content", "gc", "type", "get-item", "gi", "test-path", "resolve-path", "get-filehash",
            "select-string", "findstr", "get-command", "where-object",
            "write-output", "echo", "write-host", "out-host",
            "set-location", "cd", "sl", "push-location", "pop-location",
            "new-item", "ni", "remove-item", "ri", "rm",
            "move-item", "mi", "mv", "copy-item", "cp", "cpi",
            "rename-item", "rni", "set-content", "sc", "add-content", "ac",
            "clear-content", "clc",
            "git", "python", "python3", "pip", "pip3", "uv",
            "node", "npm", "npx", "yarn", "pnpm",
            "make", "cmake", "cargo", "go", "java", "javac", "mvn", "gradle",
            "docker", "kubectl", "curl", "wget",
            "date", "get-date", "hostname", "whoami"
    );

    private PowerShellPermission() {
    }

    public static PermissionResult checkPermission(String command, PermissionConfig config) {
        if (config.getMode() == PermissionMode.BYPASS) {
            return new PermissionResult(true);
        }

        List<String> segments = PowerShellSemantics.splitPipeline(command);
        for (String segment : segments) {
            for (var pattern : config.getDenyPatterns()) {
                if (pattern.matcher(segment).find()) {
                    return new PermissionResult(false, "Command denied by pattern: " + pattern.pattern());
                }
            }
        }

        for (var pattern : config.getAllowPatterns()) {
            if (pattern.matcher(command).find()) {
                return new PermissionResult(true);
            }
        }

        if (config.getMode() == PermissionMode.READ_ONLY) {
            return PowerShellSemantics.isReadOnly(command)
                    ? new PermissionResult(true)
                    : new PermissionResult(false, "Read-only mode: only read/search/list commands are allowed");
        }

        if (config.getMode() == PermissionMode.ACCEPT_EDITS) {
            for (String segment : segments) {
                String base = PowerShellSemantics.extractBaseCommand(segment);
                if (FILE_OP_COMMANDS.contains(base) || KNOWN_SAFE_COMMANDS.contains(base)) {
                    continue;
                }
                return new PermissionResult(
                        false,
                        "Accept-edits mode: unknown command '" + base + "' requires explicit approval"
                );
            }
        }

        return new PermissionResult(true);
    }
}
