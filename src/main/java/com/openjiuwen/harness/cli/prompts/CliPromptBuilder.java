/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.prompts;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.HarnessPromptsPackage;
import com.openjiuwen.harness.prompts.PromptMode;
import com.openjiuwen.harness.prompts.SystemPromptBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Mirrors Python's {@code build_system_prompt} helpers in
 * {@code openjiuwen/harness/cli/prompts/builder.py}.
 */
public final class CliPromptBuilder {

    public static final int MAX_MEMORY_CHARS = 40_000;

    private static final List<String> ROOT_MARKERS = List.of(
            ".git",
            "pyproject.toml",
            "package.json",
            "Cargo.toml",
            "go.mod"
    );

    private CliPromptBuilder() {
    }

    public static Optional<Path> findProjectRoot(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return Optional.empty();
        }
        try {
            Path current = Paths.get(cwd).toAbsolutePath().normalize();
            for (Path parent : collectParents(current)) {
                for (String marker : ROOT_MARKERS) {
                    if (Files.exists(parent.resolve(marker))) {
                        return Optional.of(parent);
                    }
                }
            }
        } catch (InvalidPathException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public static String loadOpenjiuwenMd(String cwd) {
        return loadOpenjiuwenMd(cwd, Paths.get(System.getProperty("user.home")));
    }

    static String loadOpenjiuwenMd(String cwd, Path homeDir) {
        List<String> parts = new ArrayList<>();

        Path userFile = homeDir.resolve(".openjiuwen").resolve("OPENJIUWEN.md");
        if (Files.exists(userFile)) {
            readFile(userFile).ifPresent(content -> parts.add("### User-level memory\n" + content));
        }

        Optional<Path> projectRoot = findProjectRoot(cwd);
        if (projectRoot.isPresent()) {
            Path projectFile = projectRoot.get().resolve("OPENJIUWEN.md");
            readFile(projectFile).ifPresent(content -> parts.add("### Project-level memory\n" + content));
        }

        if (parts.isEmpty()) {
            return null;
        }

        String combined = String.join("\n\n", parts);
        if (combined.length() > MAX_MEMORY_CHARS) {
            combined = combined.substring(0, MAX_MEMORY_CHARS) + "\n[...truncated]";
        }
        return combined;
    }

    public static String buildEnvironmentSection(String cwd, String model, String provider) {
        String gitBranch = getGitBranch(cwd).orElse("N/A");
        return "## Environment\n"
                + "- CWD: " + cwd + "\n"
                + "- Platform: " + System.getProperty("os.name") + " " + System.getProperty("os.arch") + "\n"
                + "- Java: " + System.getProperty("java.version") + "\n"
                + "- Model: " + model + " (" + provider + ")\n"
                + "- Git branch: " + gitBranch + "\n"
                + "- Date: " + LocalDate.now(ZoneOffset.UTC) + "\n";
    }

    public static String buildSystemPrompt(String cwd, String model, String provider) {
        return buildSystemPrompt(cwd, model, provider, "en");
    }

    public static String buildSystemPrompt(String cwd, String model, String provider, String language) {
        String resolvedLanguage = HarnessPromptsPackage.resolveLanguage(language);
        SystemPromptBuilder builder = new SystemPromptBuilder(resolvedLanguage, PromptMode.FULL);
        builder.addSection(new PromptSection(
                "environment",
                singleLanguageSection(resolvedLanguage, buildEnvironmentSection(cwd, model, provider)),
                20
        ));

        String memory = loadOpenjiuwenMd(cwd);
        if (memory != null) {
            builder.addSection(new PromptSection(
                    "project_memory",
                    singleLanguageSection(resolvedLanguage, "## Project Memory\n" + memory),
                    120
            ));
        }

        return builder.build();
    }

    private static List<Path> collectParents(Path current) {
        List<Path> parents = new ArrayList<>();
        Path cursor = current;
        while (cursor != null) {
            parents.add(cursor);
            cursor = cursor.getParent();
        }
        return parents;
    }

    private static Optional<String> readFile(Path path) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(path));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> getGitBranch(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return Optional.empty();
        }
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                    .directory(Paths.get(cwd).toFile())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                process.destroyForcibly();
                return Optional.empty();
            }
            try (InputStream inputStream = process.getInputStream()) {
                String branch = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
                return branch.isEmpty() ? Optional.empty() : Optional.of(branch);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | InvalidPathException ignored) {
            return Optional.empty();
        }
    }

    private static Map<String, String> singleLanguageSection(String language, String value) {
        Map<String, String> content = new LinkedHashMap<>();
        content.put(language, value);
        return content;
    }
}
