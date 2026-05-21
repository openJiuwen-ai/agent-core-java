/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Permission suggestion builders for allow_always persistence.
 *
 * <p>Mirrors Python's {@code suggestions} module in
 * {@code openjiuwen.harness.security.suggestions}.
 */
public final class PermissionSuggestions {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionSuggestions.class);

    private static final Set<String> SHELL_SUGGESTION_TOOLS = Set.of("bash", "mcp_exec_command", "create_terminal");
    private static final Set<String> PATH_SUGGESTION_TOOLS = Set.of(
            "read_file", "write_file", "edit_file",
            "read_text_file", "write_text_file",
            "write", "read",
            "glob_file_search", "glob", "list_dir", "list_files",
            "grep", "search_replace"
    );
    private static final Set<String> PATH_SUGGESTION_KEYS = Set.of(
            "path", "file_path", "target_file", "file", "old_path", "new_path",
            "source_path", "dest_path", "directory", "dir"
    );

    private PermissionSuggestions() {
    }

    /**
     * Permission suggestion data class.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Suggestion {
        private List<String> tools;
        private String matchType;
        private String pattern;
        @Builder.Default
        private String action = "allow";
        @Builder.Default
        private String scope = "exact";
        private String reason;
    }

    /**
     * Build permission suggestions for a tool call.
     *
     * <p>Mirrors Python's {@code build_permission_suggestions} function.
     */
    public static List<Suggestion> buildPermissionSuggestions(
            String toolName,
            Map<String, Object> toolArgs,
            ShellStructureAnalysis shellAstResult) {
        if (SHELL_SUGGESTION_TOOLS.contains(toolName)) {
            String command = extractCommand(toolArgs);
            if (command == null || command.isEmpty()) {
                return List.of();
            }
            return buildShellPermissionSuggestions(toolName, command, shellAstResult);
        }
        if (PATH_SUGGESTION_TOOLS.contains(toolName)) {
            Suggestion suggestion = buildPathPermissionSuggestion(toolName, toolArgs);
            return suggestion != null ? List.of(suggestion) : List.of();
        }
        return List.of();
    }

    /**
     * Build shell command permission suggestions.
     */
    public static List<Suggestion> buildShellPermissionSuggestions(
            String toolName,
            String command,
            ShellStructureAnalysis shellAstResult) {
        if (shellAstResult == null) {
            shellAstResult = ShellStructureParser.analyze(command);
        }

        if (shellAstResult.getKind() == ShellStructureAnalysis.Kind.TOO_COMPLEX) {
            return List.of();
        }
        if (shellAstResult.getKind() == ShellStructureAnalysis.Kind.PARSE_UNAVAILABLE
                && shellAstResult.hasRiskyStructure()) {
            return List.of();
        }

        // For simple commands, generate a safe wildcard pattern
        String cmd = command.strip();
        if (cmd.isEmpty()) {
            return List.of();
        }

        // Split command to get base command
        String[] tokens = cmd.split("\\s+");
        if (tokens.length == 0) {
            return List.of();
        }

        String baseCmd = tokens[0];
        String pattern = PermissionPatterns.buildCommandAllowPattern(baseCmd);

        return List.of(Suggestion.builder()
                .tools(List.of(toolName))
                .matchType("command")
                .pattern(pattern)
                .action("allow")
                .reason("User approved shell command")
                .build());
    }

    /**
     * Build path-based permission suggestion.
     */
    private static Suggestion buildPathPermissionSuggestion(String toolName, Map<String, Object> toolArgs) {
        String pathValue = extractPathValue(toolArgs);
        if (pathValue == null || pathValue.isEmpty()) {
            return null;
        }

        // Normalize path
        String normalizedPath = pathValue.replace("\\", "/").replaceAll("/+$", "");

        return Suggestion.builder()
                .tools(List.of(toolName))
                .matchType("path")
                .pattern(normalizedPath + "/*")
                .action("allow")
                .reason("User approved path access")
                .build();
    }

    /**
     * Extract command from tool args.
     */
    private static String extractCommand(Map<String, Object> toolArgs) {
        if (toolArgs == null) return null;
        Object cmdObj = toolArgs.get("command");
        if (cmdObj == null) cmdObj = toolArgs.get("cmd");
        return cmdObj != null ? cmdObj.toString().strip() : null;
    }

    /**
     * Extract path value from tool args.
     */
    private static String extractPathValue(Map<String, Object> toolArgs) {
        if (toolArgs == null) return null;
        for (String key : PATH_SUGGESTION_KEYS) {
            Object value = toolArgs.get(key);
            if (value != null && !value.toString().isEmpty()) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * Merge suggestions into permissions approval_overrides.
     *
     * <p>Mirrors Python's {@code _persist_tiered_approval_override_suggestions} function.
     */
    public static boolean mergeSuggestionsIntoPermissions(
            Map<String, Object> permissions,
            List<Suggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return false;
        }

        Object overridesObj = permissions.get("approval_overrides");
        List<Map<String, Object>> overrides;
        if (overridesObj instanceof List) {
            overrides = (List<Map<String, Object>>) overridesObj;
        } else {
            overrides = new ArrayList<>();
            permissions.put("approval_overrides", overrides);
        }

        boolean persistedAny = false;
        for (Suggestion suggestion : suggestions) {
            for (String toolName : suggestion.getTools()) {
                if (ensureSingleAllowOverride(overrides, toolName, suggestion)) {
                    persistedAny = true;
                }
            }
        }
        return persistedAny;
    }

    /**
     * Ensure a single allow override entry exists.
     */
    private static boolean ensureSingleAllowOverride(
            List<Map<String, Object>> overrides,
            String toolName,
            Suggestion suggestion) {
        // Check if already exists
        for (Map<String, Object> existing : overrides) {
            if (!(existing instanceof Map)) continue;
            Object toolsObj = existing.get("tools");
            List<String> tools = toolsObj instanceof List
                    ? (List<String>) toolsObj
                    : toolsObj instanceof String
                    ? List.of((String) toolsObj)
                    : List.of();

            String existingMatchType = (String) existing.get("match_type");
            String existingPattern = (String) existing.get("pattern");
            String existingAction = existing.get("action") != null
                    ? existing.get("action").toString().toLowerCase().strip()
                    : "";

            if (tools.contains(toolName)
                    && Objects.equals(existingMatchType, suggestion.getMatchType())
                    && Objects.equals(existingPattern, suggestion.getPattern())
                    && "allow".equals(existingAction)) {
                LOG.info("[PermissionSuggestions] permission.persist.skip tool={} reason=approval_override_exists",
                        toolName);
                return true;
            }
        }

        // Add new entry
        String id = buildApprovalOverrideId(toolName, suggestion.getMatchType(), suggestion.getPattern());
        Map<String, Object> newEntry = new LinkedHashMap<>();
        newEntry.put("id", id);
        newEntry.put("tools", List.of(toolName));
        newEntry.put("match_type", suggestion.getMatchType());
        newEntry.put("pattern", suggestion.getPattern());
        newEntry.put("action", suggestion.getAction());
        overrides.add(newEntry);
        return true;
    }

    /**
     * Build approval override ID.
     */
    private static String buildApprovalOverrideId(String toolName, String matchType, String pattern) {
        String raw = "user_allow_" + toolName + "_" + matchType + "_" + pattern;
        String collapsed = raw.replaceAll("[^a-zA-Z0-9]+", "_").strip().toLowerCase();
        if (collapsed.isEmpty()) {
            return "user_allow_override";
        }
        return collapsed.substring(0, Math.min(collapsed.length(), 120));
    }
}