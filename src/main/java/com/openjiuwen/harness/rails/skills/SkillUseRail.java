/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.prompts.sections.SkillsSection;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.tools.skills.ListSkillTool;
import com.openjiuwen.harness.tools.skills.SkillDescriptor;
import com.openjiuwen.harness.tools.skills.SkillTool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rail that manages skill prompt injection and skill tools.
 *
 * <p>Mirrors Python's {@code SkillUseRail} in
 * {@code openjiuwen/harness/rails/skills/skill_use_rail.py}.</p>
 */
public class SkillUseRail extends DeepAgentRail {

    public static final String SKILL_MODE_ALL = "all";
    public static final String SKILL_MODE_AUTO_LIST = "auto_list";
    private static final Set<String> VALID_SKILL_MODES = Set.of(SKILL_MODE_ALL, SKILL_MODE_AUTO_LIST);

    private final List<Path> skillDirs;
    private final String skillMode;
    private final boolean enableCache;
    private final boolean includeTools;
    private final Set<String> enabledSkills;
    private final Set<String> disabledSkills;
    private final List<SkillDescriptor> skills = new ArrayList<>();
    private final Map<Path, SkillDescriptor> skillCache = new LinkedHashMap<>();
    private final Map<Path, Long> skillUpdatedAt = new LinkedHashMap<>();
    private final Set<String> ownedToolNames = new LinkedHashSet<>();
    private final Set<String> ownedToolIds = new LinkedHashSet<>();
    private final Map<String, String> evolutionTexts = new LinkedHashMap<>();
    private String language = "cn";

    public SkillUseRail(String skillsDir) {
        this(skillsDir, SKILL_MODE_AUTO_LIST, true, true, null, null);
    }

    public SkillUseRail(
            String skillsDir,
            String skillMode,
            boolean enableCache,
            boolean includeTools,
            Iterable<String> enabledSkills,
            Iterable<String> disabledSkills
    ) {
        setPriority(100);
        if (!VALID_SKILL_MODES.contains(skillMode)) {
            throw new IllegalArgumentException("Unsupported skill_mode: " + skillMode);
        }
        this.skillDirs = normalizeSkillDirs(skillsDir);
        this.skillMode = skillMode;
        this.enableCache = enableCache;
        this.includeTools = includeTools;
        this.enabledSkills = normalizeNameSet(enabledSkills);
        this.disabledSkills = normalizeNameSet(disabledSkills);
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        if (agent != null && agent.deepConfig() != null) {
            language = agent.deepConfig().getLanguage();
        }
        if (agent != null) {
            SkillTool skillTool = new SkillTool(this::getSkillsMeta);
            agent.registerTool(skillTool);
            ownedToolNames.add(skillTool.getCard().getName());
            ownedToolIds.add(skillTool.getCard().getId());
            if (SKILL_MODE_AUTO_LIST.equals(skillMode)) {
                ListSkillTool listSkillTool = new ListSkillTool(this::getSkillsMeta);
                agent.registerTool(listSkillTool);
                ownedToolNames.add(listSkillTool.getCard().getName());
                ownedToolIds.add(listSkillTool.getCard().getId());
            }
        }
    }

    @Override
    public void uninit(DeepAgent agent) {
        if (agent != null) {
            for (String name : ownedToolNames) {
                agent.unregisterTool(name);
            }
        }
        ownedToolNames.clear();
        ownedToolIds.clear();
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        reloadSkills();
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        reloadSkills();
        String resolvedLanguage = String.valueOf(ctx.getValues().getOrDefault("language", language));
        PromptSection section = buildSkillsSection(resolvedLanguage);
        if (section == null) {
            ctx.put(SectionName.SKILLS, null);
            return;
        }
        ctx.put("skills_section", section);
        ctx.put("skills", dumpSkills());
    }

    public List<SkillDescriptor> getSkillsMeta() {
        return new ArrayList<>(skills);
    }

    public void reloadSkills() {
        if (!enableCache) {
            skillCache.clear();
            skillUpdatedAt.clear();
        }
        refreshSkillsIncrementally();
        skills.clear();
        skills.addAll(filterSkills(collectSkillsInOrder()));
    }

    public void clearSkills() {
        skillCache.clear();
        skillUpdatedAt.clear();
        skills.clear();
    }

    public Set<String> getOwnedToolNames() {
        return new LinkedHashSet<>(ownedToolNames);
    }

    public Set<String> getOwnedToolIds() {
        return new LinkedHashSet<>(ownedToolIds);
    }

    public String getSkillMode() {
        return skillMode;
    }

    public boolean isIncludeTools() {
        return includeTools;
    }

    public Map<String, String> getEvolutionTexts() {
        return new LinkedHashMap<>(evolutionTexts);
    }

    private PromptSection buildSkillsSection(String resolvedLanguage) {
        if (SKILL_MODE_ALL.equals(skillMode)) {
            List<String> lines = new ArrayList<>();
            int index = 0;
            for (SkillDescriptor skill : skills) {
                lines.add(SkillsSection.buildSkillLine(
                        index++,
                        skill.name(),
                        getSkillDescription(skill),
                        Path.of(skill.directory(), "SKILL.md").toString()
                ));
            }
            return SkillsSection.buildSkillsSection(
                    SkillsSection.buildSkillLines(lines),
                    resolvedLanguage,
                    SKILL_MODE_ALL
            );
        }
        return SkillsSection.buildSkillsSection("", resolvedLanguage, SKILL_MODE_AUTO_LIST);
    }

    private String getSkillDescription(SkillDescriptor skill) {
        String base = skill.description() == null ? "" : skill.description();
        String extra = evolutionTexts.get(skill.name());
        if (extra == null || extra.isBlank()) {
            return base;
        }
        return base + "\n" + extra;
    }

    private void refreshSkillsIncrementally() {
        Set<Path> discovered = new LinkedHashSet<>();
        for (Path root : skillDirs) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.list(root)) {
                List<Path> children = stream
                        .filter(Files::isDirectory)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
                for (Path child : children) {
                    Path skillMd = child.resolve("SKILL.md");
                    if (!Files.isRegularFile(skillMd)) {
                        continue;
                    }
                    Path key = child.toAbsolutePath().normalize();
                    discovered.add(key);
                    long updatedAt = Files.getLastModifiedTime(skillMd).toMillis();
                    if (!skillCache.containsKey(key) || skillUpdatedAt.getOrDefault(key, -1L) != updatedAt) {
                        SkillDescriptor descriptor = loadSkill(child, skillMd);
                        skillCache.put(key, descriptor);
                        skillUpdatedAt.put(key, updatedAt);
                    }
                }
            } catch (IOException ignored) {
                // Python implementation logs and skips unreadable skill roots.
            }
        }
        skillCache.keySet().removeIf(path -> !discovered.contains(path));
        skillUpdatedAt.keySet().removeIf(path -> !discovered.contains(path));
    }

    private SkillDescriptor loadSkill(Path skillDir, Path skillMd) {
        String description;
        try {
            description = loadDescription(skillMd);
        } catch (IOException exception) {
            description = "Skill located in " + skillDir;
        }
        return new SkillDescriptor(
                skillDir.getFileName().toString(),
                description,
                skillDir.toAbsolutePath().normalize().toString(),
                Map.of()
        );
    }

    private static String loadDescription(Path skillMd) throws IOException {
        String text = Files.readString(skillMd, StandardCharsets.UTF_8);
        if (!text.startsWith("---")) {
            return "";
        }
        String[] parts = text.split("---", 3);
        if (parts.length < 3) {
            return "";
        }
        for (String line : parts[1].split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("description:")) {
                return trimmed.substring("description:".length()).trim().replaceAll("^['\"]|['\"]$", "");
            }
        }
        return "";
    }

    private List<SkillDescriptor> collectSkillsInOrder() {
        Map<String, SkillDescriptor> byName = new LinkedHashMap<>();
        for (SkillDescriptor skill : skillCache.values()) {
            byName.putIfAbsent(skill.name(), skill);
        }
        return new ArrayList<>(byName.values());
    }

    private List<SkillDescriptor> filterSkills(List<SkillDescriptor> candidates) {
        List<SkillDescriptor> result = new ArrayList<>();
        for (SkillDescriptor skill : candidates) {
            if (!enabledSkills.isEmpty() && !enabledSkills.contains(skill.name())) {
                continue;
            }
            if (disabledSkills.contains(skill.name())) {
                continue;
            }
            result.add(skill);
        }
        return result;
    }

    private List<Map<String, Object>> dumpSkills() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SkillDescriptor skill : skills) {
            result.add(skill.asMap());
        }
        return result;
    }

    private static List<Path> normalizeSkillDirs(String raw) {
        List<Path> result = new ArrayList<>();
        for (String item : parseNameList(raw)) {
            result.add(Path.of(item).toAbsolutePath().normalize());
        }
        return result;
    }

    private static Set<String> normalizeNameSet(Iterable<String> raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null) {
            return result;
        }
        for (String item : raw) {
            result.addAll(parseNameList(item));
        }
        return result;
    }

    private static List<String> parseNameList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String normalized = raw.replace(';', ',');
        List<String> result = new ArrayList<>();
        for (String part : normalized.split(",")) {
            String item = part.trim();
            if (!item.isBlank()) {
                result.add(item);
            }
        }
        return result;
    }
}
