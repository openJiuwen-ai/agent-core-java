/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.prompts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * System prompt builder for the CLI agent.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.cli.prompts.builder}.
 *
 * Builds the system prompt using the harness SystemPromptBuilder
 * with custom sections for environment info and OPENJIUWEN.md project memory.
 */
public final class CliPromptBuilder {

    private static final int MAX_MEMORY_CHARS = 40_000;

    private static final String[] ROOT_MARKERS = {
        ".git",
        "pyproject.toml",
        "package.json",
        "Cargo.toml",
        "go.mod"
    };

    private CliPromptBuilder() {
    }

    /**
     * Walk up from cwd to find the first directory with a root marker.
     *
     * @param cwd Current working directory.
     * @return Project root path, or null if not found.
     */
    public static Optional<Path> findProjectRoot(String cwd) {
        Path current = Paths.get(cwd).toAbsolutePath();
        List<Path> parents = new ArrayList<>();
        parents.add(current);
        current.getParents().forEach(parents::add);

        for (Path parent : parents) {
            for (String marker : ROOT_MARKERS) {
                if (Files.exists(parent.resolve(marker))) {
                    return Optional.of(parent);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Load and merge OPENJIUWEN.md memory files.
     *
     * <p>Reads two layers:
     * <ol>
     *   <li>User-level: ~/.openjiuwen/OPENJIUWEN.md</li>
     *   <li>Project-level: {project_root}/OPENJIUWEN.md</li>
     * </ol>
     *
     * @param cwd Current working directory (used to locate project root).
     * @return Merged memory text, or empty string when no memory files exist.
     */
    public static String loadOpenjiuwenMd(String cwd) {
        List<String> parts = new ArrayList<>();

        // User-level
        Path userFile = Paths.get(System.getProperty("user.home"), ".openjiuwen", "OPENJIUWEN.md");
        if (Files.exists(userFile)) {
            try {
                String content = Files.readString(userFile);
                parts.add("### User-level memory\n" + content);
            } catch (IOException e) {
                // Ignore read errors
            }
        }

        // Project-level
        Optional<Path> projectRoot = findProjectRoot(cwd);
        if (projectRoot.isPresent()) {
            Path projFile = projectRoot.get().resolve("OPENJIUWEN.md");
            if (Files.exists(projFile)) {
                try {
                    String content = Files.readString(projFile);
                    parts.add("### Project-level memory\n" + content);
                } catch (IOException e) {
                    // Ignore read errors
                }
            }
        }

        if (parts.isEmpty()) {
            return "";
        }

        String combined = String.join("\n\n", parts);
        if (combined.length() > MAX_MEMORY_CHARS) {
            combined = combined.substring(0, MAX_MEMORY_CHARS);
        }

        return combined;
    }

    /**
     * Build environment info section for system prompt.
     *
     * @param cwd Current working directory.
     * @return Environment info text.
     */
    public static String buildEnvironmentInfo(String cwd) {
        StringBuilder sb = new StringBuilder();

        // Platform info
        sb.append("Platform: ").append(System.getProperty("os.name")).append("\n");
        sb.append("OS Version: ").append(System.getProperty("os.version")).append("\n");
        sb.append("Java Version: ").append(System.getProperty("java.version")).append("\n");

        // Working directory
        sb.append("Working Directory: ").append(cwd).append("\n");

        // Project root (if found)
        Optional<Path> root = findProjectRoot(cwd);
        root.ifPresent(p -> sb.append("Project Root: ").append(p).append("\n"));

        return sb.toString();
    }

    /**
     * Build the full system prompt for CLI agent.
     *
     * @param cwd Current working directory.
     * @param modelId Model identifier.
     * @return Complete system prompt string.
     */
    public static String buildSystemPrompt(String cwd, String modelId) {
        StringBuilder prompt = new StringBuilder();

        // Environment info
        prompt.append("## Environment\n\n");
        prompt.append(buildEnvironmentInfo(cwd));
        prompt.append("\n");

        // Memory section
        String memory = loadOpenjiuwenMd(cwd);
        if (!memory.isEmpty()) {
            prompt.append("## Project Memory (OPENJIUWEN.md)\n\n");
            prompt.append(memory);
            prompt.append("\n");
        }

        return prompt.toString();
    }
}