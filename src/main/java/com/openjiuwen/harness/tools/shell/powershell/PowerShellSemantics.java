/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * PowerShell command classification and exit-code semantic interpretation.
 *
 * <p>Mirrors Python's {@code _semantics.py} in
 * {@code openjiuwen.harness.tools.shell.powershell}.
 */
public final class PowerShellSemantics {

    private PowerShellSemantics() {
    }

    private static final Set<String> SEARCH_COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "select-string", "findstr", "get-command", "where-object"
    )));

    private static final Set<String> READ_COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "get-content", "gc", "type", "get-item", "gi", "test-path", "resolve-path", "get-filehash"
    )));

    private static final Set<String> LIST_COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "get-childitem", "gci", "dir", "ls"
    )));

    private static final Set<String> NEUTRAL_COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "write-output", "echo", "write-host", "out-host"
    )));

    private static final Set<String> SILENT_COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "set-location", "cd", "sl", "push-location", "pop-location",
            "new-item", "ni", "remove-item", "ri", "rm",
            "move-item", "mi", "mv", "copy-item", "cp", "cpi",
            "rename-item", "rni", "set-content", "sc", "add-content", "ac",
            "clear-content", "clc"
    )));

    private static final Set<CommandKind> READ_KINDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            CommandKind.SEARCH, CommandKind.READ, CommandKind.LIST
    )));

    private static final Pattern OPERATOR_PATTERN = Pattern.compile("\\s*(?:\\|\\||&&|[;|])\\s*");

    public static List<String> splitPipeline(String command) {
        if (command == null || command.isBlank()) {
            return Collections.emptyList();
        }
        String[] parts = OPERATOR_PATTERN.split(command);
        return Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static String extractBaseCommand(String segment) {
        if (segment == null || segment.isBlank()) {
            return "";
        }
        String[] tokens = segment.split("\\s+");
        for (String token : tokens) {
            if (token.equals("&") || token.equals(".")) {
                continue;
            }
            if (token.startsWith("$") && token.contains("=")) {
                continue;
            }
            if (token.startsWith("-")) {
                continue;
            }
            String base = token;
            int lastBackslash = token.lastIndexOf('\\');
            int lastSlash = token.lastIndexOf('/');
            if (lastBackslash >= 0) {
                base = token.substring(lastBackslash + 1);
            } else if (lastSlash >= 0) {
                base = token.substring(lastSlash + 1);
            }
            if (base.toLowerCase().endsWith(".exe")) {
                base = base.substring(0, base.length() - 4);
            }
            return base.toLowerCase();
        }
        return "";
    }

    public static CommandKind lookupKind(String base) {
        if (SEARCH_COMMANDS.contains(base)) {
            return CommandKind.SEARCH;
        }
        if (READ_COMMANDS.contains(base)) {
            return CommandKind.READ;
        }
        if (LIST_COMMANDS.contains(base)) {
            return CommandKind.LIST;
        }
        if (NEUTRAL_COMMANDS.contains(base)) {
            return CommandKind.NEUTRAL;
        }
        if (SILENT_COMMANDS.contains(base)) {
            return CommandKind.SILENT;
        }
        return CommandKind.OTHER;
    }

    public static boolean isReadOnly(String command) {
        List<String> parts = splitPipeline(command);
        if (parts.isEmpty()) {
            return false;
        }
        for (String part : parts) {
            String base = extractBaseCommand(part);
            CommandKind kind = lookupKind(base);
            if (kind == CommandKind.NEUTRAL) {
                continue;
            }
            if (!READ_KINDS.contains(kind)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isSilent(String command) {
        List<String> parts = splitPipeline(command);
        if (parts.isEmpty()) {
            return false;
        }
        for (String part : parts) {
            String base = extractBaseCommand(part);
            CommandKind kind = lookupKind(base);
            if (kind == CommandKind.NEUTRAL) {
                continue;
            }
            if (kind != CommandKind.SILENT) {
                return false;
            }
        }
        return true;
    }

    public static ExitCodeMeaning interpretExitCode(String command, int exitCode, String stdout, String stderr) {
        if (exitCode == 0) {
            return new ExitCodeMeaning(false);
        }
        return new ExitCodeMeaning(true);
    }
}