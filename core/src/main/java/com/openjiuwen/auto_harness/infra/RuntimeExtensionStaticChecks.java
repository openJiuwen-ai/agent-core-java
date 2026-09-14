/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Shared static-analysis utilities for runtime extensions.
 * <p>
 * Mirrors Python's module functions in
 * {@code openjiuwen/auto_harness/infra/runtime_extension_static_checks.py}.
 */
public final class RuntimeExtensionStaticChecks {

    private static final Logger LOGGER = Logger.getLogger(RuntimeExtensionStaticChecks.class.getName());

    private RuntimeExtensionStaticChecks() {
    }

    public static List<String> validateSkillFrontmatter(Path skillMdPath) {
        List<String> errors = new ArrayList<>();
        if (!Files.isRegularFile(skillMdPath)) {
            errors.add("SKILL.md not found: " + skillMdPath);
            return errors;
        }

        String text;
        try {
            text = Files.readString(skillMdPath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            errors.add("Cannot read SKILL.md: " + skillMdPath + ": " + e.getMessage());
            return errors;
        }

        if (!text.startsWith("---")) {
            errors.add("SKILL.md missing frontmatter: " + skillMdPath);
            return errors;
        }

        String[] parts = text.split("---", 3);
        if (parts.length < 3) {
            errors.add("SKILL.md malformed frontmatter: " + skillMdPath);
            return errors;
        }

        Object data;
        try {
            data = new Yaml().load(parts[1]);
        } catch (YAMLException e) {
            errors.add("SKILL.md frontmatter YAML error: " + skillMdPath + ": " + e.getMessage());
            return errors;
        }
        if (data == null) {
            data = Map.of();
        }
        if (!(data instanceof Map<?, ?> map)) {
            errors.add("SKILL.md frontmatter not a dict: " + skillMdPath);
            return errors;
        }

        Object name = map.get("name");
        if (!(name instanceof String textName) || textName.strip().isEmpty()) {
            errors.add("SKILL.md missing 'name' field: " + skillMdPath);
        }

        Object description = map.get("description");
        if (!(description instanceof String textDescription) || textDescription.strip().isEmpty()) {
            errors.add("SKILL.md missing 'description' field: " + skillMdPath);
        }
        return errors;
    }

    public static List<String> checkRuff(Path extensionRoot) {
        List<String> errors = new ArrayList<>();
        Map<String, String> env = buildRuffEnv();
        String root = extensionRoot.toString();
        String python = pythonExecutable();

        for (List<String> fixCommand : List.of(
                List.of(python, "-m", "ruff", "format", root),
                List.of(python, "-m", "ruff", "check", "--fix", root)
        )) {
            try {
                runProcess(fixCommand, env);
            } catch (IOException e) {
                LOGGER.fine("ruff not available, skipping auto-fix");
                return errors;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                errors.add("ruff check interrupted");
                return errors;
            }
        }

        try {
            CommandResult result = runProcess(List.of(python, "-m", "ruff", "check", root), env);
            if (result.returnCode() != 0) {
                String output = result.stdout().strip();
                if (output.isEmpty()) {
                    output = result.stderr().strip();
                }
                errors.add("ruff check failed: " + preview(output, 500));
            }
        } catch (IOException e) {
            LOGGER.fine("ruff not available, skipping lint");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add("ruff check interrupted");
        }
        return errors;
    }

    public static ExtStaticCheckResult runStaticChecksAgainstRuntime(
            RuntimeExtensionArtifact runtimeExt,
            String sessionIdPrefix
    ) throws IOException {
        if (runtimeExt == null) {
            throw new IllegalArgumentException("runtimeExt must not be null");
        }
        ExtStaticCheckResult result = new ExtStaticCheckResult();
        Path configPath = Path.of(nullToEmpty(runtimeExt.getConfigPath()));
        if (!Files.isRegularFile(configPath)) {
            throw new NoSuchFileException("Missing extension manifest: " + configPath);
        }

        try {
            List<Class<?>> rails = RuntimeExtensionLoader.loadRuntimeRails(runtimeExt, sessionIdPrefix);
            for (Class<?> railClass : rails) {
                instantiate(railClass);
            }
            result.setRailsCount(rails.size());
        } catch (Exception e) {
            result.getErrors().add("Rails load failed: " + messageOf(e));
        }

        try {
            List<Class<?>> tools = RuntimeExtensionLoader.loadRuntimeTools(runtimeExt, sessionIdPrefix);
            for (Class<?> toolClass : tools) {
                instantiate(toolClass);
            }
            result.setToolsCount(tools.size());
        } catch (Exception e) {
            result.getErrors().add("Tools load failed: " + messageOf(e));
        }

        List<String> skillDirs;
        try {
            skillDirs = RuntimeExtensionLoader.loadRuntimeSkillDirs(runtimeExt);
            result.setSkillDirsCount(skillDirs.size());
        } catch (Exception e) {
            result.getErrors().add("Skill dirs load failed: " + messageOf(e));
            skillDirs = List.of();
        }

        for (String skillDir : skillDirs) {
            Path skillDirPath = Path.of(skillDir);
            try (Stream<Path> walk = Files.walk(skillDirPath)) {
                List<Path> skillMds = walk
                        .filter(path -> path.getFileName() != null
                                && "SKILL.md".equals(path.getFileName().toString()))
                        .toList();
                result.setSkillsCount(result.getSkillsCount() + skillMds.size());
                if (skillMds.isEmpty()) {
                    result.getErrors().add("Skill dir has no SKILL.md: " + skillDir);
                } else {
                    for (Path skillMd : skillMds) {
                        result.getErrors().addAll(validateSkillFrontmatter(skillMd));
                    }
                }
            } catch (Exception e) {
                result.getErrors().add("Skill validation failed for " + skillDir + ": " + messageOf(e));
            }
        }

        Path extensionRoot = Path.of(nullToEmpty(runtimeExt.getRuntimePath()));
        if (Files.isDirectory(extensionRoot)) {
            result.getErrors().addAll(checkRuff(extensionRoot));
        }
        return result;
    }

    private static void instantiate(Class<?> type) throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    private static Map<String, String> buildRuffEnv() {
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        env.put("CI", "1");
        String venv = env.get("VIRTUAL_ENV");
        if (venv == null || venv.isBlank()) {
            return env;
        }
        Path venvPath = Path.of(venv);
        Path binDir = isWindows() ? venvPath.resolve("Scripts") : venvPath.resolve("bin");
        String existingPath = env.getOrDefault("PATH", env.getOrDefault("Path", ""));
        String pathSeparator = System.getProperty("path.separator");
        String updatedPath = existingPath.isBlank()
                ? binDir.toString()
                : binDir + pathSeparator + existingPath;
        env.put("PATH", updatedPath);
        return env;
    }

    private static CommandResult runProcess(List<String> command, Map<String, String> env)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().putAll(env);
        Process process = builder.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int returnCode = process.waitFor();
        return new CommandResult(returnCode, stdout, stderr);
    }

    private static String pythonExecutable() {
        String configured = System.getenv("AUTO_HARNESS_PYTHON");
        return configured == null || configured.isBlank() ? "python" : configured;
    }

    private static String messageOf(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String preview(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    private record CommandResult(int returnCode, String stdout, String stderr) {
    }
}
