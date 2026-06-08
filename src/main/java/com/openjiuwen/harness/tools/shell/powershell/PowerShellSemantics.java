/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * PowerShell command classification and exit-code semantic interpretation.
 *
 * <p>Mirrors Python's {@code PowerShellSemantics} in
 * {@code openjiuwen/harness/tools/shell/powershell/_semantics.py}.
 */
public final class PowerShellSemantics {

    private static final Set<String> SEARCH_COMMANDS = Set.of(
            "select-string", "sls", "findstr", "get-command", "where-object", "where"
    );
    private static final Set<String> READ_COMMANDS = Set.of(
            "get-content", "gc", "type", "get-item", "gi", "test-path", "resolve-path", "get-filehash",
            "select-object", "select", "sort-object", "sort", "format-table", "ft", "format-list", "fl",
            "format-wide", "fw", "foreach-object", "foreach", "measure-object"
    );
    private static final Set<String> LIST_COMMANDS = Set.of(
            "get-childitem", "gci", "dir", "ls"
    );
    private static final Set<String> NEUTRAL_COMMANDS = Set.of(
            "write-output", "echo", "write-host", "out-host"
    );
    private static final Set<String> SILENT_COMMANDS = Set.of(
            "set-location", "cd", "sl", "push-location", "pop-location",
            "new-item", "ni", "remove-item", "ri", "rm",
            "move-item", "mi", "mv", "copy-item", "cp", "cpi",
            "rename-item", "rni", "set-content", "sc", "add-content", "ac",
            "clear-content", "clc"
    );
    private static final Set<String> GET_CHILD_ITEM_COMMANDS = Set.of(
            "get-childitem", "gci", "dir", "ls"
    );
    private static final Set<String> SEARCH_EXIT_ONE_COMMANDS = Set.of(
            "select-string", "sls", "findstr"
    );
    private static final Set<CommandKind> READ_KINDS = Set.of(
            CommandKind.SEARCH, CommandKind.READ, CommandKind.LIST
    );
    private static final Map<String, CommandKind> KIND_LOOKUP = buildKindLookup();
    private static final Map<String, ExitCodeHandler> SEMANTICS_TABLE = buildSemanticsTable();

    private PowerShellSemantics() {
    }

    public static List<String> splitPipeline(String command) {
        if (command == null || command.isBlank()) {
            return List.of();
        }

        List<String> parts = new ArrayList<>();
        int start = 0;
        int braceDepth = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        Character quote = null;
        boolean escaped = false;
        int index = 0;

        while (index < command.length()) {
            char current = command.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (current == '`') {
                escaped = true;
            } else if (quote != null) {
                if (current == quote) {
                    quote = null;
                }
            } else if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == '{') {
                braceDepth++;
            } else if (current == '(') {
                parenDepth++;
            } else if (current == '[') {
                bracketDepth++;
            } else if (current == '}') {
                braceDepth = Math.max(0, braceDepth - 1);
            } else if (current == ')') {
                parenDepth = Math.max(0, parenDepth - 1);
            } else if (current == ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
            } else if (braceDepth == 0 && parenDepth == 0 && bracketDepth == 0) {
                int operatorLength = operatorLengthAt(command, index);
                if (operatorLength > 0) {
                    addTrimmedPart(parts, command.substring(start, index));
                    index += operatorLength;
                    start = index;
                    continue;
                }
            }
            index++;
        }

        addTrimmedPart(parts, command.substring(start));
        return List.copyOf(parts);
    }

    public static boolean isReadOnly(String command) {
        List<String> parts = splitPipeline(command);
        if (parts.isEmpty()) {
            return false;
        }
        for (String part : parts) {
            CommandKind kind = KIND_LOOKUP.getOrDefault(extractBaseCommand(part), CommandKind.OTHER);
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
            CommandKind kind = KIND_LOOKUP.getOrDefault(extractBaseCommand(part), CommandKind.OTHER);
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

        List<String> parts = splitPipeline(command);
        if (parts.isEmpty()) {
            return new ExitCodeMeaning(true);
        }

        String baseCommand = extractBaseCommand(parts.get(parts.size() - 1));
        ExitCodeHandler handler = SEMANTICS_TABLE.get(baseCommand);
        if (handler != null) {
            return handler.interpret(exitCode, safe(stdout), safe(stderr));
        }

        boolean partialSuccess = exitCode == 1 && !safe(stdout).isEmpty() && safe(stderr).isEmpty();
        if (partialSuccess && isReadOnly(command)) {
            return new ExitCodeMeaning(
                    false,
                    "PowerShell returned exit code 1 after producing output; treating output as partial result"
            );
        }

        return new ExitCodeMeaning(true);
    }

    static String extractBaseCommand(String segment) {
        if (segment == null || segment.isBlank()) {
            return "";
        }
        for (String token : segment.split("\\s+")) {
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
            int lastBackslash = base.lastIndexOf('\\');
            int lastSlash = base.lastIndexOf('/');
            if (lastBackslash >= 0) {
                base = base.substring(lastBackslash + 1);
            }
            if (lastSlash >= 0) {
                base = base.substring(lastSlash + 1);
            }
            String lower = base.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".exe")) {
                lower = lower.substring(0, lower.length() - 4);
            }
            return lower;
        }
        return "";
    }

    private static Map<String, CommandKind> buildKindLookup() {
        Map<String, CommandKind> lookup = new HashMap<>();
        addKindEntries(lookup, SEARCH_COMMANDS, CommandKind.SEARCH);
        addKindEntries(lookup, READ_COMMANDS, CommandKind.READ);
        addKindEntries(lookup, LIST_COMMANDS, CommandKind.LIST);
        addKindEntries(lookup, NEUTRAL_COMMANDS, CommandKind.NEUTRAL);
        addKindEntries(lookup, SILENT_COMMANDS, CommandKind.SILENT);
        return Map.copyOf(lookup);
    }

    private static Map<String, ExitCodeHandler> buildSemanticsTable() {
        Map<String, ExitCodeHandler> table = new HashMap<>();
        for (String command : GET_CHILD_ITEM_COMMANDS) {
            table.put(command, PowerShellSemantics::getChildItemSemantics);
        }
        for (String command : SEARCH_EXIT_ONE_COMMANDS) {
            table.put(command, PowerShellSemantics::searchSemantics);
        }
        return Map.copyOf(table);
    }

    private static void addKindEntries(Map<String, CommandKind> lookup, Set<String> commands, CommandKind kind) {
        for (String command : commands) {
            lookup.put(command, kind);
        }
    }

    private static int operatorLengthAt(String command, int index) {
        char current = command.charAt(index);
        char next = index + 1 < command.length() ? command.charAt(index + 1) : '\0';
        if ((current == '|' || current == '&') && next == current) {
            return 2;
        }
        if (current == '|' || current == ';') {
            return 1;
        }
        return 0;
    }

    private static ExitCodeMeaning getChildItemSemantics(int code, String stdout, String stderr) {
        if (code == 0) {
            return new ExitCodeMeaning(false);
        }
        if (code == 1 && !stdout.isEmpty() && stderr.isEmpty()) {
            return new ExitCodeMeaning(false, "Partial results produced; some items may be inaccessible");
        }
        return new ExitCodeMeaning(true, "Get-ChildItem error (exit " + code + ")");
    }

    private static ExitCodeMeaning searchSemantics(int code, String stdout, String stderr) {
        if (code == 0) {
            return new ExitCodeMeaning(false);
        }
        if (code == 1 && stdout.isEmpty() && stderr.isEmpty()) {
            return new ExitCodeMeaning(false, "No matches found");
        }
        return new ExitCodeMeaning(true, "Search command error (exit " + code + ")");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void addTrimmedPart(List<String> parts, String candidate) {
        String trimmed = candidate.trim();
        if (!trimmed.isEmpty()) {
            parts.add(trimmed);
        }
    }

    @FunctionalInterface
    private interface ExitCodeHandler {
        ExitCodeMeaning interpret(int code, String stdout, String stderr);
    }
}
