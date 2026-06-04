/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * File-system backed evolution record store.
 *
 * <p>Mirrors Python's {@code EvolutionStore} in
 * {@code openjiuwen.agent_evolving.checkpointing.evolution_store}.</p>
 */
public class FileEvolutionStore implements EvolutionStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EVOLUTION_FILENAME = "evolutions.json";
    private static final int MAX_INJECT_DESC = 5;
    private static final int INDEX_TOP_N = 3;

    private final List<Path> baseDirs;
    private final ConcurrentMap<String, Object> skillLocks = new ConcurrentHashMap<>();

    public FileEvolutionStore(String skillsBaseDir) {
        this(parseBaseDirs(skillsBaseDir));
    }

    public FileEvolutionStore(Path skillsBaseDir) {
        this(List.of(skillsBaseDir));
    }

    public FileEvolutionStore(Collection<Path> baseDirs) {
        List<Path> normalized = normalizeBaseDirs(baseDirs);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("skills_base_dir is empty");
        }
        this.baseDirs = List.copyOf(normalized);
    }

    public List<Path> getBaseDirs() {
        return new ArrayList<>(baseDirs);
    }

    public Path getBaseDir() {
        return baseDirs.getFirst();
    }

    @Override
    public String readSkillContent(String skillName) {
        Path skillDir = resolveSkillDir(skillName);
        if (skillDir == null) {
            return "";
        }
        Path skillMd = findSkillMd(skillDir);
        return skillMd == null ? "" : readFileText(skillMd);
    }

    @Override
    public boolean writeSkillContent(String skillName, String content) {
        Path skillDir = resolveSkillDir(skillName);
        if (skillDir == null) {
            return false;
        }
        Path skillMd = findSkillMd(skillDir);
        if (skillMd == null) {
            skillMd = skillDir.resolve("SKILL.md");
        }
        writeFileText(skillMd, content != null ? content : "");
        return true;
    }

    @Override
    public EvolutionLog loadEvolutionLog(String skillName) {
        return loadFullEvolutionLog(skillName);
    }

    public EvolutionLog loadEvolutionLog(String skillName, EvolutionTarget target) {
        EvolutionLog log = loadFullEvolutionLog(skillName);
        if (target == null) {
            return log;
        }
        List<EvolutionRecord> filtered = log.getEntries().stream()
                .filter(record -> record.getChange() != null && record.getChange().getTarget() == target)
                .toList();
        return new EvolutionLog(log.getSkillId(), log.getVersion(), log.getUpdatedAt(), filtered);
    }

    public EvolutionLog loadFullEvolutionLog(String skillName) {
        Path skillDir = resolveSkillDir(skillName);
        if (skillDir == null) {
            return EvolutionLog.empty(skillName);
        }
        Path evoPath = skillDir.resolve(EVOLUTION_FILENAME);
        if (!Files.exists(evoPath)) {
            return EvolutionLog.empty(skillName);
        }
        String raw = readFileText(evoPath);
        if (raw == null || raw.isBlank()) {
            return EvolutionLog.empty(skillName);
        }
        try {
            if (raw.stripLeading().startsWith("[")) {
                List<Map<String, Object>> entries = MAPPER.readValue(raw, new TypeReference<>() {
                });
                EvolutionLog log = EvolutionLog.empty(skillName);
                log.setEntries(entries.stream().map(EvolutionRecord::fromDict).toList());
                return log;
            }
            Map<String, Object> data = MAPPER.readValue(raw, new TypeReference<>() {
            });
            return EvolutionLog.fromDict(data);
        } catch (IOException exception) {
            return EvolutionLog.empty(skillName);
        }
    }

    @Override
    public boolean saveEvolutionLog(String skillName, EvolutionLog log) {
        Path skillDir = resolveSkillDir(skillName, true);
        if (skillDir == null) {
            return false;
        }
        try {
            Files.createDirectories(skillDir);
            EvolutionLog effective = log != null ? log : EvolutionLog.empty(skillName);
            effective.setSkillId(skillName);
            effective.setUpdatedAt(Instant.now().toString());
            writeFileText(skillDir.resolve(EVOLUTION_FILENAME),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(effective.toDict()));
            return true;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public int deleteRecords(String skillName, List<String> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return 0;
        }
        synchronized (lockFor(skillName)) {
            EvolutionLog log = loadFullEvolutionLog(skillName);
            int before = log.getEntries().size();
            Set<String> ids = new LinkedHashSet<>(recordIds);
            log.setEntries(log.getEntries().stream()
                    .filter(record -> !ids.contains(record.getId()))
                    .toList());
            if (before != log.getEntries().size()) {
                saveEvolutionLog(skillName, log);
                renderEvolutionMarkdown(skillName);
            }
            return before - log.getEntries().size();
        }
    }

    @Override
    public List<EvolutionRecord> loadRecords(String skillName) {
        return new ArrayList<>(loadFullEvolutionLog(skillName).getEntries());
    }

    @Override
    public boolean saveRecord(String skillName, EvolutionRecord record) {
        appendRecord(skillName, record);
        return true;
    }

    @Override
    public boolean mergeRecords(String skillName, String primaryId, List<String> removeIds, String newContent) {
        synchronized (lockFor(skillName)) {
            EvolutionLog log = loadFullEvolutionLog(skillName);
            EvolutionRecord primary = null;
            for (EvolutionRecord record : log.getEntries()) {
                if (Objects.equals(record.getId(), primaryId)) {
                    primary = record;
                    break;
                }
            }
            if (primary == null) {
                return false;
            }
            if (primary.getChange() != null) {
                primary.getChange().setContent(newContent);
            }
            Set<String> removeSet = removeIds == null ? Set.of() : new LinkedHashSet<>(removeIds);
            log.setEntries(log.getEntries().stream()
                    .filter(record -> Objects.equals(record.getId(), primaryId) || !removeSet.contains(record.getId()))
                    .toList());
            saveEvolutionLog(skillName, log);
            renderEvolutionMarkdown(skillName);
            return true;
        }
    }

    @Override
    public boolean updateRecordContent(String skillName, String recordId, String newContent) {
        synchronized (lockFor(skillName)) {
            EvolutionLog log = loadFullEvolutionLog(skillName);
            boolean updated = false;
            for (EvolutionRecord record : log.getEntries()) {
                if (Objects.equals(record.getId(), recordId) && record.getChange() != null) {
                    record.getChange().setContent(newContent);
                    record.setTimestamp(Instant.now().toString());
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                return false;
            }
            saveEvolutionLog(skillName, log);
            renderEvolutionMarkdown(skillName);
            return true;
        }
    }

    public String appendRecord(String skillName, EvolutionRecord record) {
        if (record == null) {
            return null;
        }
        synchronized (lockFor(skillName)) {
            Path skillDir = resolveSkillDir(skillName, true);
            if (skillDir == null) {
                return null;
            }
            if (record.getChange() != null && record.getChange().getTarget() == EvolutionTarget.SCRIPT) {
                persistScript(skillDir, record);
            }
            EvolutionLog log = loadFullEvolutionLog(skillName);
            String mergeTarget = record.getChange() != null ? record.getChange().getMergeTarget() : null;
            boolean replaced = false;
            if (mergeTarget != null && !mergeTarget.isBlank()) {
                for (int i = 0; i < log.getEntries().size(); i++) {
                    if (mergeTarget.equals(log.getEntries().get(i).getId())) {
                        log.getEntries().set(i, record);
                        replaced = true;
                        break;
                    }
                }
            }
            if (!replaced) {
                log.getEntries().add(record);
            }
            saveEvolutionLog(skillName, log);
            renderEvolutionMarkdown(skillName);
            return record.getId();
        }
    }

    public List<EvolutionRecord> getPendingRecords(String skillName, EvolutionTarget target) {
        return loadEvolutionLog(skillName, target).getPendingEntries();
    }

    public String formatDescExperienceText(String skillName) {
        return formatDescExperienceText(skillName, MAX_INJECT_DESC);
    }

    public String formatDescExperienceText(String skillName, int maxItems) {
        List<EvolutionRecord> pending = new ArrayList<>(getPendingRecords(skillName, EvolutionTarget.DESCRIPTION));
        pending.sort(Comparator.comparingDouble(EvolutionRecord::getScore).reversed());
        List<String> lines = pending.stream()
                .limit(maxItems)
                .map(record -> "- " + safeContent(record))
                .toList();
        return String.join("\n", lines);
    }

    public Map<String, String> formatAllDescExperiences(List<String> skillNames) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String skillName : skillNames != null ? skillNames : List.<String>of()) {
            String text = formatDescExperienceText(skillName);
            if (!text.isBlank()) {
                result.put(skillName, text);
            }
        }
        return result;
    }

    public String formatBodyExperienceText(String skillName) {
        List<EvolutionRecord> pending = getPendingRecords(skillName, EvolutionTarget.BODY);
        if (pending.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add("\n\n# Skill '" + skillName + "' body 演进经验\n");
        for (int i = 0; i < pending.size(); i++) {
            EvolutionRecord record = pending.get(i);
            String section = record.getChange() != null ? record.getChange().getSection() : "";
            lines.add((i + 1) + ". **[" + section + "]** " + safeContent(record));
        }
        return String.join("\n", lines);
    }

    public String listPendingSummary(List<String> skillNames) {
        List<String> lines = new ArrayList<>();
        int count = 0;
        for (String skillName : skillNames != null ? skillNames : List.<String>of()) {
            List<EvolutionRecord> desc = getPendingRecords(skillName, EvolutionTarget.DESCRIPTION);
            List<EvolutionRecord> body = getPendingRecords(skillName, EvolutionTarget.BODY);
            List<EvolutionRecord> all = new ArrayList<>();
            all.addAll(desc);
            all.addAll(body);
            if (all.isEmpty()) {
                continue;
            }
            count++;
            lines.add(count + ". **" + skillName + "** - 共 " + all.size()
                    + " 条 pending 经验（description: " + desc.size() + ", body: " + body.size() + "）");
            for (EvolutionRecord record : all) {
                String target = record.getChange() != null && record.getChange().getTarget() == EvolutionTarget.DESCRIPTION
                        ? "description" : "body";
                String content = safeContent(record);
                String title = content.contains("\n") ? content.substring(0, content.indexOf('\n')) : abbreviate(content, 50);
                lines.add("   - [" + target + "] **" + title + "**: ");
                if (content.contains("\n")) {
                    String summary = content.substring(content.indexOf('\n') + 1).lines()
                            .map(String::strip)
                            .filter(line -> !line.isEmpty())
                            .map(line -> line.replaceFirst("^-\\s*", ""))
                            .collect(Collectors.joining(" "));
                    if (!summary.isEmpty()) {
                        lines.add("    " + abbreviate(summary.replace("**", ""), 100));
                    }
                }
            }
            lines.add("");
        }
        return lines.isEmpty() ? "当前所有 Skill 暂无演进信息。" : String.join("\n", lines);
    }

    public List<String> listSkillNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Path baseDir : baseDirs) {
            if (!Files.isDirectory(baseDir)) {
                continue;
            }
            try (var stream = Files.list(baseDir)) {
                stream.filter(Files::isDirectory)
                        .filter(path -> !path.getFileName().toString().startsWith("_"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .map(path -> path.getFileName().toString())
                        .forEach(names::add);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        return new ArrayList<>(names);
    }

    public boolean skillExists(String skillName) {
        return resolveSkillDir(skillName) != null;
    }

    public Path resolveSkillDir(String skillName) {
        return resolveSkillDir(skillName, false);
    }

    public Path resolveSkillDir(String skillName, boolean create) {
        if (skillName == null || skillName.isBlank()) {
            return null;
        }
        for (Path baseDir : baseDirs) {
            Path candidate = baseDir.resolve(skillName);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return create ? baseDirs.getFirst().resolve(skillName) : null;
    }

    public String readFileText(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        } catch (IOException exception) {
            return "";
        }
    }

    public void writeFileText(Path path, String content) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content != null ? content : "", StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public void renderEvolutionMarkdown(String skillName) {
        Path skillDir = resolveSkillDir(skillName);
        if (skillDir == null) {
            return;
        }
        EvolutionLog log = loadFullEvolutionLog(skillName);
        List<EvolutionRecord> active = log.getEntries().stream()
                .filter(record -> record.getChange() == null
                        || record.getChange().getSkipReason() == null
                        || record.getChange().getSkipReason().isBlank())
                .toList();
        if (active.isEmpty()) {
            return;
        }

        Path evolutionDir = skillDir.resolve("evolution");
        Map<String, List<EvolutionRecord>> sections = new LinkedHashMap<>();
        List<EvolutionRecord> scripts = new ArrayList<>();
        for (EvolutionRecord record : active) {
            if (record.getChange() == null) {
                continue;
            }
            if (record.getChange().getTarget() == EvolutionTarget.SCRIPT) {
                scripts.add(record);
            } else {
                sections.computeIfAbsent(record.getChange().getSection(), ignored -> new ArrayList<>()).add(record);
            }
        }
        sections.forEach((section, records) -> renderSectionFile(evolutionDir, section, records));
        if (!scripts.isEmpty()) {
            renderScriptIndex(evolutionDir.resolve("scripts"), scripts);
        }
        updateSkillMdIndex(skillDir, active);
    }

    private void persistScript(Path skillDir, EvolutionRecord record) {
        Path scriptsDir = skillDir.resolve("evolution").resolve("scripts");
        String language = record.getChange().getScriptLanguage() != null ? record.getChange().getScriptLanguage() : "py";
        String ext = switch (language.toLowerCase(Locale.ROOT)) {
            case "python" -> "py";
            case "javascript" -> "js";
            case "typescript" -> "ts";
            case "shell", "bash" -> "sh";
            default -> language;
        };
        String filename = record.getChange().getScriptFilename() != null
                ? record.getChange().getScriptFilename()
                : record.getId() + "_script." + ext;
        writeFileText(scriptsDir.resolve(filename), record.getChange().getContent());
        record.getChange().setScriptFilename(filename);
        record.getChange().setContent("Script: " + filename + "\n"
                + "Language: " + (record.getChange().getScriptLanguage() != null
                ? record.getChange().getScriptLanguage() : "unknown") + "\n"
                + "Purpose: " + (record.getChange().getScriptPurpose() != null
                ? record.getChange().getScriptPurpose() : ""));
    }

    private void renderSectionFile(Path evolutionDir, String section, List<EvolutionRecord> records) {
        String safeSection = section != null ? section : "";
        List<String> lines = new ArrayList<>();
        lines.add("# " + safeSection);
        lines.add("");
        lines.add("> Auto-generated from evolutions.json. Do not edit directly.");
        lines.add("");
        for (EvolutionRecord record : records) {
            String content = safeContent(record);
            String[] parts = content.split("\\R", 2);
            lines.add("### [" + record.getId() + "] " + parts[0]);
            if (parts.length > 1 && !parts[1].isBlank()) {
                lines.add(parts[1].stripTrailing());
            }
            String appliedTag = record.isApplied() ? " | applied" : "";
            lines.add("");
            lines.add("*Source: " + record.getSource() + " | " + record.getTimestamp() + appliedTag + "*");
            lines.add("");
            lines.add("---");
            lines.add("");
        }
        writeFileText(evolutionDir.resolve(slug(safeSection) + ".md"), String.join("\n", lines));
    }

    private void renderScriptIndex(Path scriptsDir, List<EvolutionRecord> entries) {
        List<String> lines = new ArrayList<>();
        lines.add("# Script Index");
        lines.add("");
        lines.add("> Auto-generated from evolutions.json. Do not edit directly.");
        lines.add("");
        lines.add("| File | Language | Purpose | Source |");
        lines.add("|------|----------|---------|--------|");
        for (EvolutionRecord record : entries) {
            String filename = record.getChange().getScriptFilename() != null
                    ? record.getChange().getScriptFilename() : record.getId();
            String language = record.getChange().getScriptLanguage() != null
                    ? record.getChange().getScriptLanguage() : "unknown";
            String purpose = record.getChange().getScriptPurpose() != null
                    ? record.getChange().getScriptPurpose() : "";
            String timestamp = record.getTimestamp() != null && record.getTimestamp().length() >= 10
                    ? record.getTimestamp().substring(0, 10) : String.valueOf(record.getTimestamp());
            lines.add("| [" + filename + "](" + filename + ") | " + language + " | " + purpose + " | " + timestamp + " |");
        }
        lines.add("");
        writeFileText(scriptsDir.resolve("_index.md"), String.join("\n", lines));
    }

    private void updateSkillMdIndex(Path skillDir, List<EvolutionRecord> entries) {
        Path skillMd = findSkillMd(skillDir);
        if (skillMd == null) {
            return;
        }
        int bodyCount = 0;
        int descCount = 0;
        int scriptCount = 0;
        Map<String, Integer> sectionCounts = new LinkedHashMap<>();
        for (EvolutionRecord record : entries) {
            if (record.getChange() == null) {
                continue;
            }
            if (record.getChange().getTarget() == EvolutionTarget.BODY) {
                bodyCount++;
            } else if (record.getChange().getTarget() == EvolutionTarget.DESCRIPTION) {
                descCount++;
            } else if (record.getChange().getTarget() == EvolutionTarget.SCRIPT) {
                scriptCount++;
            }
            if (record.getChange().getTarget() != EvolutionTarget.SCRIPT) {
                String section = record.getChange().getSection();
                sectionCounts.put(section, sectionCounts.getOrDefault(section, 0) + 1);
            }
        }

        List<String> parts = new ArrayList<>();
        if (bodyCount > 0) {
            parts.add(bodyCount + " body");
        }
        if (descCount > 0) {
            parts.add(descCount + " description");
        }
        if (scriptCount > 0) {
            parts.add(scriptCount + " script");
        }

        List<String> lines = new ArrayList<>();
        lines.add("<!-- evolution-index-start -->");
        lines.add("## Evolution Experiences");
        lines.add("");
        lines.add("This skill has accumulated **" + entries.size() + "** evolution experiences ("
                + String.join(", ", parts) + ").");
        lines.add("");
        List<EvolutionRecord> top = entries.stream()
                .filter(record -> record.getScore() >= 0.5)
                .sorted(Comparator.comparingDouble(EvolutionRecord::getScore).reversed())
                .limit(INDEX_TOP_N)
                .toList();
        if (!top.isEmpty()) {
            lines.add("### Top Experiences");
            lines.add("");
            for (EvolutionRecord record : top) {
                lines.add("- [" + record.getId() + "] (score=" + String.format(Locale.ROOT, "%.2f", record.getScore())
                        + ") " + abbreviate(safeContent(record).split("\\R", 2)[0], 80));
            }
            lines.add("");
        }
        lines.add("| Type | Count | Details |");
        lines.add("|------|-------|---------|");
        sectionCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsFirst(String::compareTo)))
                .forEach(entry -> lines.add("| " + entry.getKey() + " | " + entry.getValue()
                        + " | [-> evolution/" + slug(entry.getKey()) + ".md](evolution/" + slug(entry.getKey()) + ".md) |"));
        if (scriptCount > 0) {
            lines.add("| Scripts | " + scriptCount
                    + " | [-> evolution/scripts/_index.md](evolution/scripts/_index.md) |");
        }
        lines.add("");
        lines.add("*Last updated: " + Instant.now() + "*");
        lines.add("<!-- evolution-index-end -->");

        String indexBlock = String.join("\n", lines);
        String content = readFileText(skillMd);
        String startToken = "<!-- evolution-index-start -->";
        String endToken = "<!-- evolution-index-end -->";
        int start = content.indexOf(startToken);
        int end = content.indexOf(endToken);
        String updated;
        if (start >= 0 && end > start) {
            int close = end + endToken.length();
            updated = content.substring(0, start) + indexBlock + content.substring(close);
        } else {
            updated = content.stripTrailing() + "\n\n" + indexBlock + "\n";
        }
        writeFileText(skillMd, updated);
    }

    private static Path findSkillMd(Path skillDir) {
        Path skillMd = skillDir.resolve("SKILL.md");
        if (Files.isRegularFile(skillMd)) {
            return skillMd;
        }
        try (var stream = Files.list(skillDir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    private Object lockFor(String skillName) {
        return skillLocks.computeIfAbsent(skillName != null ? skillName : "", ignored -> new Object());
    }

    private static List<Path> parseBaseDirs(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String normalized = raw.replace(',', ';');
        List<Path> paths = new ArrayList<>();
        for (String item : normalized.split(";")) {
            if (!item.isBlank()) {
                paths.add(Path.of(item.trim()));
            }
        }
        return paths;
    }

    private static List<Path> normalizeBaseDirs(Collection<Path> rawDirs) {
        Set<Path> normalized = new LinkedHashSet<>();
        for (Path path : rawDirs != null ? rawDirs : List.<Path>of()) {
            if (path != null) {
                normalized.add(path.toAbsolutePath().normalize());
            }
        }
        return new ArrayList<>(normalized);
    }

    private static String safeContent(EvolutionRecord record) {
        return record.getChange() != null && record.getChange().getContent() != null
                ? record.getChange().getContent() : "";
    }

    private static String slug(String section) {
        return (section == null || section.isBlank() ? "troubleshooting" : section)
                .toLowerCase(Locale.ROOT)
                .replace(" ", "_");
    }

    private static String abbreviate(String text, int max) {
        String safe = text != null ? text : "";
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
