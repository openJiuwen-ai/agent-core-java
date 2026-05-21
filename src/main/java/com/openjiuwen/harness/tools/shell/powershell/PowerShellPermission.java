/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Permission check pipeline for the PowerShell tool.
 *
 * <p>Mirrors Python's {@code _permission.py} in
 * {@code openjiuwen.harness.tools.shell.powershell}.
 */
public final class PowerShellPermission {

    private PowerShellPermission() {
    }

    private static final Set<String> FILE_OP_COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "new-item", "ni", "remove-item", "ri", "rm",
            "move-item", "mi", "mv", "copy-item", "cp", "cpi",
            "rename-item", "rni", "set-content", "sc", "add-content", "ac",
            "clear-content", "clc"
    )));

    private static final Set<String> KNOWN_SAFE_COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
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
    )));

    public static List<Pattern> compilePatterns(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<Pattern> patterns = new ArrayList<>();
        for (String patternStr : raw) {
            patterns.add(Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE));
        }
        return patterns;
    }

    public static PermissionResult checkPermission(String command, PermissionConfig config) {
        if (config.getMode() == PermissionMode.BYPASS) {
            return new PermissionResult(true);
        }

        if (config.getDenyPatterns() != null && !config.getDenyPatterns().isEmpty()) {
            for (String segment : PowerShellSemantics.splitPipeline(command)) {
                for (Pattern pattern : config.getDenyPatterns()) {
                    if (pattern.matcher(segment).find()) {
                        return new PermissionResult(false,
                                "Command denied by pattern: " + pattern.pattern());
                    }
                }
            }
        }

        if (config.getAllowPatterns() != null && !config.getAllowPatterns().isEmpty()) {
            for (Pattern pattern : config.getAllowPatterns()) {
                if (pattern.matcher(command).find()) {
                    return new PermissionResult(true);
                }
            }
        }

        if (config.getMode() == PermissionMode.READ_ONLY) {
            if (PowerShellSemantics.isReadOnly(command)) {
                return new PermissionResult(true);
            }
            return new PermissionResult(false,
                    "Read-only mode: only read/search/list commands are allowed");
        }

        if (config.getMode() == PermissionMode.ACCEPT_EDITS) {
            for (String segment : PowerShellSemantics.splitPipeline(command)) {
                String base = PowerShellSemantics.extractBaseCommand(segment);
                if (FILE_OP_COMMANDS.contains(base) || KNOWN_SAFE_COMMANDS.contains(base)) {
                    continue;
                }
                return new PermissionResult(false,
                        "Accept-edits mode: unknown command '" + base + "' requires explicit approval");
            }
            return new PermissionResult(true);
        }

        return new PermissionResult(true);
    }

    public enum PermissionMode {
        AUTO("auto"),
        READ_ONLY("read_only"),
        ACCEPT_EDITS("accept_edits"),
        BYPASS("bypass");

        private final String value;

        PermissionMode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static PermissionMode fromValue(String value) {
            if (value == null || value.isEmpty()) {
                return AUTO;
            }
            String lower = value.toLowerCase();
            for (PermissionMode mode : values()) {
                if (mode.value.equals(lower)) {
                    return mode;
                }
            }
            return AUTO;
        }
    }

    public static final class PermissionResult {
        private final boolean allowed;
        private final String reason;

        public PermissionResult(boolean allowed) {
            this.allowed = allowed;
            this.reason = null;
        }

        public PermissionResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getReason() {
            return reason;
        }
    }

    public static final class PermissionConfig {
        private PermissionMode mode;
        private List<Pattern> denyPatterns;
        private List<Pattern> allowPatterns;

        public PermissionConfig() {
            this.mode = PermissionMode.AUTO;
            this.denyPatterns = Collections.emptyList();
            this.allowPatterns = Collections.emptyList();
        }

        public PermissionConfig(PermissionMode mode) {
            this.mode = mode;
            this.denyPatterns = Collections.emptyList();
            this.allowPatterns = Collections.emptyList();
        }

        public PermissionConfig(PermissionMode mode, List<Pattern> denyPatterns, List<Pattern> allowPatterns) {
            this.mode = mode;
            this.denyPatterns = denyPatterns != null ? denyPatterns : Collections.emptyList();
            this.allowPatterns = allowPatterns != null ? allowPatterns : Collections.emptyList();
        }

        public PermissionMode getMode() {
            return mode;
        }

        public void setMode(PermissionMode mode) {
            this.mode = mode;
        }

        public List<Pattern> getDenyPatterns() {
            return denyPatterns;
        }

        public void setDenyPatterns(List<Pattern> denyPatterns) {
            this.denyPatterns = denyPatterns;
        }

        public List<Pattern> getAllowPatterns() {
            return allowPatterns;
        }

        public void setAllowPatterns(List<Pattern> allowPatterns) {
            this.allowPatterns = allowPatterns;
        }
    }
}