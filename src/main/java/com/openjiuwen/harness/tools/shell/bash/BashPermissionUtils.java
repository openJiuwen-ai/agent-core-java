/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Permission check pipeline for the Bash tool.
 *
 * <p>Mirrors Python's functions in
 * {@code openjiuwen.harness.tools.shell.bash._permission}.
 *
 * <p>Five-layer pipeline:
 * 1. bypass mode -> allow everything
 * 2. deny_patterns -> block matching commands
 * 3. allow_patterns -> pass matching commands
 * 4. mode check (read_only / accept_edits)
 * 5. pipeline sub-command validation
 */
public final class BashPermissionUtils {

    private static final Set<String> FILE_OP_COMMANDS = new HashSet<>(Arrays.asList(
            "mkdir", "touch", "rm", "rmdir", "mv", "cp",
            "sed", "chmod", "chown", "chgrp", "ln"
    ));

    private static final Set<String> KNOWN_SAFE_COMMANDS = new HashSet<>(Arrays.asList(
            "find", "grep", "egrep", "fgrep", "rg", "ag", "ack",
            "locate", "which", "whereis", "type", "command",
            "cat", "head", "tail", "less", "more", "wc", "stat",
            "file", "strings", "jq", "yq", "awk", "gawk", "cut",
            "sort", "uniq", "tr", "tee", "od", "xxd", "hexdump",
            "sha256sum", "sha1sum", "md5sum", "md5", "shasum",
            "ls", "tree", "du", "df", "lsof",
            "echo", "printf", "true", "false", ":", "test", "[",
            "mkdir", "touch", "rm", "rmdir", "mv", "cp",
            "sed", "chmod", "chown", "chgrp", "ln",
            "cd", "export", "unset", "source", ".", "wait", "pushd", "popd",
            "git", "python", "python3", "pip", "pip3", "uv",
            "node", "npm", "npx", "yarn", "pnpm",
            "make", "cmake", "cargo", "go", "java", "javac", "mvn", "gradle",
            "docker", "docker-compose", "kubectl",
            "curl", "wget", "ssh", "scp", "rsync",
            "tar", "zip", "unzip", "gzip", "gunzip",
            "date", "env", "id", "whoami", "hostname", "uname", "ps", "top",
            "diff", "patch", "xargs", "basename", "dirname", "realpath"
    ));

    private BashPermissionUtils() {
    }

    /**
     * Run the 5-layer permission pipeline.
     *
     * @param command Shell command string
     * @param config  Permission configuration
     * @return PermissionResult indicating allow or deny with reason
     */
    public static PermissionResult checkPermission(String command, PermissionConfig config) {
        if (config.getMode() == PermissionMode.BYPASS) {
            return new PermissionResult(true);
        }

        List<String> segments = BashSemanticsUtils.splitPipeline(command);

        if (!config.getDenyPatterns().isEmpty()) {
            for (String segment : segments) {
                for (Pattern pattern : config.getDenyPatterns()) {
                    if (pattern.matcher(segment).find()) {
                        return new PermissionResult(false,
                                "Command denied by pattern: " + pattern.pattern());
                    }
                }
            }
        }

        if (!config.getAllowPatterns().isEmpty()) {
            for (Pattern pattern : config.getAllowPatterns()) {
                if (pattern.matcher(command).find()) {
                    return new PermissionResult(true);
                }
            }
        }

        if (config.getMode() == PermissionMode.READ_ONLY) {
            if (BashSemanticsUtils.isReadOnly(command)) {
                return new PermissionResult(true);
            }
            return new PermissionResult(false,
                    "Read-only mode: only read/search/list commands are allowed");
        }

        if (config.getMode() == PermissionMode.ACCEPT_EDITS) {
            for (String segment : segments) {
                String base = BashSemanticsUtils.extractBaseCommand(segment);
                if (base.isEmpty()) {
                    continue;
                }
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

    public static Set<String> getFileOpCommands() {
        return FILE_OP_COMMANDS;
    }

    public static Set<String> getKnownSafeCommands() {
        return KNOWN_SAFE_COMMANDS;
    }
}