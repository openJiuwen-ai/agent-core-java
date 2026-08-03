/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Command classification and exit-code semantic interpretation.
 *
 * <p>Mirrors Python's {@code classify_command}, {@code is_read_only},
 * {@code is_silent}, and {@code interpret_exit_code} in
 * {@code openjiuwen/harness/tools/shell/bash/_semantics.py}.
 */
public final class BashSemantics {

    private static final Set<String> SEARCH_COMMANDS = Set.of(
            "find", "grep", "egrep", "fgrep", "rg", "ag", "ack",
            "locate", "which", "whereis", "type", "command", "findstr"
    );
    private static final Set<String> READ_COMMANDS = Set.of(
            "cat", "head", "tail", "less", "more", "wc", "stat",
            "file", "strings", "jq", "yq", "awk", "gawk", "cut",
            "sort", "uniq", "tr", "tee", "od", "xxd", "hexdump",
            "sha256sum", "sha1sum", "md5sum", "md5", "shasum",
            "get-content", "get-item", "test-path", "select-object", "where-object"
    );
    private static final Set<String> LIST_COMMANDS = Set.of(
            "ls", "dir", "tree", "du", "df", "lsof", "lsblk", "get-childitem"
    );
    private static final Set<String> NEUTRAL_COMMANDS = Set.of(
            "echo", "printf", "true", "false", ":", "test", "["
    );
    private static final Set<String> SILENT_COMMANDS = Set.of(
            "mv", "cp", "rm", "mkdir", "rmdir", "chmod", "chown",
            "chgrp", "touch", "ln", "cd", "export", "unset",
            "source", ".", "wait", "pushd", "popd"
    );
    private static final Set<CommandKind> READ_KINDS = Set.of(CommandKind.SEARCH, CommandKind.READ, CommandKind.LIST);
    private static final Pattern OPERATOR_RE = Pattern.compile("\\s*(?:\\|\\||&&|[;|])\\s*");
    private static final Map<String, CommandKind> KIND_LOOKUP = buildKindLookup();
    private static final Map<String, ExitCodeHandler> SEMANTICS_TABLE = buildSemanticsTable();

    private BashSemantics() {
    }

    public static CommandKind classifyCommand(String command) {
        List<String> parts = splitPipeline(command);
        if (parts.isEmpty()) {
            return CommandKind.OTHER;
        }
        String base = extractBaseCommand(parts.get(parts.size() - 1));
        return KIND_LOOKUP.getOrDefault(base, CommandKind.OTHER);
    }

    public static List<String> splitPipeline(String command) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
        String[] rawParts = OPERATOR_RE.split(command);
        List<String> parts = new ArrayList<>();
        for (String rawPart : rawParts) {
            String trimmed = rawPart.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
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

        String base = extractBaseCommand(parts.get(parts.size() - 1));
        ExitCodeHandler handler = SEMANTICS_TABLE.get(base);
        if (handler != null) {
            return handler.interpret(exitCode, safe(stdout), safe(stderr));
        }
        return new ExitCodeMeaning(true);
    }

    static String extractBaseCommand(String segment) {
        if (segment == null || segment.isBlank()) {
            return "";
        }
        String[] tokens = segment.split("\\s+");
        for (String token : tokens) {
            if (token.contains("=") && !token.startsWith("-")) {
                continue;
            }
            String base = stripQuotes(token);
            int lastSlash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
            if (lastSlash >= 0) {
                base = base.substring(lastSlash + 1);
            }
            String normalized = base.toLowerCase(Locale.ROOT);
            if (normalized.endsWith(".exe")) {
                normalized = normalized.substring(0, normalized.length() - 4);
            }
            return normalized;
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
        for (String command : List.of("grep", "egrep", "fgrep", "rg", "ag", "ack", "findstr")) {
            table.put(command, BashSemantics::grepSemantics);
        }
        table.put("find", BashSemantics::findSemantics);
        table.put("diff", BashSemantics::diffSemantics);
        table.put("test", BashSemantics::testSemantics);
        table.put("[", BashSemantics::testSemantics);
        for (String command : List.of("get-content", "get-item", "get-childitem", "select-object", "where-object")) {
            table.put(command, BashSemantics::powershellReadSemantics);
        }
        return Map.copyOf(table);
    }

    private static void addKindEntries(Map<String, CommandKind> lookup, Set<String> commands, CommandKind kind) {
        for (String command : commands) {
            lookup.put(command, kind);
        }
    }

    private static ExitCodeMeaning grepSemantics(int code, String stdout, String stderr) {
        if (code == 0) {
            return new ExitCodeMeaning(false);
        }
        if (code == 1) {
            return new ExitCodeMeaning(false, "No matches found");
        }
        return new ExitCodeMeaning(true, "grep error (exit " + code + ")");
    }

    private static ExitCodeMeaning findSemantics(int code, String stdout, String stderr) {
        if (code == 0) {
            return new ExitCodeMeaning(false);
        }
        if (code == 1) {
            return new ExitCodeMeaning(false, "Some directories inaccessible");
        }
        return new ExitCodeMeaning(true, "find error (exit " + code + ")");
    }

    private static ExitCodeMeaning diffSemantics(int code, String stdout, String stderr) {
        if (code == 0) {
            return new ExitCodeMeaning(false, "Files are identical");
        }
        if (code == 1) {
            return new ExitCodeMeaning(false, "Files differ");
        }
        return new ExitCodeMeaning(true, "diff error (exit " + code + ")");
    }

    private static ExitCodeMeaning testSemantics(int code, String stdout, String stderr) {
        if (code == 0) {
            return new ExitCodeMeaning(false, "Condition is true");
        }
        if (code == 1) {
            return new ExitCodeMeaning(false, "Condition is false");
        }
        return new ExitCodeMeaning(true, "test error (exit " + code + ")");
    }

    private static ExitCodeMeaning powershellReadSemantics(int code, String stdout, String stderr) {
        if (code == 0) {
            return new ExitCodeMeaning(false);
        }
        if (code == 1 && stderr.isBlank()) {
            return new ExitCodeMeaning(false, "No output returned");
        }
        return new ExitCodeMeaning(true, "PowerShell read command error (exit " + code + ")");
    }

    private static String stripQuotes(String token) {
        String result = token;
        while (!result.isEmpty() && (result.charAt(0) == '"' || result.charAt(0) == '\'')) {
            result = result.substring(1);
        }
        while (!result.isEmpty()) {
            char last = result.charAt(result.length() - 1);
            if (last == '"' || last == '\'') {
                result = result.substring(0, result.length() - 1);
            } else {
                break;
            }
        }
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface ExitCodeHandler {
        ExitCodeMeaning interpret(int code, String stdout, String stderr);
    }
}
