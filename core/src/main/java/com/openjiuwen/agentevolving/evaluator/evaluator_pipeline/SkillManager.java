/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import lombok.Getter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Skill persistence manager for evaluator pipeline iterations.
 *
 * <p>Mirrors Python's {@code SkillManager} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/skill_manager.py}.</p>
 */
@Getter
public class SkillManager {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern HOW_TO_READ_PATTERN = Pattern.compile(
            "<!--\\s*evolution-howtoread-start\\s*-->.*?<!--\\s*evolution-howtoread-end\\s*-->",
            Pattern.DOTALL);
    private static final Pattern INDEX_PATTERN = Pattern.compile("<!--\\s*evolution-index-start\\s*-->");
    private static final Pattern FAILED_PATTERN = Pattern.compile(
            "FAILED\\s+(.+?)\\s*-\\s*(.*?)(?=\\nFAILED|\\n={3,}|\\nPASSED|\\z)",
            Pattern.DOTALL);
    private static final Pattern ASSERTION_PATTERN = Pattern.compile(
            "(test_\\w+.*?)\\n.*?(AssertionError|assert\\s+.*?)\\n(.*?)(?=\\n\\n|\\nFAILED|\\z)",
            Pattern.DOTALL);
    private static final Pattern ERROR_BLOCK_PATTERN = Pattern.compile(
            "_{3,}\\s*(.*?)\\s*_{3,}\\n(.*?)(?=_{3,}|\\z)",
            Pattern.DOTALL);

    private final PipelineConfig config;
    private final Path skillRoot;

    private Path skillDir;
    private String currentSkill;
    private String currentEvolutions;
    private final List<Path> skillHistory = new ArrayList<>();
    private String resolvedSkillName = "";
    private Map<String, String> allSkills = new LinkedHashMap<>();
    private Map<String, String> allEvolutions = new LinkedHashMap<>();
    private Map<String, Map<String, String>> allEvolutionFiles = new LinkedHashMap<>();

    public SkillManager(PipelineConfig config) {
        this.config = config;
        Map<String, Object> agentConfig = config.getAgentConfig();
        Object skillPersistenceDir = agentConfig.getOrDefault(
                "skill_persistence_dir",
                "~/.jiuwenswarm/agent/workspace/skills");
        this.skillRoot = expandUser(String.valueOf(skillPersistenceDir));
    }

    public void initForTask(String taskId) {
        this.skillDir = skillRoot.resolve(taskId);
        ensureDirectory(skillDir);
        this.resolvedSkillName = loadResolvedSkillName(taskId);
    }

    public Path resolvedNamePath() {
        requireSkillDir();
        return skillDir.resolve(".resolved_skill_name");
    }

    public String loadResolvedSkillName(String taskId) {
        Path namePath = resolvedNamePath();
        if (Files.exists(namePath)) {
            String savedName = readText(namePath).strip();
            if (!savedName.isEmpty()) {
                return savedName;
            }
        }
        return taskId;
    }

    public void saveResolvedSkillName() {
        writeText(resolvedNamePath(), resolvedSkillName);
    }

    public void setResolvedSkillName(String resolvedSkillName) {
        this.resolvedSkillName = resolvedSkillName == null ? "" : resolvedSkillName;
    }

    public Path getSkillDirPath(String skillName) {
        return getSkillDirPath(skillName, null);
    }

    public Path getSkillDirPath(String skillName, Integer iteration) {
        requireSkillDir();
        Path base = skillDir.resolve(skillName);
        if (iteration == null) {
            return base.resolve("latest");
        }
        return base.resolve(String.format(Locale.ROOT, "iteration_%03d", iteration));
    }

    public Map<String, String> loadAllSkills() {
        return loadAllSkills(true);
    }

    public Map<String, String> loadAllSkills(boolean verbose) {
        allSkills = new LinkedHashMap<>();
        allEvolutions = new LinkedHashMap<>();
        allEvolutionFiles = new LinkedHashMap<>();

        requireSkillDir();
        if (!Files.exists(skillDir)) {
            return allSkills;
        }

        try (Stream<Path> children = Files.list(skillDir)) {
            children.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(child -> {
                        Path skillMd = child.resolve("SKILL.md");
                        if (!Files.exists(skillMd)) {
                            return;
                        }
                        String skillName = child.getFileName().toString();
                        if ("latest".equals(skillName) || skillName.startsWith("iteration_")) {
                            return;
                        }
                        allSkills.put(skillName, readText(skillMd));

                        Path evoPath = child.resolve("evolutions.json");
                        if (Files.exists(evoPath)) {
                            allEvolutions.put(skillName, readText(evoPath));
                        }

                        Path evoDir = child.resolve("evolution");
                        if (Files.isDirectory(evoDir)) {
                            Map<String, String> files = new LinkedHashMap<>();
                            try (Stream<Path> mdFiles = Files.list(evoDir)) {
                                mdFiles.filter(path -> path.getFileName().toString().endsWith(".md"))
                                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                                        .forEach(mdFile -> {
                                            try {
                                                files.put(mdFile.getFileName().toString(), readText(mdFile));
                                            } catch (RuntimeException exception) {
                                                LOGGER.warning(
                                                        "Failed to read evolution file {}: {}",
                                                        mdFile,
                                                        exception.getMessage());
                                            }
                                        });
                            } catch (IOException exception) {
                                throw new IllegalStateException("Failed to read evolution dir " + evoDir, exception);
                            }
                            if (!files.isEmpty()) {
                                allEvolutionFiles.put(skillName, files);
                            }
                        }
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list skill directory " + skillDir, exception);
        }

        if (!allSkills.isEmpty()) {
            if (allSkills.containsKey(resolvedSkillName)) {
                currentSkill = allSkills.get(resolvedSkillName);
            } else {
                currentSkill = allSkills.values().iterator().next();
            }
            currentEvolutions = allEvolutions.get(resolvedSkillName);
        }

        if (verbose) {
            LOGGER.info("  Loaded {} skills: {}", allSkills.size(), new ArrayList<>(allSkills.keySet()));
        }
        return allSkills;
    }

    public List<Path> saveAllSkills(Map<String, String> skills, int iteration) {
        return saveAllSkills(skills, iteration, Map.of(), Map.of());
    }

    public List<Path> saveAllSkills(
            Map<String, String> skills,
            int iteration,
            Map<String, String> evolutions,
            Map<String, Map<String, String>> evolutionFiles) {
        requireSkillDir();
        List<Path> savedPaths = new ArrayList<>();
        Map<String, String> safeEvolutions = evolutions == null ? Map.of() : evolutions;
        Map<String, Map<String, String>> safeEvolutionFiles = evolutionFiles == null ? Map.of() : evolutionFiles;

        for (Map.Entry<String, String> entry : skills.entrySet()) {
            String skillName = entry.getKey();
            String skillContent = entry.getValue();
            Path skillSubDir = skillDir.resolve(skillName);
            ensureDirectory(skillSubDir);

            Path iterDir = skillSubDir.resolve(String.format(Locale.ROOT, "iteration_%03d", iteration));
            ensureDirectory(iterDir);

            Path skillPath = iterDir.resolve("SKILL.md");
            writeText(skillPath, skillContent);

            Path latestDir = skillSubDir.resolve("latest");
            ensureDirectory(latestDir);
            copy(skillPath, latestDir.resolve("SKILL.md"));

            Path rootSkillPath = skillSubDir.resolve("SKILL.md");
            copy(skillPath, rootSkillPath);

            String evoContent = safeEvolutions.get(skillName);
            if (evoContent != null) {
                String merged = mergeEvolutionsForSkill(skillName, evoContent);
                Path evoPath = iterDir.resolve("evolutions.json");
                writeText(evoPath, merged);
                copy(evoPath, latestDir.resolve("evolutions.json"));
                copy(evoPath, skillSubDir.resolve("evolutions.json"));
                allEvolutions.put(skillName, merged);
            }

            Map<String, String> skillEvolutionFiles = safeEvolutionFiles.getOrDefault(skillName, Map.of());
            if (!skillEvolutionFiles.isEmpty()) {
                Path evoDir = iterDir.resolve("evolution");
                Path latestEvoDir = latestDir.resolve("evolution");
                Path rootEvoDir = skillSubDir.resolve("evolution");
                ensureDirectory(evoDir);
                ensureDirectory(latestEvoDir);
                ensureDirectory(rootEvoDir);
                for (Map.Entry<String, String> fileEntry : skillEvolutionFiles.entrySet()) {
                    Path filePath = evoDir.resolve(fileEntry.getKey());
                    writeText(filePath, fileEntry.getValue());
                    copy(filePath, latestEvoDir.resolve(fileEntry.getKey()));
                    copy(filePath, rootEvoDir.resolve(fileEntry.getKey()));
                }
            }

            allSkills.put(skillName, skillContent);
            skillHistory.add(skillPath);
            savedPaths.add(skillPath);
            LOGGER.info("    Skill saved: {} -> {}", skillName, skillPath);
        }

        if (allSkills.containsKey(resolvedSkillName)) {
            currentSkill = allSkills.get(resolvedSkillName);
        } else if (!allSkills.isEmpty()) {
            currentSkill = allSkills.values().iterator().next();
        }
        currentEvolutions = allEvolutions.get(resolvedSkillName);

        return savedPaths;
    }

    public String mergeEvolutionsForSkill(String skillName, String newEvolutionContent) {
        requireSkillDir();
        Path existingEvolutionPath = skillDir.resolve(skillName).resolve("evolutions.json");
        Map<String, Map<String, Object>> existingEntries = new LinkedHashMap<>();

        if (Files.exists(existingEvolutionPath)) {
            try {
                Map<String, Object> existingData = OBJECT_MAPPER.readValue(
                        readText(existingEvolutionPath),
                        new TypeReference<>() {
                        });
                Object rawEntries = existingData.get("entries");
                if (rawEntries instanceof List<?> list) {
                    for (Object rawEntry : list) {
                        if (!(rawEntry instanceof Map<?, ?> map)) {
                            continue;
                        }
                        Map<String, Object> entry = toStringKeyMap(map);
                        String entryId = String.valueOf(entry.getOrDefault("id", ""));
                        if (!entryId.isEmpty()) {
                            existingEntries.put(entryId, entry);
                        }
                    }
                }
            } catch (Exception exception) {
                LOGGER.warning(
                        "Failed to parse existing evolutions.json for {}: {}",
                        skillName,
                        exception.getMessage());
            }
        }

        Map<String, Object> newData;
        try {
            newData = OBJECT_MAPPER.readValue(newEvolutionContent, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return newEvolutionContent;
        }

        Object rawEntries = newData.get("entries");
        if (rawEntries instanceof List<?> list) {
            for (Object rawEntry : list) {
                if (!(rawEntry instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> entry = toStringKeyMap(map);
                String entryId = String.valueOf(entry.getOrDefault("id", ""));
                if (!entryId.isEmpty()) {
                    existingEntries.put(entryId, entry);
                }
            }
        }

        Map<String, Object> mergedData = new LinkedHashMap<>();
        mergedData.put("entries", new ArrayList<>(existingEntries.values()));
        mergedData.put("skill_id", String.valueOf(newData.getOrDefault("skill_id", "")));
        mergedData.put("updated_at", String.valueOf(newData.getOrDefault("updated_at", "")));
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(mergedData);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write merged evolutions for " + skillName, exception);
        }
    }

    public void renderEvolutionToSkillMdFor(String skillName) {
        requireSkillDir();
        Path skillSubDir = skillDir.resolve(skillName);
        Path skillMdPath = skillSubDir.resolve("SKILL.md");

        if (!Files.exists(skillMdPath)) {
            return;
        }

        String howToReadBlock = String.join("\n",
                "<!-- evolution-howtoread-start -->",
                "### How to Read Evolution Details",
                "",
                "**IMPORTANT**: Before applying this skill, review the Experience Index below. "
                        + "If any experience summary matches your current task or a failure you encountered, "
                        + "you MUST read the linked detail section for specific guidance.",
                "",
                "1. Check the **Summary** column below for relevant experiences",
                "2. Click or read the **Detail** path to find the full guidance",
                "3. Read the evolution file using: `cat <skill-dir>/evolution/<filename>.md`",
                "4. Look for the specific experience ID anchor (e.g., `#ev_xxxxxxxx`)",
                "",
                "For narrative guidance, read the relevant `evolution/*.md` detail section. "
                        + "For reusable helper code, first review `evolution/scripts/_index.md`, "
                        + "then inspect the specific script source before adapting or running it.",
                "<!-- evolution-howtoread-end -->");

        String content = readText(skillMdPath);
        Matcher howToReadMatcher = HOW_TO_READ_PATTERN.matcher(content);
        if (howToReadMatcher.find()) {
            content = howToReadMatcher.replaceAll(Matcher.quoteReplacement(howToReadBlock));
        } else {
            Matcher indexMatcher = INDEX_PATTERN.matcher(content);
            if (indexMatcher.find()) {
                content = indexMatcher.replaceFirst(
                        Matcher.quoteReplacement(howToReadBlock + "\n\n<!-- evolution-index-start -->"));
            } else {
                content = content.stripTrailing() + "\n\n" + howToReadBlock + "\n";
            }
        }

        writeText(skillMdPath, content);
        Path latestPath = skillSubDir.resolve("latest").resolve("SKILL.md");
        if (Files.exists(latestPath.getParent())) {
            copy(skillMdPath, latestPath);
        }
        if (allSkills.containsKey(skillName)) {
            allSkills.put(skillName, content);
        }
        if (skillName.equals(resolvedSkillName)) {
            currentSkill = content;
        }
        LOGGER.info("    How-to-Read guidance injected into {}/SKILL.md", skillName);
    }

    public static String computeSkillHash(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.substring(0, 16);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash skill content", exception);
        }
    }

    public boolean hasSkillChanged(String newContent) {
        if (currentSkill == null || currentSkill.isEmpty()) {
            return true;
        }
        return !computeSkillHash(currentSkill).equals(computeSkillHash(newContent));
    }

    public List<String> getAllSkillNames() {
        return new ArrayList<>(allSkills.keySet());
    }

    public static Map<String, String> extractSpecificErrors(String testOutput) {
        Map<String, String> errors = new LinkedHashMap<>();
        Matcher failedMatcher = FAILED_PATTERN.matcher(testOutput);
        while (failedMatcher.find()) {
            String testName = failedMatcher.group(1).strip();
            String errorBody = failedMatcher.group(2).strip();
            List<String> filtered = new ArrayList<>();
            for (String line : errorBody.split("\\R")) {
                String stripped = line.strip();
                if (!stripped.isEmpty() && !startsWithAny(stripped, "---", "+++", "@@")) {
                    filtered.add(line);
                }
            }
            String core = String.join("\n", filtered.stream().limit(8).toList());
            errors.put(testName, truncate(core, 400));
        }

        if (!errors.isEmpty()) {
            return errors;
        }

        Matcher assertionMatcher = ASSERTION_PATTERN.matcher(testOutput);
        while (assertionMatcher.find()) {
            String[] lines = assertionMatcher.group(1).strip().split("\\R");
            String testName = lines[lines.length - 1].strip();
            String assertionLine = assertionMatcher.group(2).strip();
            String detail = assertionMatcher.group(3).strip();
            errors.put(testName, truncate(assertionLine + "\n" + detail, 400));
        }

        if (!errors.isEmpty()) {
            return errors;
        }

        Matcher errorBlockMatcher = ERROR_BLOCK_PATTERN.matcher(testOutput);
        while (errorBlockMatcher.find()) {
            String header = errorBlockMatcher.group(1).strip();
            String body = errorBlockMatcher.group(2).strip();
            if (!header.contains("FAILED") && !header.contains("ERROR")) {
                continue;
            }
            List<String> filtered = new ArrayList<>();
            for (String line : body.split("\\R")) {
                if (!line.strip().isEmpty()) {
                    filtered.add(line);
                }
            }
            String core = String.join("\n", filtered.stream().limit(6).toList());
            String[] parts = header.split("\\s+");
            String testName = parts.length == 0 ? "unknown" : parts[0];
            errors.put(testName, truncate(core, 400));
        }

        return errors;
    }

    private void requireSkillDir() {
        if (skillDir == null) {
            throw new RuntimeException("skill_dir not initialized, call init_for_task first");
        }
    }

    private static void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create directory " + path, exception);
        }
    }

    private static void writeText(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write " + path, exception);
        }
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }

    private static void copy(Path source, Path target) {
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to copy " + source + " -> " + target, exception);
        }
    }

    private static Path expandUser(String rawPath) {
        if ("~".equals(rawPath)) {
            return Path.of(System.getProperty("user.home"));
        }
        if (rawPath.startsWith("~/") || rawPath.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home") + rawPath.substring(1));
        }
        return Path.of(rawPath);
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
