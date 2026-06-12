/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Store and load evolution records for skill directories.
 *
 * <p>Mirrors Python's {@code EvolutionStore} in
 * {@code openjiuwen/agent_evolving/checkpointing/evolution_store.py}.</p>
 */
public class EvolutionStore implements
        StoreRecordsHelper.StoreRecordsStore,
        StoreProjectionHelper.StoreProjectionStore,
        StoreArchiveHelper.StoreArchiveStore {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final Pattern EVOLUTION_INDEX_PATTERN = Pattern.compile(
            "<!-- evolution-index-start -->.*?<!-- evolution-index-end -->",
            Pattern.DOTALL);
    private static final Pattern VALID_FRONTMATTER_LINE = Pattern.compile("^([A-Za-z0-9_-]+)\\s*:\\s*(.*)$");
    private static final Set<String> EXCLUDED_PACK_DIRS = Set.of("evolution", "archive", "__pycache__", ".git");
    private static final Set<String> EXCLUDED_PACK_FILES = Set.of("evolutions.json");
    private static final int TAR_BLOCK_SIZE = 512;

    private final List<Path> baseDirs;
    private final ConcurrentMap<String, Object> skillSemanticLocks = new ConcurrentHashMap<>();
    private final StoreRecordsHelper records;
    private final StoreProjectionHelper projection;
    private final StoreArchiveHelper archive;
    private Object sysOperation;

    public EvolutionStore(String skillsBaseDir) {
        this(parseBaseDirInput(skillsBaseDir));
    }

    public EvolutionStore(List<String> skillsBaseDirs) {
        this.baseDirs = normalizeBaseDirs(skillsBaseDirs);
        if (this.baseDirs.isEmpty()) {
            throw new IllegalArgumentException("skills_base_dir is empty");
        }
        this.records = new StoreRecordsHelper(this);
        this.projection = new StoreProjectionHelper(this);
        this.archive = new StoreArchiveHelper(this);
    }

    public List<Path> getBaseDirs() {
        return List.copyOf(baseDirs);
    }

    public Path getBaseDir() {
        return baseDirs.get(0);
    }

    public Object getSysOperation() {
        return sysOperation;
    }

    public void setSysOperation(Object sysOperation) {
        this.sysOperation = sysOperation;
    }

    public List<String> listSkillNames() {
        List<String> names = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Path root : baseDirs) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(root)) {
                stream.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .forEach(path -> {
                            String name = path.getFileName().toString();
                            if (!name.startsWith("_") && seen.add(name)) {
                                names.add(name);
                            }
                        });
            } catch (IOException exception) {
                LOGGER.warning("[EvolutionStore] list skills failed for {}: {}", root, exception.getMessage());
            }
        }
        return names;
    }

    public boolean skillExists(String name) {
        return resolveSkillDir(name, false) != null;
    }

    public boolean skillDefinitionExists(String name) {
        Path skillDir = resolveSkillDir(name, false);
        return skillDir != null && Files.isRegularFile(skillDir.resolve("SKILL.md"));
    }

    public CompletionStage<String> readSkillContent(String name) {
        return readSkillContent(name, false);
    }

    public CompletionStage<String> readSkillContent(String name, boolean strict) {
        Path skillDir = resolveSkillDir(name, false);
        if (skillDir == null) {
            if (strict) {
                return CompletableFuture.failedFuture(definitionNotFound("skill '" + name + "' does not exist"));
            }
            return CompletableFuture.completedFuture("");
        }
        Path markdownPath = strict ? skillDir.resolve("SKILL.md") : findSkillMd(skillDir);
        if (strict && !Files.isRegularFile(markdownPath)) {
            return CompletableFuture.failedFuture(definitionNotFound("skill '" + name + "' is missing SKILL.md"));
        }
        if (markdownPath == null) {
            return CompletableFuture.completedFuture("");
        }
        return readFileText(markdownPath);
    }

    public CompletionStage<String> readPristineSkillContent(String name) {
        return readSkillContent(name).thenApply(content -> {
            if (content == null || content.isEmpty()) {
                return "";
            }
            String stripped = EVOLUTION_INDEX_PATTERN.matcher(content).replaceAll("");
            return stripped.stripTrailing() + "\n";
        });
    }

    public CompletionStage<String> readSkillId(String name) {
        return readSkillContent(name).thenApply(EvolutionStore::readSkillIdFromContent);
    }

    public CompletionStage<String> ensureSkillId(String name) {
        Path skillDir = resolveSkillDir(name, false);
        if (skillDir == null) {
            return CompletableFuture.completedFuture("");
        }
        Path markdownPath = findSkillMd(skillDir);
        if (markdownPath == null) {
            return CompletableFuture.completedFuture("");
        }
        return readFileText(markdownPath).thenCompose(content -> {
            if (content == null || content.isEmpty()) {
                return CompletableFuture.completedFuture("");
            }
            SkillIdContent ensured = ensureSkillIdInContent(content);
            if (!ensured.content().equals(content)) {
                return writeFileText(markdownPath, ensured.content()).thenApply(ignored -> {
                    LOGGER.info("[EvolutionStore] assigned skill_id={} for skill={}", ensured.skillId(), name);
                    return ensured.skillId();
                });
            }
            return CompletableFuture.completedFuture(ensured.skillId());
        });
    }

    public CompletionStage<byte[]> packSkillForSharing(String name) {
        Path skillDir = resolveSkillDir(name, false);
        if (skillDir == null) {
            return CompletableFuture.completedFuture(new byte[0]);
        }
        Path markdownPath = findSkillMd(skillDir);
        if (markdownPath == null) {
            return CompletableFuture.completedFuture(packSkillDirectory(skillDir, null, null));
        }
        return readPristineSkillContent(name).thenApply(pristine -> {
            if (pristine.isEmpty()) {
                return packSkillDirectory(skillDir, null, null);
            }
            String relative = skillDir.relativize(markdownPath).toString().replace('\\', '/');
            return packSkillDirectory(skillDir, relative, pristine);
        });
    }

    public CompletionStage<Path> installSkillPackage(byte[] packageBytes) {
        return installSkillPackage(packageBytes, null);
    }

    public CompletionStage<Path> installSkillPackage(byte[] packageBytes, String skillName) {
        if (packageBytes == null || packageBytes.length == 0) {
            return CompletableFuture.completedFuture(null);
        }
        String resolvedName = skillName == null ? "" : skillName.strip();
        List<TarEntryData> entries;
        try {
            entries = readTarGz(packageBytes);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(storeError("failed to unpack skill package: " + exception.getMessage(), exception));
        }
        if (resolvedName.isEmpty()) {
            resolvedName = inferSkillName(entries);
        }
        if (resolvedName.isEmpty()) {
            LOGGER.warning("[EvolutionStore] install_skill_package: cannot infer skill name");
            return CompletableFuture.completedFuture(null);
        }

        Path destination = resolveSkillDir(resolvedName, true);
        if (destination == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            if (Files.exists(destination) && hasAnyChild(destination)) {
                LOGGER.warning("[EvolutionStore] install_skill_package: skill directory already exists: {}", destination);
                return CompletableFuture.completedFuture(null);
            }
            SkillPackage.unpackSkillPackage(packageBytes, destination);
            LOGGER.info("[EvolutionStore] installed skill package to {}", destination);
            return CompletableFuture.completedFuture(destination);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(storeError("failed to install skill package: " + exception.getMessage(), exception));
        }
    }

    @Override
    public Path resolveSkillDir(String name, boolean create) {
        for (Path baseDir : baseDirs) {
            Path candidate = baseDir.resolve(name);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        if (create && !baseDirs.isEmpty()) {
            return baseDirs.get(0).resolve(name);
        }
        return null;
    }

    @Override
    public Path resolveSkillDir(String name) {
        return resolveSkillDir(name, false);
    }

    @Override
    public Path findSkillMd(Path skillDir) {
        if (skillDir == null) {
            return null;
        }
        Path skillMarkdown = skillDir.resolve("SKILL.md");
        if (Files.isRegularFile(skillMarkdown)) {
            return skillMarkdown;
        }
        try (Stream<Path> stream = Files.list(skillDir)) {
            Optional<Path> firstMarkdown = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .findFirst();
            return firstMarkdown.orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    @Override
    public CompletionStage<String> readFileText(Path path) {
        try {
            if (path == null || !Files.exists(path)) {
                return CompletableFuture.completedFuture("");
            }
            return CompletableFuture.completedFuture(Files.readString(path, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            LOGGER.warning("[EvolutionStore] failed to read {}: {}", path, exception.getMessage());
            return CompletableFuture.completedFuture("");
        }
    }

    @Override
    public CompletionStage<Void> writeFileText(Path path, String content) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
            return CompletableFuture.completedFuture(null);
        } catch (Exception exception) {
            LOGGER.error("[EvolutionStore] write {} failed: {}", path, exception.getMessage());
            return CompletableFuture.failedFuture(storeError("failed to write " + path + ": " + exception.getMessage(), exception));
        }
    }

    public CompletionStage<EvolutionLog> loadEvolutionLog(String name, EvolutionTarget target) {
        return loadFullEvolutionLog(name).thenApply(evolutionLog -> {
            if (target == null) {
                return evolutionLog;
            }
            List<EvolutionRecord> filtered = new ArrayList<>();
            for (EvolutionRecord record : evolutionLog.getEntries()) {
                if (record.getChange() != null && record.getChange().getTarget() == target) {
                    filtered.add(record);
                }
            }
            return new EvolutionLog(evolutionLog.getSkillId(), evolutionLog.getVersion(), evolutionLog.getUpdatedAt(), filtered);
        });
    }

    public CompletionStage<EvolutionLog> loadEvolutionLog(String name) {
        return loadEvolutionLog(name, null);
    }

    @Override
    public CompletionStage<EvolutionLog> loadFullEvolutionLog(String name) {
        return records.loadFullEvolutionLog(name);
    }

    public CompletionStage<Void> appendRecord(String name, EvolutionRecord record) {
        return withSkillLock(name, () -> records.appendRecordTransactional(name, record).thenAccept(evolutionLog -> {
            if (evolutionLog == null) {
                return;
            }
            LOGGER.info(
                    "[EvolutionStore] wrote {}/evolutions.json (id={}, target={})",
                    name,
                    record.getId(),
                    record.getChange() == null ? "" : record.getChange().getTarget().getValue());
            int total = evolutionLog.getEntries().size();
            if (total >= 30) {
                LOGGER.warning("[EvolutionStore] skill '{}' has {} experiences, consider /evolve_simplify", name, total);
            }
        }));
    }

    @Override
    public CompletionStage<Void> saveEvolutionLog(String name, EvolutionLog evolutionLog, Path skillDir) {
        return records.saveEvolutionLog(name, evolutionLog, skillDir);
    }

    public CompletionStage<Void> saveEvolutionLog(String name, EvolutionLog evolutionLog) {
        return saveEvolutionLog(name, evolutionLog, null);
    }

    @Override
    public CompletionStage<List<EvolutionRecord>> getPendingRecords(String name, EvolutionTarget target) {
        return loadEvolutionLog(name, target).thenApply(EvolutionLog::getPendingEntries);
    }

    public CompletionStage<Void> renderEvolutionMarkdown(String name) {
        return projection.renderEvolutionMarkdown(name);
    }

    public CompletionStage<String> formatDescExperienceText(String name, int maxItems) {
        return projection.formatDescExperienceText(name, maxItems);
    }

    public CompletionStage<String> formatDescExperienceText(String name) {
        return projection.formatDescExperienceText(name);
    }

    public CompletionStage<Map<String, String>> formatAllDescExperiences(List<String> names) {
        return projection.formatAllDescExperiences(names);
    }

    public CompletionStage<String> formatBodyExperienceText(String name) {
        return projection.formatBodyExperienceText(name);
    }

    public CompletionStage<String> listPendingSummary(List<String> names) {
        return projection.listPendingSummary(names);
    }

    public CompletionStage<Integer> updateRecordScores(String name, Map<String, StoreRecordsHelper.RecordUpdate> updates) {
        return withSkillLock(name, () -> records.updateRecordScores(name, updates));
    }

    public CompletionStage<List<EvolutionRecord>> getRecordsByScore(String name, Double minScore) {
        return records.getRecordsByScore(name, minScore);
    }

    public CompletionStage<Integer> deleteRecords(String name, List<String> recordIds) {
        return withSkillLock(name, () -> records.deleteRecords(name, recordIds));
    }

    public CompletionStage<Integer> markRecordsApplied(String name, List<String> recordIds) {
        return withSkillLock(name, () -> records.markRecordsApplied(name, recordIds));
    }

    public CompletionStage<EvolutionRecord> mergeRecords(
            String name,
            String primaryId,
            List<String> removeIds,
            String newContent,
            Double newScore
    ) {
        return withSkillLock(name, () -> records.mergeRecords(name, primaryId, removeIds, newContent, newScore));
    }

    public CompletionStage<EvolutionRecord> mergeRecords(
            String name,
            String primaryId,
            List<String> removeIds,
            String newContent
    ) {
        return mergeRecords(name, primaryId, removeIds, newContent, null);
    }

    public CompletionStage<EvolutionRecord> updateRecordContent(
            String name,
            String recordId,
            String newContent,
            Double newScore
    ) {
        return withSkillLock(name, () -> records.updateRecordContent(name, recordId, newContent, newScore));
    }

    public CompletionStage<EvolutionRecord> updateRecordContent(String name, String recordId, String newContent) {
        return updateRecordContent(name, recordId, newContent, null);
    }

    public CompletionStage<Path> createSkill(String name, String description, String body, String frontmatter) {
        return archive.createSkill(name, description, body, frontmatter);
    }

    public CompletionStage<Path> createSkill(String name, String description, String body) {
        return createSkill(name, description, body, null);
    }

    public CompletionStage<List<SkillDescription>> listSkillNamesWithDescriptions() {
        List<SkillDescription> result = new ArrayList<>();
        CompletionStage<List<SkillDescription>> stage = CompletableFuture.completedFuture(result);
        for (String name : listSkillNames()) {
            stage = stage.thenCompose(items -> readSkillContent(name).thenApply(content -> {
                items.add(new SkillDescription(name, extractDescriptionFromSkillMd(content)));
                return items;
            }));
        }
        return stage;
    }

    public static String extractDescriptionFromSkillMd(String content) {
        return StoreProjectionHelper.extractDescriptionFromSkillMd(content);
    }

    public CompletionStage<String> archiveSkillBody(String name) {
        return archive.archiveSkillBody(name);
    }

    public CompletionStage<String> archiveEvolutions(String name) {
        return archive.archiveEvolutions(name);
    }

    public CompletionStage<Void> clearEvolutions(String name) {
        return archive.clearEvolutions(name);
    }

    public List<String> listArchives(String name) {
        return archive.listArchives(name);
    }

    private <T> CompletionStage<T> withSkillLock(String name, Supplier<CompletionStage<T>> action) {
        Object lock = skillSemanticLocks.computeIfAbsent(name, ignored -> new Object());
        synchronized (lock) {
            try {
                return CompletableFuture.completedFuture(action.get().toCompletableFuture().join());
            } catch (CompletionException exception) {
                return CompletableFuture.failedFuture(exception.getCause() == null ? exception : exception.getCause());
            } catch (RuntimeException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
    }

    private static List<String> parseBaseDirInput(String raw) {
        if (raw == null) {
            return List.of();
        }
        String text = raw.strip();
        if (text.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(text.replace(",", ";").split(";"))
                .map(String::strip)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static List<Path> normalizeBaseDirs(List<String> rawDirs) {
        List<Path> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (rawDirs == null) {
            return normalized;
        }
        for (String raw : rawDirs) {
            for (String item : parseBaseDirInput(raw)) {
                Path path = expandUser(item).toAbsolutePath().normalize();
                String key = path.toString();
                if (seen.add(key)) {
                    normalized.add(path);
                }
            }
        }
        return normalized;
    }

    private static Path expandUser(String raw) {
        if (raw.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        if (raw.startsWith("~/") || raw.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home")).resolve(raw.substring(2));
        }
        return Path.of(raw);
    }

    private static BaseError definitionNotFound(String message) {
        return ErrorHelper.buildError(
                StatusCode.TOOLCHAIN_EVOLVING_SKILL_DEFINITION_NOT_FOUND,
                null,
                null,
                null,
                Map.of("error_msg", message)
        );
    }

    private static BaseError storeError(String message, Throwable cause) {
        return ErrorHelper.buildError(
                StatusCode.TOOLCHAIN_EVOLVING_SKILL_STORE_EXECUTION_ERROR,
                null,
                null,
                cause,
                Map.of("error_msg", message)
        );
    }

    public static String newSkillId() {
        return SkillPackage.newSkillId();
    }

    public static String readSkillIdFromContent(String content) {
        return SkillPackage.readSkillIdFromContent(content);
    }

    public static SkillIdContent ensureSkillIdInContent(String content) {
        SkillPackage.SkillIdContent ensured = SkillPackage.ensureSkillIdInContent(content);
        return new SkillIdContent(ensured.content(), ensured.skillId());
    }

    private static String stripBom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private static String parseFrontmatterValue(String content, String key) {
        String text = stripBom(content == null ? "" : content);
        if (!text.startsWith("---")) {
            return "";
        }
        int closing = findClosingFrontmatter(text);
        if (closing < 0) {
            return "";
        }
        String frontmatter = text.substring(3, closing);
        for (String line : frontmatter.split("\\R")) {
            Matcher matcher = VALID_FRONTMATTER_LINE.matcher(line.strip());
            if (matcher.matches() && key.equals(matcher.group(1))) {
                return matcher.group(2).strip().replace("\"", "").replace("'", "");
            }
        }
        return "";
    }

    private static int findClosingFrontmatter(String text) {
        Matcher matcher = Pattern.compile("(?m)^---\\s*$").matcher(text);
        if (!matcher.find()) {
            return -1;
        }
        return matcher.find() ? matcher.start() : -1;
    }

    private static byte[] packSkillDirectory(Path skillDir, String skillMarkdownRelativePath, String skillMarkdownContent) {
        return SkillPackage.packSkillDirectory(skillDir, skillMarkdownRelativePath, skillMarkdownContent);
    }

    private static boolean shouldPack(Path relative) {
        if (relative.getNameCount() == 0) {
            return false;
        }
        for (Path part : relative) {
            if (EXCLUDED_PACK_DIRS.contains(part.toString())) {
                return false;
            }
        }
        String fileName = relative.getFileName().toString();
        return !EXCLUDED_PACK_FILES.contains(fileName) && !fileName.startsWith(".");
    }

    private static void writeTarEntry(ByteArrayOutputStream output, String name, byte[] content, long modifiedAt)
            throws IOException {
        byte[] header = new byte[TAR_BLOCK_SIZE];
        String entryName = name;
        String prefix = "";
        if (entryName.getBytes(StandardCharsets.UTF_8).length > 100) {
            int slash = entryName.lastIndexOf('/');
            if (slash > 0) {
                prefix = entryName.substring(0, slash);
                entryName = entryName.substring(slash + 1);
            }
        }
        writeString(header, 0, 100, entryName);
        writeOctal(header, 100, 8, 0644);
        writeOctal(header, 108, 8, 0);
        writeOctal(header, 116, 8, 0);
        writeOctal(header, 124, 12, content.length);
        writeOctal(header, 136, 12, modifiedAt);
        Arrays.fill(header, 148, 156, (byte) ' ');
        header[156] = '0';
        writeString(header, 257, 6, "ustar");
        writeString(header, 263, 2, "00");
        writeString(header, 345, 155, prefix);
        long checksum = 0;
        for (byte item : header) {
            checksum += Byte.toUnsignedInt(item);
        }
        writeChecksum(header, checksum);
        output.write(header);
        output.write(content);
        int padding = TAR_BLOCK_SIZE - (content.length % TAR_BLOCK_SIZE);
        if (padding < TAR_BLOCK_SIZE) {
            output.write(new byte[padding]);
        }
    }

    private static void writeString(byte[] header, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int count = Math.min(bytes.length, length);
        System.arraycopy(bytes, 0, header, offset, count);
    }

    private static void writeOctal(byte[] header, int offset, int length, long value) {
        String text = Long.toOctalString(value);
        String padded = "0".repeat(Math.max(0, length - text.length() - 1)) + text;
        writeString(header, offset, length - 1, padded);
        header[offset + length - 1] = 0;
    }

    private static void writeChecksum(byte[] header, long checksum) {
        String text = Long.toOctalString(checksum);
        String padded = "0".repeat(Math.max(0, 6 - text.length())) + text;
        writeString(header, 148, 6, padded);
        header[154] = 0;
        header[155] = (byte) ' ';
    }

    private static List<TarEntryData> readTarGz(byte[] packageBytes) throws IOException {
        List<TarEntryData> entries = new ArrayList<>();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(packageBytes))) {
            byte[] header = new byte[TAR_BLOCK_SIZE];
            while (readFully(gzip, header) == TAR_BLOCK_SIZE) {
                if (isZeroBlock(header)) {
                    break;
                }
                String name = readString(header, 0, 100);
                String prefix = readString(header, 345, 155);
                if (!prefix.isEmpty()) {
                    name = prefix + "/" + name;
                }
                long size = readOctal(header, 124, 12);
                boolean directory = header[156] == '5';
                byte[] content = gzip.readNBytes(Math.toIntExact(size));
                long padding = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)) % TAR_BLOCK_SIZE;
                if (padding > 0) {
                    gzip.skipNBytes(padding);
                }
                entries.add(new TarEntryData(name, directory, content));
                Arrays.fill(header, (byte) 0);
            }
        }
        return entries;
    }

    private static int readFully(GZIPInputStream input, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = input.read(buffer, total, buffer.length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte item : block) {
            if (item != 0) {
                return false;
            }
        }
        return true;
    }

    private static String readString(byte[] header, int offset, int length) {
        int end = offset;
        int max = offset + length;
        while (end < max && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8).strip();
    }

    private static long readOctal(byte[] header, int offset, int length) {
        String text = readString(header, offset, length).strip();
        return text.isEmpty() ? 0 : Long.parseLong(text, 8);
    }

    private static String inferSkillName(List<TarEntryData> entries) {
        Set<String> topLevelNames = new LinkedHashSet<>();
        for (TarEntryData entry : entries) {
            if (entry.name().isBlank() || entry.name().startsWith("/")) {
                continue;
            }
            int slash = entry.name().indexOf('/');
            topLevelNames.add(slash < 0 ? entry.name() : entry.name().substring(0, slash));
        }
        if (topLevelNames.size() == 1) {
            String only = topLevelNames.iterator().next();
            return only.endsWith(".md") ? "" : only;
        }
        for (TarEntryData entry : entries) {
            if (entry.name().endsWith("SKILL.md") && entry.name().contains("/")) {
                return entry.name().substring(0, entry.name().indexOf('/'));
            }
        }
        return "";
    }

    private static boolean hasAnyChild(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.findAny().isPresent();
        }
    }

    public record SkillIdContent(String content, String skillId) {
    }

    public record SkillDescription(String name, String description) {
    }

    private record TarEntryData(String name, boolean directory, byte[] content) {
    }
}
