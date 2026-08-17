/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.tiered;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tool category classification for tiered permission rules.
 *
 * <p>Mirrors Python {@code tiered_policy._SHELL_TOOLS / _PATH_TOOLS / _NETWORK_TOOLS}.
 * Rules must target a single consistent category; path-class rules are handled by
 * {@code file_guard} (Pipeline B) and are skipped on Pipeline A.
 *
 * @since 0.1.15
 */
public final class ToolCategory {

    /** Shell command tools. */
    public static final Set<String> SHELL_TOOLS =
            Set.of("bash", "mcp_exec_command", "create_terminal");

    /** Path-access tools (governed by file_guard, not tiered parameter rules). */
    public static final Set<String> PATH_TOOLS = Set.of(
            "read_file", "write_file", "edit_file",
            "read_text_file", "write_text_file",
            "write", "read",
            "glob_file_search", "glob", "list_dir", "list_files",
            "grep", "search_replace");

    /** Network tools. */
    public static final Set<String> NETWORK_TOOLS =
            Set.of("mcp_fetch_webpage", "mcp_free_search", "mcp_paid_search");

    /** Argument keys whose string values are treated as paths. */
    public static final Set<String> PATH_ARG_KEYS = Set.of(
            "path", "file_path", "target_file", "file", "old_path", "new_path",
            "source_path", "dest_path", "directory", "dir");

    private ToolCategory() {
    }

    /**
     * Classify a tool name.
     *
     * @param toolName tool name
     * @return {@code "shell"}/{@code "path"}/{@code "network"}, or {@code null} when unknown
     * @since 0.1.15
     */
    public static String of(String toolName) {
        if (SHELL_TOOLS.contains(toolName)) {
            return "shell";
        }
        if (PATH_TOOLS.contains(toolName)) {
            return "path";
        }
        if (NETWORK_TOOLS.contains(toolName)) {
            return "network";
        }
        return null;
    }

    /**
     * Whether all tools in a rule share one category.
     *
     * @param tools tool names
     * @return true when consistent and non-empty
     * @since 0.1.15
     */
    public static boolean ruleToolsCategoryConsistent(List<String> tools) {
        Set<String> categories = new HashSet<>();
        for (String tool : tools) {
            String category = of(tool);
            if (category == null) {
                return false;
            }
            categories.add(category);
            if (categories.size() > 1) {
                return false;
            }
        }
        return !categories.isEmpty();
    }
}
