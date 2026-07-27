/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.checkpointing;

import com.openjiuwen.agentevolving.signal.EvolutionTarget;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Private projection/rendering helpers for {@code EvolutionStore}.
 *
 * <p>Mirrors Python's {@code StoreProjectionHelper} in
 * {@code openjiuwen/agent_evolving/checkpointing/store_projection.py}.</p>
 */
public class StoreProjectionHelper {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final Pattern EVOLUTION_INDEX_PATTERN = Pattern.compile(
            "<!-- evolution-index-start -->.*?<!-- evolution-index-end -->",
            Pattern.DOTALL);
    private static final int MAX_INJECT_DESC = 5;
    private static final DateTimeFormatter ISO_SECONDS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final StoreProjectionStore store;

    public StoreProjectionHelper(StoreProjectionStore store) {
        this.store = store;
    }

    public CompletionStage<Void> renderEvolutionMarkdown(String name) {
        Path skillDir = store.resolveSkillDir(name);
        if (skillDir == null) {
            return CompletableFuture.completedFuture(null);
        }

        return store.loadFullEvolutionLog(name).thenCompose(evolutionLog -> {
            List<EvolutionRecord> activeEntries = new ArrayList<>();
            for (EvolutionRecord record : evolutionLog.getEntries()) {
                EvolutionPatch change = record.getChange();
                if (change != null && isBlank(change.getSkipReason())) {
                    activeEntries.add(record);
                }
            }
            if (activeEntries.isEmpty()) {
                return clearRenderedOutputs(skillDir);
            }

            Path evolutionDir = skillDir.resolve("evolution");
            createDirectories(evolutionDir);

            Map<String, List<EvolutionRecord>> sectionGroups = new LinkedHashMap<>();
            List<EvolutionRecord> scriptEntries = new ArrayList<>();
            for (EvolutionRecord record : activeEntries) {
                if (record.getChange() != null && record.getChange().getTarget() == EvolutionTarget.SCRIPT) {
                    scriptEntries.add(record);
                } else if (record.getChange() != null) {
                    sectionGroups.computeIfAbsent(record.getChange().getSection(), ignored -> new ArrayList<>()).add(record);
                }
            }

            CompletionStage<Void> stage = CompletableFuture.completedFuture(null);
            for (Map.Entry<String, List<EvolutionRecord>> entry : sectionGroups.entrySet()) {
                stage = stage.thenCompose(ignored -> renderSectionFile(evolutionDir, entry.getKey(), entry.getValue()));
            }
            if (!scriptEntries.isEmpty()) {
                Path scriptsDir = evolutionDir.resolve("scripts");
                createDirectories(scriptsDir);
                stage = stage.thenCompose(ignored -> renderScriptIndex(scriptsDir, scriptEntries));
            }
            return stage.thenCompose(ignored -> updateSkillMdIndex(skillDir, activeEntries))
                    .thenAccept(ignored -> LOGGER.info(
                            "[EvolutionStore] rendered markdown for skill '{}' ({} entries)",
                            name,
                            activeEntries.size()));
        });
    }

    public CompletionStage<Void> clearRenderedOutputs(Path skillDir) {
        Path evolutionDir = skillDir.resolve("evolution");
        if (Files.exists(evolutionDir)) {
            try (Stream<Path> stream = Files.walk(evolutionDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Python ignores non-empty dir deletion failures here as well.
                    }
                });
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to clear rendered outputs under " + evolutionDir, exception);
            }
        }

        Path skillMdPath = store.findSkillMd(skillDir);
        if (skillMdPath == null) {
            return CompletableFuture.completedFuture(null);
        }

        return store.readFileText(skillMdPath).thenCompose(content -> {
            if (isBlank(content) || !EVOLUTION_INDEX_PATTERN.matcher(content).find()) {
                return CompletableFuture.completedFuture(null);
            }
            String cleaned = EVOLUTION_INDEX_PATTERN.matcher(content).replaceAll("");
            cleaned = cleaned.stripTrailing() + "\n";
            return store.writeFileText(skillMdPath, cleaned);
        });
    }

    public CompletionStage<Void> renderSectionFile(Path evolutionDir, String section, List<EvolutionRecord> records) {
        List<String> lines = new ArrayList<>();
        lines.add("# " + section);
        lines.add("");
        lines.add("> Auto-generated from evolutions.json. Do not edit directly.");
        lines.add("");
        for (EvolutionRecord record : records) {
            String content = record.getChange() == null || record.getChange().getContent() == null
                    ? ""
                    : record.getChange().getContent();
            String[] parts = content.isEmpty() ? new String[]{""} : content.split("\n", 2);
            lines.add("<a id=\"" + record.getId() + "\"></a>");
            lines.add("### [" + record.getId() + "] " + recordSummary(record));
            if (!isBlank(record.getSummary()) && !content.isBlank()) {
                lines.add(content.stripTrailing());
            } else if (parts.length > 1 && !parts[1].isBlank()) {
                lines.add(parts[1].stripTrailing());
            }
            String appliedTag = record.isApplied() ? " | applied" : "";
            lines.add("");
            lines.add("*Source: " + nullToEmpty(record.getSource()) + " | " + nullToEmpty(record.getTimestamp()) + appliedTag + "*");
            lines.add("");
            lines.add("---");
            lines.add("");
        }

        Path output = evolutionDir.resolve(sectionFilename(section));
        return store.writeFileText(output, String.join("\n", lines));
    }

    public CompletionStage<Void> renderScriptIndex(Path scriptsDir, List<EvolutionRecord> entries) {
        List<String> lines = new ArrayList<>();
        lines.add("# Script Index");
        lines.add("");
        lines.add("> Auto-generated from evolutions.json. Do not edit directly.");
        lines.add("");
        lines.add("| File | Language | Purpose | Source |");
        lines.add("|------|----------|---------|--------|");
        for (EvolutionRecord record : entries) {
            EvolutionPatch change = record.getChange();
            String filename = !isBlank(change.getScriptFilename()) ? change.getScriptFilename() : record.getId();
            String language = !isBlank(change.getScriptLanguage()) ? change.getScriptLanguage() : "unknown";
            String purpose = nullToEmpty(change.getScriptPurpose());
            String timestamp = nullToEmpty(record.getTimestamp());
            String date = timestamp.length() >= 10 ? timestamp.substring(0, 10) : timestamp;
            lines.add("| [" + filename + "](" + filename + ") | " + language + " | " + purpose + " | " + date + " |");
        }
        lines.add("");
        return store.writeFileText(scriptsDir.resolve("_index.md"), String.join("\n", lines));
    }

    public CompletionStage<Void> updateSkillMdIndex(Path skillDir, List<EvolutionRecord> entries) {
        Path skillMdPath = store.findSkillMd(skillDir);
        if (skillMdPath == null) {
            return CompletableFuture.completedFuture(null);
        }

        int bodyCount = 0;
        int descriptionCount = 0;
        int scriptCount = 0;
        List<EvolutionRecord> narrativeEntries = new ArrayList<>();
        List<EvolutionRecord> scriptEntries = new ArrayList<>();
        for (EvolutionRecord record : entries) {
            EvolutionTarget target = record.getChange() == null ? EvolutionTarget.BODY : record.getChange().getTarget();
            if (target == EvolutionTarget.BODY) {
                bodyCount++;
                narrativeEntries.add(record);
            } else if (target == EvolutionTarget.DESCRIPTION) {
                descriptionCount++;
                narrativeEntries.add(record);
            } else if (target == EvolutionTarget.SCRIPT) {
                scriptCount++;
                scriptEntries.add(record);
            }
        }

        List<String> parts = new ArrayList<>();
        if (bodyCount > 0) {
            parts.add(bodyCount + " body");
        }
        if (descriptionCount > 0) {
            parts.add(descriptionCount + " description");
        }
        if (scriptCount > 0) {
            parts.add(scriptCount + " script");
        }

        List<String> experienceIndexLines = formatExperienceIndexTable(narrativeEntries);
        List<String> scriptTableLines = formatScriptAssetsTable(scriptEntries);
        String now = ZonedDateTime.now(ZoneOffset.UTC).format(ISO_SECONDS_FORMATTER);
        List<String> blockLines = new ArrayList<>();
        blockLines.add("<!-- evolution-index-start -->");
        blockLines.add("## Evolution Experiences");
        blockLines.add("");
        blockLines.add(
                "Use this section as an index of lessons learned from previous executions. Before applying this skill, "
                        + "check whether the current task matches any listed experience summary. If it matches, read the "
                        + "linked detail section first and use the guidance while planning and executing the task.");
        blockLines.add("");
        blockLines.add(
                "For narrative guidance, read the relevant `evolution/*.md#...` detail section. For reusable helper code, "
                        + "first review `evolution/scripts/_index.md`, then inspect the specific script source before "
                        + "adapting or running it. Scripts are implementation aids, not mandatory steps.");
        blockLines.add("");
        blockLines.add("This skill has accumulated **" + entries.size() + "** evolution experiences (" + String.join(", ", parts) + ").");
        blockLines.add("");
        blockLines.addAll(experienceIndexLines);
        blockLines.addAll(scriptTableLines);
        blockLines.add("*Last updated: " + now + "*");
        blockLines.add("<!-- evolution-index-end -->");
        String indexBlock = String.join("\n", blockLines);

        return store.readFileText(skillMdPath).thenCompose(content -> {
            String updated;
            Matcher matcher = EVOLUTION_INDEX_PATTERN.matcher(content);
            if (matcher.find()) {
                updated = matcher.replaceAll(Matcher.quoteReplacement(indexBlock));
            } else {
                updated = content.stripTrailing() + "\n\n" + indexBlock + "\n";
            }
            return store.writeFileText(skillMdPath, updated);
        });
    }

    public CompletionStage<String> formatDescExperienceText(String name, int maxItems) {
        return store.getPendingRecords(name, EvolutionTarget.DESCRIPTION).thenApply(pending -> {
            if (pending.isEmpty()) {
                return "";
            }
            List<EvolutionRecord> ordered = new ArrayList<>(pending);
            ordered.sort(Comparator.comparingDouble(EvolutionRecord::getScore).reversed());
            List<String> lines = new ArrayList<>();
            int limit = Math.min(maxItems, ordered.size());
            for (int index = 0; index < limit; index++) {
                lines.add("- " + ordered.get(index).getChange().getContent());
            }
            return String.join("\n", lines);
        });
    }

    public CompletionStage<String> formatDescExperienceText(String name) {
        return formatDescExperienceText(name, MAX_INJECT_DESC);
    }

    public CompletionStage<Map<String, String>> formatAllDescExperiences(List<String> names) {
        CompletionStage<Map<String, String>> stage = CompletableFuture.completedFuture(new LinkedHashMap<>());
        for (String name : names) {
            stage = stage.thenCompose(result -> formatDescExperienceText(name).thenApply(text -> {
                if (!text.isEmpty()) {
                    result.put(name, text);
                }
                return result;
            }));
        }
        return stage;
    }

    public CompletionStage<String> formatBodyExperienceText(String name) {
        return store.getPendingRecords(name, EvolutionTarget.BODY).thenApply(pending -> {
            if (pending.isEmpty()) {
                return "";
            }
            List<String> lines = new ArrayList<>();
            lines.add("");
            lines.add("");
            lines.add("# Skill '" + name + "' body 演进经验");
            for (int index = 0; index < pending.size(); index++) {
                EvolutionRecord record = pending.get(index);
                lines.add((index + 1) + ". **[" + record.getChange().getSection() + "]** " + record.getChange().getContent());
            }
            return String.join("\n", lines);
        });
    }

    public CompletionStage<String> listPendingSummary(List<String> names) {
        CompletionStage<List<String>> stage = CompletableFuture.completedFuture(new ArrayList<>());
        for (String name : names) {
            stage = stage.thenCompose(lines -> store.getPendingRecords(name, EvolutionTarget.DESCRIPTION)
                    .thenCompose(descPending -> store.getPendingRecords(name, EvolutionTarget.BODY).thenApply(bodyPending -> {
                        List<EvolutionRecord> allPending = new ArrayList<>(descPending);
                        allPending.addAll(bodyPending);
                        if (allPending.isEmpty()) {
                            return lines;
                        }

                        int count = 0;
                        for (String line : lines) {
                            if (line.matches("^\\d+\\.\\s.*")) {
                                count++;
                            }
                        }
                        lines.add((count + 1) + ". **" + name + "** - 共 " + allPending.size()
                                + " 条 pending 经验（description: " + descPending.size()
                                + ", body: " + bodyPending.size() + "）");
                        for (EvolutionRecord record : allPending) {
                            String targetTag = record.getChange().getTarget() == EvolutionTarget.DESCRIPTION
                                    ? "description"
                                    : "body";
                            String content = nullToEmpty(record.getChange().getContent());
                            String title = content.contains("\n") ? content.substring(0, content.indexOf('\n')) : truncate(content, 50);
                            lines.add("   - [" + targetTag + "] **" + title + "**: ");
                            if (content.contains("\n")) {
                                String[] bodyLines = content.split("\n");
                                List<String> fragments = new ArrayList<>();
                                for (int i = 1; i < bodyLines.length; i++) {
                                    String trimmed = bodyLines[i].trim();
                                    if (!trimmed.isEmpty()) {
                                        fragments.add(trimmed.replaceFirst("^-\\s*", ""));
                                    }
                                }
                                if (!fragments.isEmpty()) {
                                    String summary = String.join(" ", fragments).replace("**", "");
                                    lines.add("    " + truncate(summary, 100));
                                }
                            }
                        }
                        lines.add("");
                        return lines;
                    })));
        }

        return stage.thenApply(lines -> lines.isEmpty() ? "当前所有 Skill 暂无演进信息。" : String.join("\n", lines));
    }

    public static String extractDescriptionFromSkillMd(String content) {
        if (content == null || !content.startsWith("---")) {
            return "";
        }
        String[] parts = content.split("---", 3);
        if (parts.length < 3) {
            return "";
        }
        String frontMatter = parts[1];
        for (String line : frontMatter.strip().split("\n")) {
            if (line.startsWith("description:")) {
                return line.substring("description:".length()).trim().replace("\"", "").replace("'", "");
            }
        }
        return "";
    }

    private static List<String> formatExperienceIndexTable(List<EvolutionRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        List<EvolutionRecord> ordered = new ArrayList<>(records);
        ordered.sort(Comparator.comparing(StoreProjectionHelper::timestampValue).reversed());
        ordered.sort(Comparator.comparingDouble(EvolutionRecord::getScore).reversed());
        ordered.sort(Comparator.comparing(record -> nullToEmpty(record.getChange().getSection())));
        List<String> lines = new ArrayList<>();
        lines.add("### Experience Index");
        lines.add("");
        lines.add("| Summary | Type | Score | Detail |");
        lines.add("|---------|------|-------|--------|");
        for (EvolutionRecord record : ordered) {
            String detailPath = "evolution/" + sectionFilename(record.getChange().getSection()) + "#" + record.getId();
            lines.add("| " + recordSummary(record)
                    + " | " + record.getChange().getSection()
                    + " | " + String.format(java.util.Locale.ROOT, "%.2f", record.getScore())
                    + " | [" + detailPath + "](" + detailPath + ") |");
        }
        lines.add("");
        return lines;
    }

    private static List<String> formatScriptAssetsTable(List<EvolutionRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        List<EvolutionRecord> ordered = new ArrayList<>(records);
        ordered.sort(Comparator.comparing(StoreProjectionHelper::timestampValue).reversed());
        ordered.sort(Comparator.comparingDouble(EvolutionRecord::getScore).reversed());
        List<String> lines = new ArrayList<>();
        lines.add("### Script Assets");
        lines.add("");
        lines.add("| Summary | Language | Score | Index | Source |");
        lines.add("|---------|----------|-------|-------|--------|");
        for (EvolutionRecord record : ordered) {
            EvolutionPatch change = record.getChange();
            String filename = !isBlank(change.getScriptFilename()) ? change.getScriptFilename() : record.getId();
            String source = "evolution/scripts/" + filename;
            lines.add("| " + recordSummary(record)
                    + " | " + nullToEmpty(change.getScriptLanguage(), "unknown")
                    + " | " + String.format(java.util.Locale.ROOT, "%.2f", record.getScore())
                    + " | [evolution/scripts/_index.md](evolution/scripts/_index.md)"
                    + " | [" + source + "](" + source + ") |");
        }
        lines.add("");
        return lines;
    }

    private static String sectionFilename(String section) {
        return nullToEmpty(section).toLowerCase().replace(" ", "_") + ".md";
    }

    private static String recordSummary(EvolutionRecord record) {
        if (!isBlank(record.getSummary())) {
            return normalizeSummaryText(record.getSummary(), 96);
        }
        EvolutionPatch change = record.getChange();
        if (change != null && change.getTarget() == EvolutionTarget.SCRIPT && !isBlank(change.getScriptPurpose())) {
            return normalizeSummaryText(change.getScriptPurpose(), 96);
        }
        String content = change == null ? "" : nullToEmpty(change.getContent());
        String firstLine = content.isEmpty() ? "" : content.split("\n", 2)[0];
        String normalized = normalizeSummaryText(firstLine, 96);
        return normalized.isEmpty() ? record.getId() : normalized;
    }

    private static String normalizeSummaryText(String text, int maxChars) {
        String value = nullToEmpty(text).trim();
        value = value.replaceFirst("^#{1,6}\\s*", "");
        value = value.replace("|", " ");
        value = value.replaceAll("\\s+", " ").trim();
        if (value.length() > maxChars) {
            return value.substring(0, maxChars - 3).stripTrailing() + "...";
        }
        return value;
    }

    private static String timestampValue(EvolutionRecord record) {
        return nullToEmpty(record.getTimestamp());
    }

    private static void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create directory: " + path, exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String nullToEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * Boundary used by {@link StoreProjectionHelper} to access the outer store.
     *
     * <p>Mirrors Python's store interaction surface in
     * {@code openjiuwen/agent_evolving/checkpointing/store_projection.py}.</p>
     */
    public interface StoreProjectionStore {

        Path resolveSkillDir(String name);

        CompletionStage<EvolutionLog> loadFullEvolutionLog(String name);

        CompletionStage<List<EvolutionRecord>> getPendingRecords(String name, EvolutionTarget target);

        Path findSkillMd(Path skillDir);

        CompletionStage<String> readFileText(Path path);

        CompletionStage<Void> writeFileText(Path path, String content);
    }
}
