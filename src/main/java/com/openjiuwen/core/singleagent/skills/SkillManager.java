/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.common.logging.Loggers;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages skill registration and retrieval.
 *
 * <p>Maintains a registry of skills and provides methods to register,
 * unregister, and query skills. Skills are loaded from YAML files containing
 * metadata such as name and description.</p>
 *
 * <p>Supports incremental refresh: only loads new or mtime-changed skills,
 * removes stale skills, and maintains directory traversal order.</p>
 */
public class SkillManager {

    private final Map<String, Skill> registry = new LinkedHashMap<>();
    private final Map<String, Long> updateAtCache = new LinkedHashMap<>();
    private final List<String> skillOrder = new ArrayList<>();
    private String sysOperationId;
    private String description = "";

    /**
     * Auto-generated for codecheck compliance.
     */
    public SkillManager(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSysOperationId(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSysOperationId() {
        return sysOperationId;
    }

    /**
     * Register skill(s) from path.
     *
     * @param skillPath path to the skill directory or file
     * @param sessionId session ID for file operations
     * @param overwrite whether to overwrite existing skills
     */
    public void register(String skillPath, String sessionId, boolean overwrite) {
        if (skillPath == null || skillPath.isEmpty()) {
            return;
        }

        try {
            registerRoot(Path.of(skillPath), sessionId, overwrite);
        } catch (Exception e) {
            Loggers.AGENT.warning("Failed to register skill from path: " + skillPath + " - " + e.getMessage());
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void register(String skillPath) {
        register(skillPath, null, false);
    }

    /**
     * Register skill from a {@link Path} (mirrors Python's Path type).
     *
     * @param skillPath path to the skill directory or file
     * @param sessionId session ID for file operations
     * @param overwrite whether to overwrite existing skills
     */
    public void register(Path skillPath, String sessionId, boolean overwrite) {
        if (skillPath == null) {
            return;
        }
        register(skillPath.toString(), sessionId, overwrite);
    }

    /**
     * Register skill from a {@link Path} with defaults.
     */
    public void register(Path skillPath) {
        register(skillPath, null, false);
    }

    /**
     * Register skills from a list of paths.
     *
     * @param skillPaths list of paths to skill directories or files
     * @param sessionId  session ID for file operations
     * @param overwrite  whether to overwrite existing skills
     */
    public void register(List<String> skillPaths, String sessionId, boolean overwrite) {
        if (skillPaths == null || skillPaths.isEmpty()) {
            return;
        }
        for (String path : skillPaths) {
            register(path, sessionId, overwrite);
        }
    }

    /**
     * Register skills from a list of {@link Path} objects.
     *
     * @param skillPaths list of Path objects pointing to skill directories or files
     * @param sessionId  session ID for file operations
     * @param overwrite  whether to overwrite existing skills
     */
    public void registerPaths(List<Path> skillPaths, String sessionId, boolean overwrite) {
        if (skillPaths == null || skillPaths.isEmpty()) {
            return;
        }
        for (Path p : skillPaths) {
            register(p, sessionId, overwrite);
        }
    }

    /**
     * Register skills from a list of {@link Path} objects with defaults.
     */
    public void registerPaths(List<Path> skillPaths) {
        registerPaths(skillPaths, null, false);
    }

    /**
     * Incrementally refresh skills from given root directories.
     *
     * <p>Only loads new or mtime-changed skills, removes stale skills
     * (directories that no longer exist), and maintains traversal order.</p>
     *
     * @param roots list of skill root directories to scan
     */
    public void refreshIncrementally(List<Path> roots) {
        long startTime = System.currentTimeMillis();
        Set<String> discoveredKeys = new LinkedHashSet<>();
        List<String> orderedKeys = new ArrayList<>();

        for (Path root : roots) {
            if (!root.toFile().isDirectory()) {
                continue;
            }
            File[] subdirs = root.toFile().listFiles(File::isDirectory);
            if (subdirs == null) {
                continue;
            }
            Arrays.sort(subdirs, Comparator.comparing(File::getName));

            for (File subdir : subdirs) {
                File skillMd = new File(subdir, "SKILL.md");
                if (!skillMd.exists()) {
                    skillMd = new File(subdir, "Skill.md");
                }
                if (!skillMd.exists()) {
                    continue;
                }

                String key = subdir.toPath().toAbsolutePath().normalize().toString();
                long mtime = skillMd.lastModified();

                discoveredKeys.add(key);
                orderedKeys.add(key);

                Long cachedMtime = updateAtCache.get(key);
                if (cachedMtime == null || cachedMtime != mtime) {
                    Skill skill = createSkillFromPath(skillMd.toPath());
                    if (skill != null) {
                        skill.setUpdateAt(mtime);
                        registry.put(skill.getName(), skill);
                        updateAtCache.put(key, mtime);
                    }
                }
            }
        }

        Set<String> staleKeys = new LinkedHashSet<>(updateAtCache.keySet());
        staleKeys.removeAll(discoveredKeys);
        for (String key : staleKeys) {
            Skill stale = findSkillByDirectory(key);
            if (stale != null) {
                registry.remove(stale.getName());
            }
            updateAtCache.remove(key);
        }

        skillOrder.clear();
        skillOrder.addAll(orderedKeys);

        long elapsed = System.currentTimeMillis() - startTime;
        Loggers.AGENT.debug("refreshIncrementally completed in {} ms, skills count: {}", elapsed, registry.size());
    }

    /**
     * Get all registered skills in directory traversal order, deduplicated by name.
     */
    public List<Skill> getAllInOrder() {
        List<Skill> result = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();
        for (String key : skillOrder) {
            Skill skill = findSkillByDirectory(key);
            if (skill != null && !seenNames.contains(skill.getName())) {
                seenNames.add(skill.getName());
                result.add(skill);
            }
        }
        for (Skill skill : registry.values()) {
            if (!seenNames.contains(skill.getName())) {
                seenNames.add(skill.getName());
                result.add(skill);
            }
        }
        return result;
    }

    /**
     * Build a snapshot signature of all visible skill directories and their SKILL.md mtimes.
     *
     * <p>Used for fast comparison to detect whether skills have changed
     * without actually reloading them.</p>
     *
     * @param roots list of skill root directories to scan
     * @return list of (directory-path, mtime) entries
     */
    public List<Map.Entry<String, Long>> buildSnapshotSignature(List<Path> roots) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>();
        for (Path root : roots) {
            if (!root.toFile().isDirectory()) {
                continue;
            }
            File[] subdirs = root.toFile().listFiles(File::isDirectory);
            if (subdirs == null) {
                continue;
            }
            Arrays.sort(subdirs, Comparator.comparing(File::getName));
            for (File subdir : subdirs) {
                File skillMd = new File(subdir, "SKILL.md");
                if (!skillMd.exists()) {
                    skillMd = new File(subdir, "Skill.md");
                }
                if (!skillMd.exists()) {
                    continue;
                }
                String key = subdir.toPath().toAbsolutePath().normalize().toString();
                entries.add(Map.entry(key, skillMd.lastModified()));
            }
        }
        return entries;
    }

    /**
     * Clear all registered skills, mtime cache, and traversal order.
     */
    public void clearAll() {
        registry.clear();
        updateAtCache.clear();
        skillOrder.clear();
    }

    /**
     * Find a registered skill by its directory path.
     */
    Skill findSkillByDirectory(String directoryPath) {
        return registry.values().stream()
                .filter(s -> s.getDirectory() != null && s.getDirectory().equals(directoryPath))
                .findFirst()
                .orElse(null);
    }

    /**
     * Register skill directory by scanning for Skill.md files.
     */
    private void registerRoot(Path root, String sessionId, boolean overwrite) {
        if (root.toFile().isDirectory()) {
            Path directSkillFile = root.resolve("SKILL.md");
            if (!directSkillFile.toFile().exists()) {
                directSkillFile = root.resolve("Skill.md");
            }
            if (directSkillFile.toFile().exists()) {
                Skill directSkill = createSkillFromPath(directSkillFile);
                if (directSkill != null) {
                    if (!overwrite && registry.containsKey(directSkill.getName())) {
                        throw new IllegalStateException("Skill already exists: " + directSkill.getName());
                    }
                    registry.put(directSkill.getName(), directSkill);
                    String key = root.toAbsolutePath().normalize().toString();
                    updateAtCache.put(key, directSkillFile.toFile().lastModified());
                    skillOrder.add(key);
                    return;
                }
            }
        }

        Skill skill = createSkillFromPath(root);
        if (skill != null) {
            if (!overwrite && registry.containsKey(skill.getName())) {
                throw new IllegalStateException("Skill already exists: " + skill.getName());
            }
            registry.put(skill.getName(), skill);
            return;
        }

        File dir = root.toFile();
        if (dir.isDirectory()) {
            File[] subdirs = dir.listFiles(File::isDirectory);
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    File skillMd = new File(subdir, "Skill.md");
                    if (!skillMd.exists()) {
                        skillMd = new File(subdir, "SKILL.md");
                    }
                    if (skillMd.exists()) {
                        Skill s = createSkillFromPath(skillMd.toPath());
                        if (s != null) {
                            if (!overwrite && registry.containsKey(s.getName())) {
                                throw new IllegalStateException("Skill already exists: " + s.getName());
                            }
                            registry.put(s.getName(), s);
                            String key = subdir.toPath().toAbsolutePath().normalize().toString();
                            updateAtCache.put(key, skillMd.lastModified());
                            skillOrder.add(key);
                        }
                    }
                }
            }
        }
    }

    /**
     * Create a Skill object from a Skill.md file path.
     */
    private Skill createSkillFromPath(Path path) {
        try {
            String descriptionText = loadDescription(path);
            if (descriptionText != null) {
                Path skillDir = path.getParent();
                return Skill.builder()
                        .name(skillDir.getFileName().toString())
                        .description(descriptionText)
                        .directory(skillDir.toString())
                        .build();
            }
        } catch (Exception e) {
            Loggers.AGENT.warning("Failed to create skill from path: " + path + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * Load description from YAML front matter in Skill.md file.
     *
     * <p>Supports inline scalar ({@code description: foo}), quoted inline scalar
     * ({@code description: "foo"}), and block scalars
     * ({@code description: |}, {@code description: |-}, {@code description: >},
     * {@code description: >-}).</p>
     */
    private String loadDescription(Path path) {
        try {
            String content = java.nio.file.Files.readString(path);
            // Strip UTF-8 BOM and leading/trailing whitespace
            content = content.trim();
            if (content.startsWith("\uFEFF")) {
                content = content.substring(1);
            }
            if (!content.startsWith("---")) {
                return null;
            }
            String[] parts = content.split("---", 3);
            if (parts.length < 2) {
                return null;
            }
            String[] lines = parts[1].split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String trimmed = lines[i].trim();
                if (!trimmed.startsWith("description:")) {
                    continue;
                }
                String value = trimmed.substring("description:".length()).trim();
                if (value.isEmpty()) {
                    // value continues on following indented lines (rare in practice)
                    return joinContinuation(lines, i + 1);
                }
                if (value.startsWith("|") || value.startsWith(">")) {
                    return parseBlockScalar(value, lines, i + 1);
                }
                return unquoteInline(value);
            }
        } catch (Exception e) {
            // File might not exist or not be readable
        }
        return null;
    }

    /**
     * Parse a YAML block scalar ({@code |}, {@code |-}, {@code >}, {@code >-}).
     *
     * @param indicator the block scalar indicator token at the description line
     * @param lines     all lines of the YAML front matter block
     * @param startIdx  index of the first line after the description line
     * @return the assembled block scalar content
     */
    private String parseBlockScalar(String indicator, String[] lines, int startIdx) {
        boolean fold = indicator.startsWith(">");
        boolean keepTrailingNewlines = !indicator.contains("-");
        // Gather indented continuation lines
        List<String> blockLines = new ArrayList<>();
        int minIndent = -1;
        for (int j = startIdx; j < lines.length; j++) {
            String raw = lines[j];
            // A blank line ends (or is part of) the block; trailing blank lines are
            // dropped later. A non-blank, non-indented line terminates the block.
            if (raw.trim().isEmpty()) {
                blockLines.add("");
                continue;
            }
            int indent = leadingSpaces(raw);
            if (indent == 0) {
                break;
            }
            if (minIndent < 0 || indent < minIndent) {
                minIndent = indent;
            }
            blockLines.add(raw.substring(minIndent));
        }
        // Strip trailing blank lines unless chomping keeps them
        while (!blockLines.isEmpty() && blockLines.get(blockLines.size() - 1).isEmpty()) {
            if (keepTrailingNewlines) {
                break;
            }
            blockLines.remove(blockLines.size() - 1);
        }
        if (fold) {
            return foldBlock(blockLines, keepTrailingNewlines);
        }
        StringBuilder out = new StringBuilder();
        for (int k = 0; k < blockLines.size(); k++) {
            if (k > 0) {
                out.append('\n');
            }
            out.append(blockLines.get(k));
        }
        if (keepTrailingNewlines && !blockLines.isEmpty()) {
            out.append('\n');
        }
        return out.toString().trim();
    }

    /**
     * Fold lines for the {@code >} indicator: blank lines become a single
     * newline, consecutive non-blank lines join with a space.
     */
    private String foldBlock(List<String> blockLines, boolean keepTrailingNewlines) {
        StringBuilder out = new StringBuilder();
        boolean prevBlank = false;
        for (int k = 0; k < blockLines.size(); k++) {
            String line = blockLines.get(k);
            if (line.isEmpty()) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                prevBlank = true;
                continue;
            }
            if (out.length() > 0 && !prevBlank) {
                out.append(' ');
            }
            out.append(line);
            prevBlank = false;
        }
        if (keepTrailingNewlines && out.length() > 0) {
            out.append('\n');
        }
        return out.toString().trim();
    }

    /**
     * Join continuation lines for the rare case where {@code description:}
     * is followed by indented content on subsequent lines without a block
     * indicator.
     */
    private String joinContinuation(String[] lines, int startIdx) {
        StringBuilder out = new StringBuilder();
        for (int j = startIdx; j < lines.length; j++) {
            String raw = lines[j];
            if (raw.trim().isEmpty()) {
                continue;
            }
            int indent = leadingSpaces(raw);
            if (indent == 0) {
                break;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(raw.trim());
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static int leadingSpaces(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    /**
     * Strip surrounding quotes from an inline scalar value.
     */
    private static String unquoteInline(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /**
     * Unregister a skill by name.
     */
    public void unregister(String name) {
        registry.remove(name);
    }

    /**
     * Get skill by name.
     */
    public Skill get(String name) {
        return registry.get(name);
    }

    /**
     * Get all registered skills.
     */
    public List<Skill> getAll() {
        return new ArrayList<>(registry.values());
    }

    /**
     * Get all registered skill names.
     */
    public List<String> getNames() {
        return new ArrayList<>(registry.keySet());
    }

    /**
     * Check if a skill is registered.
     */
    public boolean has(String name) {
        return registry.containsKey(name);
    }

    /**
     * Clear all registered skills.
     */
    public void clear() {
        registry.clear();
    }

    /**
     * Get the number of registered skills.
     */
    public int count() {
        return registry.size();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setDescription(String description) {
        this.description = description;
    }
}