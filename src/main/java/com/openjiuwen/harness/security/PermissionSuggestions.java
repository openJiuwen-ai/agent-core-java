/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Permission suggestion builders for allow-always persistence.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/security/suggestions.py}.</p>
 */
public final class PermissionSuggestions {

    private static final Set<String> SHELL_SUGGESTION_TOOLS =
            Set.of("bash", "mcp_exec_command", "create_terminal");
    private static final Set<String> PATH_SUGGESTION_TOOLS =
            Set.of(
                    "read_file", "write_file", "edit_file",
                    "read_text_file", "write_text_file",
                    "write", "read",
                    "glob_file_search", "glob", "list_dir", "list_files",
                    "grep", "search_replace"
            );
    private static final List<String> PATH_SUGGESTION_KEYS =
            List.of("path", "file_path", "target_file", "file", "old_path", "new_path",
                    "source_path", "dest_path", "directory", "dir");

    private PermissionSuggestions() {
    }

    public static List<PermissionSuggestion> buildPermissionSuggestions(
            String toolName,
            Map<String, Object> toolArgs,
            ShellAstParseResult shellAstResult
    ) {
        if (SHELL_SUGGESTION_TOOLS.contains(toolName)) {
            String command = stringArg(toolArgs, "command", "cmd").trim();
            if (command.isEmpty()) {
                return List.of();
            }
            return buildShellPermissionSuggestions(toolName, command, shellAstResult);
        }
        if (PATH_SUGGESTION_TOOLS.contains(toolName)) {
            PermissionSuggestion suggestion = buildPathPermissionSuggestion(toolName, toolArgs);
            return suggestion == null ? List.of() : List.of(suggestion);
        }
        return List.of();
    }

    public static List<PermissionSuggestion> buildShellPermissionSuggestions(
            String toolName,
            String command,
            ShellAstParseResult shellAstResult
    ) {
        ShellAstParseResult parsed = shellAstResult == null ? ShellAst.parseShellForPermission(command) : shellAstResult;
        ShellStructureFlags flags = parsed.getFlags();
        if ("too_complex".equals(parsed.getKind())) {
            return List.of();
        }
        if ("parse_unavailable".equals(parsed.getKind()) && flags.hasRiskyStructure()) {
            return List.of();
        }
        if (flags.hasInputRedirection()
                || flags.hasOutputRedirection()
                || flags.hasCommandSubstitution()
                || flags.hasProcessSubstitution()
                || flags.hasHeredoc()
                || flags.hasSubshell()
                || flags.hasCommandGroup()
                || flags.hasParameterExpansion()) {
            return List.of();
        }

        if ("simple".equals(parsed.getKind()) && parsed.getSubcommands().size() > 1) {
            List<PermissionSuggestion> suggestions = new ArrayList<>();
            for (ShellSubcommand subcommand : parsed.getSubcommands()) {
                PermissionSuggestion suggestion = buildSingleShellSuggestion(toolName, subcommand.getText());
                if (suggestion != null) {
                    suggestions.add(suggestion);
                }
            }
            return dedupeSuggestions(suggestions);
        }

        if ("simple".equals(parsed.getKind()) && parsed.getSubcommands().size() == 1) {
            PermissionSuggestion suggestion =
                    buildSingleShellSuggestion(toolName, parsed.getSubcommands().get(0).getText());
            return suggestion == null ? List.of() : List.of(suggestion);
        }

        PermissionSuggestion suggestion = buildSingleShellSuggestion(toolName, command);
        return suggestion == null ? List.of() : List.of(suggestion);
    }

    private static PermissionSuggestion buildSingleShellSuggestion(String toolName, String command) {
        String text = command == null ? "" : command.trim();
        if (text.isEmpty()) {
            return null;
        }
        String heredocPrefix = extractPrefixBeforeHeredoc(text);
        if (heredocPrefix != null) {
            return new PermissionSuggestion(
                    new String[]{toolName},
                    "command",
                    buildPrefixPattern(heredocPrefix),
                    "allow",
                    "prefix",
                    "heredoc_prefix"
            );
        }
        if (text.contains("\n")) {
            String firstLine = text.split("\\R", 2)[0].trim();
            String prefix = extractSimpleCommandPrefix(firstLine);
            if (prefix != null) {
                return new PermissionSuggestion(
                        new String[]{toolName},
                        "command",
                        buildPrefixPattern(prefix),
                        "allow",
                        "prefix",
                        "first_line_prefix"
                );
            }
            return null;
        }
        return new PermissionSuggestion(
                new String[]{toolName},
                "command",
                text,
                "allow",
                "exact",
                "exact_command"
        );
    }

    private static String extractPrefixBeforeHeredoc(String command) {
        int index = command.indexOf("<<");
        if (index < 0) {
            return null;
        }
        String before = command.substring(0, index).trim();
        if (before.isEmpty()) {
            return null;
        }
        String prefix = extractSimpleCommandPrefix(before);
        return prefix == null ? before : prefix;
    }

    private static String extractSimpleCommandPrefix(String command) {
        List<String> argv = ShellAst.parseShellForPermission(command).getSubcommands().stream()
                .findFirst()
                .map(ShellSubcommand::getArgv)
                .orElse(List.of());
        if (argv.isEmpty()) {
            return null;
        }
        return argv.size() == 1 ? argv.get(0) : argv.get(0) + " " + argv.get(1);
    }

    private static String buildPrefixPattern(String prefix) {
        return prefix.trim() + " *";
    }

    private static PermissionSuggestion buildPathPermissionSuggestion(String toolName, Map<String, Object> toolArgs) {
        if (toolArgs == null) {
            return null;
        }
        for (String key : PATH_SUGGESTION_KEYS) {
            Object value = toolArgs.get(key);
            if (value instanceof String text && !text.trim().isEmpty()) {
                return new PermissionSuggestion(
                        new String[]{toolName},
                        "path",
                        text.trim(),
                        "allow",
                        "exact",
                        "exact_path"
                );
            }
        }
        for (Map.Entry<String, Object> entry : toolArgs.entrySet()) {
            if (!(entry.getValue() instanceof String text)) {
                continue;
            }
            String trimmed = text.trim();
            if (!trimmed.isEmpty() && valueLooksLikePath(entry.getKey(), trimmed)) {
                return new PermissionSuggestion(
                        new String[]{toolName},
                        "path",
                        trimmed,
                        "allow",
                        "exact",
                        "derived_exact_path"
                );
            }
        }
        return null;
    }

    private static boolean valueLooksLikePath(String key, String text) {
        if (PATH_SUGGESTION_KEYS.contains(key)) {
            return true;
        }
        return text.contains("/") || text.contains("\\") || (text.length() > 1 && text.charAt(1) == ':');
    }

    private static List<PermissionSuggestion> dedupeSuggestions(List<PermissionSuggestion> suggestions) {
        Set<String> seen = new LinkedHashSet<>();
        List<PermissionSuggestion> result = new ArrayList<>();
        for (PermissionSuggestion suggestion : suggestions) {
            String signature = String.join("|",
                    String.join(",", suggestion.tools()),
                    suggestion.matchType(),
                    suggestion.pattern(),
                    suggestion.action());
            if (seen.add(signature)) {
                result.add(suggestion);
            }
        }
        return result;
    }

    private static String stringArg(Map<String, Object> toolArgs, String... keys) {
        if (toolArgs == null) {
            return "";
        }
        for (String key : keys) {
            Object value = toolArgs.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "";
    }
}
