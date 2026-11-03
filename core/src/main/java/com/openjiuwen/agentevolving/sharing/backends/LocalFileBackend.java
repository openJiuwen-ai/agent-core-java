/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing.backends;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.sharing.QueryKeywords;
import com.openjiuwen.agentevolving.sharing.SharedExperience;
import com.openjiuwen.agentevolving.sharing.SharedSkillBundle;
import com.openjiuwen.agentevolving.sharing.SkillPackageMeta;
import com.openjiuwen.agentevolving.sharing.SkillSearchResult;
import com.openjiuwen.agentevolving.sharing.UploadResult;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Local-filesystem implementation of {@link SharingBackend}.
 *
 * <p>Mirrors Python's {@code LocalFileBackend} in
 * {@code openjiuwen/agent_evolving/sharing/backends/local_file.py}.</p>
 */
public class LocalFileBackend implements SharingBackend {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String DEFAULT_HUB_PATH = "~/.openjiuwen/experience_hub";
    private static final String GLOBAL_INDEX = "global.jsonl";

    private final Path hubPath;
    private final Path packagesDir;
    private final Path bundlesDir;
    private final Path indexDir;
    private final Path outboxDir;
    private final double dedupJaccardThreshold;
    private final Object lock = new Object();

    public LocalFileBackend() {
        this((String) null, 0.85d);
    }

    public LocalFileBackend(String hubPath) {
        this(hubPath, 0.85d);
    }

    public LocalFileBackend(Path hubPath) {
        this(hubPath, 0.85d);
    }

    public LocalFileBackend(String hubPath, double dedupJaccardThreshold) {
        this(resolvePath(hubPath == null || hubPath.isBlank() ? DEFAULT_HUB_PATH : hubPath), dedupJaccardThreshold);
    }

    public LocalFileBackend(Path hubPath, double dedupJaccardThreshold) {
        this.hubPath = resolvePath(hubPath == null ? DEFAULT_HUB_PATH : hubPath.toString());
        this.packagesDir = this.hubPath.resolve("packages");
        this.bundlesDir = this.hubPath.resolve("bundles");
        this.indexDir = this.hubPath.resolve("index");
        this.outboxDir = this.hubPath.resolve(".outbox");
        this.dedupJaccardThreshold = dedupJaccardThreshold;
    }

    public Path getHubPath() {
        return hubPath;
    }

    public Path getOutboxDir() {
        return outboxDir;
    }

    @Override
    public CompletionStage<UploadResult> uploadBundle(SharedSkillBundle bundle) {
        SharedSkillBundle resolvedBundle = bundle == null ? new SharedSkillBundle() : bundle;
        String skillId = stringValue(resolvedBundle.getSkillId(), "").trim();
        if (skillId.isEmpty()) {
            return CompletableFuture.completedFuture(uploadFailure("bundle.skill_id is required for upload", false));
        }

        synchronized (lock) {
            String duplicateReason = duplicateRejectionReason(skillId, resolvedBundle.getKeywordsAggregate());
            if (duplicateReason != null) {
                LOGGER.info(
                        "[LocalFileBackend] rejected bundle %s for skill=%s id=%s: %s",
                        resolvedBundle.getBundleId(),
                        resolvedBundle.getSkillName(),
                        skillId,
                        duplicateReason);
                return CompletableFuture.completedFuture(uploadFailure(duplicateReason, false));
            }

            try {
                Files.createDirectories(bundleDir(skillId));
                Files.createDirectories(indexDir);
                Path bundleFile = bundleDir(skillId).resolve(resolvedBundle.getBundleId() + ".json");
                writeJson(bundleFile, resolvedBundle.toDict(), true);

                Map<String, Object> indexEntry = new LinkedHashMap<>();
                indexEntry.put("bundle_id", resolvedBundle.getBundleId());
                indexEntry.put("skill_id", skillId);
                indexEntry.put("skill_name", resolvedBundle.getSkillName());
                indexEntry.put("skill_version", resolvedBundle.getSkillVersion());
                indexEntry.put("keywords", new ArrayList<>(resolvedBundle.getKeywordsAggregate()));
                indexEntry.put("summary", resolvedBundle.getSummaryAggregate());
                indexEntry.put("created_at", resolvedBundle.getCreatedAt());
                appendJsonLine(indexPath(skillId), indexEntry);
                upsertGlobalIndex(skillId, resolvedBundle);
            } catch (IOException exception) {
                LOGGER.warning(
                        "[LocalFileBackend] upload failed for skill_id=%s bundle=%s: %s; routing to outbox",
                        skillId,
                        resolvedBundle.getBundleId(),
                        exception);
                spoolToOutbox(resolvedBundle);
                return CompletableFuture.completedFuture(uploadFailure(exception.getMessage(), true));
            }
        }

        LOGGER.info(
                "[LocalFileBackend] uploaded bundle %s for skill=%s id=%s (%d experience(s))",
                resolvedBundle.getBundleId(),
                resolvedBundle.getSkillName(),
                skillId,
                resolvedBundle.getExperiences().size());
        UploadResult result = new UploadResult();
        result.setOk(true);
        result.setBundleId(resolvedBundle.getBundleId());
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletionStage<List<SharedSkillBundle>> downloadBundles(String skillId, QueryKeywords query, int topK) {
        String resolvedId = stringValue(skillId, "").trim();
        if (resolvedId.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<Map<String, Object>> indexEntries = readIndex(resolvedId);
        if (indexEntries.isEmpty()) {
            LOGGER.info("[LocalFileBackend] download_bundles: no index entries for skill_id=%s", resolvedId);
            return CompletableFuture.completedFuture(List.of());
        }

        List<ScoredEntry> ranked = new ArrayList<>();
        List<String> queryKeywords = query == null ? List.of() : query.getKeywords();
        for (Map<String, Object> entry : indexEntries) {
            double score = jaccard(queryKeywords, stringList(entry.get("keywords")));
            ranked.add(new ScoredEntry(score, entry));
        }
        ranked.sort((left, right) -> Double.compare(right.score, left.score));

        List<SharedSkillBundle> results = new ArrayList<>();
        int limit = Math.max(topK, 0);
        for (int index = 0; index < Math.min(limit, ranked.size()); index++) {
            ScoredEntry candidate = ranked.get(index);
            if (candidate.score <= 0.0d) {
                continue;
            }
            SharedSkillBundle bundle = loadBundle(
                    resolvedId,
                    stringValue(candidate.entry.get("bundle_id"), ""));
            if (bundle != null) {
                LOGGER.info(
                        "[LocalFileBackend] selected bundle=%s for skill_id=%s score=%.4f",
                        bundle.getBundleId(),
                        resolvedId,
                        candidate.score);
                results.add(bundle);
            }
        }
        return CompletableFuture.completedFuture(results);
    }

    @Override
    public CompletionStage<Boolean> hasSkillPackage(String skillId) {
        String resolvedId = stringValue(skillId, "").trim();
        if (resolvedId.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.completedFuture(Files.isRegularFile(packageArchive(resolvedId)));
    }

    @Override
    public CompletionStage<Void> uploadSkillPackage(String skillId, byte[] packageBytes, SkillPackageMeta meta) {
        String resolvedId = stringValue(skillId, "").trim();
        if (resolvedId.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("skill_id is required for uploadSkillPackage"));
        }
        if (packageBytes == null || packageBytes.length == 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("package_bytes is empty"));
        }

        synchronized (lock) {
            if (Files.isRegularFile(packageArchive(resolvedId))) {
                LOGGER.debug(
                        "[LocalFileBackend] skill package already exists for skill_id=%s; skipping upload",
                        resolvedId);
                return CompletableFuture.completedFuture(null);
            }

            try {
                Path packageDir = packageDir(resolvedId);
                Files.createDirectories(packageDir);
                Files.write(packageArchive(resolvedId), packageBytes);

                SkillPackageMeta metaPayload = new SkillPackageMeta();
                metaPayload.setSkillId(resolvedId);
                metaPayload.setSkillName(meta == null ? "" : meta.getSkillName());
                metaPayload.setDescription(meta == null ? "" : meta.getDescription());
                metaPayload.setUploadedAt(meta == null ? null : meta.getUploadedAt());
                writeJson(packageMetaPath(resolvedId), metaPayload.toDict(), true);
                ensureGlobalIndexEntry(resolvedId, metaPayload);
            } catch (IOException exception) {
                LOGGER.warning(
                        "[LocalFileBackend] upload_skill_package failed for skill_id=%s: %s",
                        resolvedId,
                        exception);
                return CompletableFuture.failedFuture(exception);
            }
        }

        LOGGER.info(
                "[LocalFileBackend] uploaded skill package for skill_id=%s name=%s (%d bytes)",
                resolvedId,
                meta == null ? "" : meta.getSkillName(),
                packageBytes.length);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<byte[]> downloadSkillPackage(String skillId) {
        String resolvedId = stringValue(skillId, "").trim();
        if (resolvedId.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Path archive = packageArchive(resolvedId);
        if (!Files.isRegularFile(archive)) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return CompletableFuture.completedFuture(Files.readAllBytes(archive));
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletionStage<SkillPackageMeta> getSkillPackageMeta(String skillId) {
        return CompletableFuture.completedFuture(readPackageMeta(stringValue(skillId, "").trim()));
    }

    @Override
    public CompletionStage<List<SkillSearchResult>> searchSkills(QueryKeywords query, int topK) {
        List<Map<String, Object>> entries = readGlobalIndex();
        if (entries.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<String> queryKeywords = query == null ? List.of() : query.getKeywords();
        List<ScoredEntry> ranked = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            List<String> searchTerms = new ArrayList<>(stringList(entry.get("keywords")));
            String skillName = stringValue(entry.get("skill_name"), "");
            String description = stringValue(entry.get("description"), "");
            searchTerms.add(skillName);
            searchTerms.add(description);
            ranked.add(new ScoredEntry(jaccard(queryKeywords, searchTerms), entry));
        }
        ranked.sort((left, right) -> Double.compare(right.score, left.score));

        List<SkillSearchResult> results = new ArrayList<>();
        int limit = Math.max(topK, 0);
        for (int index = 0; index < Math.min(limit, ranked.size()); index++) {
            ScoredEntry candidate = ranked.get(index);
            if (candidate.score <= 0.0d) {
                continue;
            }
            SkillSearchResult result = new SkillSearchResult();
            result.setSkillId(stringValue(candidate.entry.get("skill_id"), ""));
            result.setSkillName(stringValue(candidate.entry.get("skill_name"), ""));
            result.setDescription(stringValue(candidate.entry.get("description"), ""));
            Object experienceCount = candidate.entry.get("experience_count");
            result.setExperienceCount(experienceCount instanceof Number number ? number.intValue() : 0);
            result.setKeywords(stringList(candidate.entry.get("keywords")));
            result.setScore(candidate.score);
            results.add(result);
        }
        return CompletableFuture.completedFuture(results);
    }

    private Path packageDir(String skillId) {
        return packagesDir.resolve(skillId);
    }

    private Path packageArchive(String skillId) {
        return packageDir(skillId).resolve("skill.tar.gz");
    }

    private Path packageMetaPath(String skillId) {
        return packageDir(skillId).resolve("meta.json");
    }

    private Path bundleDir(String skillId) {
        return bundlesDir.resolve(skillId);
    }

    private Path indexPath(String skillId) {
        return indexDir.resolve(skillId + ".jsonl");
    }

    private Path globalIndexPath() {
        return indexDir.resolve(GLOBAL_INDEX);
    }

    private Path outboxSkillDir(String skillId) {
        return outboxDir.resolve(skillId);
    }

    private String duplicateRejectionReason(String skillId, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        for (Map<String, Object> entry : readIndex(skillId)) {
            List<String> existing = stringList(entry.get("keywords"));
            double score = jaccard(keywords, existing);
            if (score >= dedupJaccardThreshold) {
                String existingId = stringValue(entry.get("bundle_id"), "?");
                return String.format(
                        Locale.ROOT,
                        "keywords overlap existing bundle %s (jaccard=%.2f, threshold=%.2f)",
                        existingId,
                        score,
                        dedupJaccardThreshold);
            }
        }
        return null;
    }

    private void upsertGlobalIndex(String skillId, SharedSkillBundle bundle) throws IOException {
        List<Map<String, Object>> entries = readGlobalIndex();
        List<String> mergedKeywords = new ArrayList<>(bundle.getKeywordsAggregate());
        int experienceCount = 1;
        String skillName = bundle.getSkillName();
        String description = "";
        int existingIndex = -1;
        for (int index = 0; index < entries.size(); index++) {
            Map<String, Object> entry = entries.get(index);
            if (!Objects.equals(stringValue(entry.get("skill_id"), ""), skillId)) {
                continue;
            }
            for (String keyword : stringList(entry.get("keywords"))) {
                if (!keyword.isEmpty() && !mergedKeywords.contains(keyword)) {
                    mergedKeywords.add(keyword);
                }
            }
            Object existingCount = entry.get("experience_count");
            experienceCount = existingCount instanceof Number number ? number.intValue() + 1 : 1;
            skillName = stringValue(entry.get("skill_name"), skillName);
            description = stringValue(entry.get("description"), description);
            existingIndex = index;
            break;
        }
        if (existingIndex >= 0) {
            entries.remove(existingIndex);
        }

        SkillPackageMeta meta = readPackageMeta(skillId);
        if (meta != null) {
            skillName = meta.getSkillName().isEmpty() ? skillName : meta.getSkillName();
            description = meta.getDescription().isEmpty() ? description : meta.getDescription();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skill_id", skillId);
        payload.put("skill_name", skillName);
        payload.put("description", description);
        payload.put("keywords", mergedKeywords);
        payload.put("experience_count", experienceCount);
        payload.put("updated_at", bundle.getCreatedAt());
        entries.add(payload);
        writeGlobalIndex(entries);
    }

    private void spoolToOutbox(SharedSkillBundle bundle) {
        String skillId = stringValue(bundle.getSkillId(), "").trim();
        if (skillId.isEmpty()) {
            LOGGER.error(
                    "[LocalFileBackend] cannot spool bundle=%s to outbox without skill_id",
                    bundle.getBundleId());
            return;
        }
        try {
            Files.createDirectories(outboxSkillDir(skillId));
            Path outboxFile = outboxSkillDir(skillId).resolve(bundle.getBundleId() + ".json");
            writeJson(outboxFile, bundle.toDict(), true);
        } catch (IOException exception) {
            LOGGER.error(
                    "[LocalFileBackend] outbox spool also failed for bundle=%s: %s",
                    bundle.getBundleId(),
                    exception);
        }
    }

    private SkillPackageMeta readPackageMeta(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return null;
        }
        Path metaPath = packageMetaPath(skillId);
        if (!Files.isRegularFile(metaPath)) {
            return null;
        }
        try {
            return SkillPackageMeta.fromDict(readJsonMap(metaPath));
        } catch (IOException exception) {
            LOGGER.warning("[LocalFileBackend] meta read failed for %s: %s", skillId, exception);
            return null;
        }
    }

    private void ensureGlobalIndexEntry(String skillId, SkillPackageMeta meta) throws IOException {
        List<Map<String, Object>> entries = readGlobalIndex();
        for (Map<String, Object> entry : entries) {
            if (Objects.equals(stringValue(entry.get("skill_id"), ""), skillId)) {
                entry.put("skill_name", meta.getSkillName().isEmpty()
                        ? stringValue(entry.get("skill_name"), "")
                        : meta.getSkillName());
                entry.put("description", meta.getDescription().isEmpty()
                        ? stringValue(entry.get("description"), "")
                        : meta.getDescription());
                writeGlobalIndex(entries);
                return;
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skill_id", skillId);
        payload.put("skill_name", meta.getSkillName());
        payload.put("description", meta.getDescription());
        payload.put("keywords", List.of());
        payload.put("experience_count", 0);
        payload.put("updated_at", meta.getUploadedAt());
        entries.add(payload);
        writeGlobalIndex(entries);
    }

    private List<Map<String, Object>> readIndex(String skillId) {
        Path path = indexPath(skillId);
        if (!Files.isRegularFile(path)) {
            return new ArrayList<>();
        }
        return readJsonLines(path, "[LocalFileBackend] index corrupt at %s:%d (%s); skipping line");
    }

    private List<Map<String, Object>> readGlobalIndex() {
        Path path = globalIndexPath();
        if (!Files.isRegularFile(path)) {
            return new ArrayList<>();
        }
        try {
            return readJsonLines(path, null);
        } catch (RuntimeException exception) {
            LOGGER.warning("[LocalFileBackend] global index read failed: %s", exception);
            return new ArrayList<>();
        }
    }

    private void writeGlobalIndex(List<Map<String, Object>> entries) throws IOException {
        Files.createDirectories(indexDir);
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            lines.add(writeJsonString(entry, false));
        }
        String content = lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
        Files.writeString(globalIndexPath(), content, StandardCharsets.UTF_8);
    }

    private SharedSkillBundle loadBundle(String skillId, String bundleId) {
        if (bundleId == null || bundleId.isBlank()) {
            return null;
        }
        Path bundleFile = bundleDir(skillId).resolve(bundleId + ".json");
        if (!Files.isRegularFile(bundleFile)) {
            LOGGER.debug("[LocalFileBackend] bundle file missing: %s", bundleFile);
            return null;
        }
        try {
            return SharedSkillBundle.fromDict(readJsonMap(bundleFile));
        } catch (IOException exception) {
            LOGGER.warning("[LocalFileBackend] bundle load failed for %s: %s", bundleFile, exception);
            return null;
        } catch (RuntimeException exception) {
            LOGGER.warning("[LocalFileBackend] bundle decode failed for %s: %s", bundleFile, exception);
            return null;
        }
    }

    private static Path resolvePath(String path) {
        String resolved = path == null ? DEFAULT_HUB_PATH : path;
        if (resolved.startsWith("~")) {
            resolved = System.getProperty("user.home") + resolved.substring(1);
        }
        return Paths.get(resolved).toAbsolutePath().normalize();
    }

    private static double jaccard(List<String> left, List<String> right) {
        Set<String> leftSet = lowerCaseSet(left);
        Set<String> rightSet = lowerCaseSet(right);
        if (leftSet.isEmpty() && rightSet.isEmpty()) {
            return 0.0d;
        }
        Set<String> intersection = new LinkedHashSet<>(leftSet);
        intersection.retainAll(rightSet);
        Set<String> union = new LinkedHashSet<>(leftSet);
        union.addAll(rightSet);
        if (!intersection.isEmpty()) {
            return (double) intersection.size() / union.size();
        }
        for (String leftKeyword : leftSet) {
            for (String rightKeyword : rightSet) {
                if (leftKeyword.contains(rightKeyword) || rightKeyword.contains(leftKeyword)) {
                    return 0.5d / Math.max(union.size(), 1);
                }
            }
        }
        return 0.0d;
    }

    private static Set<String> lowerCaseSet(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                result.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static List<String> stringList(Object value) {
        return stringListInternal(value);
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String resolved = String.valueOf(value);
        return resolved.isEmpty() ? fallback : resolved;
    }

    private static List<String> stringListInternal(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        }
        return result;
    }

    private static void writeJson(Path path, Map<String, Object> payload, boolean pretty) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, writeJsonString(payload, pretty), StandardCharsets.UTF_8);
    }

    private static String writeJsonString(Map<String, Object> payload, boolean pretty) throws JsonProcessingException {
        return pretty
                ? OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
                : OBJECT_MAPPER.writeValueAsString(payload);
    }

    private static Map<String, Object> readJsonMap(Path path) throws IOException {
        return OBJECT_MAPPER.readValue(Files.readString(path, StandardCharsets.UTF_8), MAP_TYPE);
    }

    private static void appendJsonLine(Path path, Map<String, Object> payload) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(
                path,
                writeJsonString(payload, false) + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private static List<Map<String, Object>> readJsonLines(Path path, String corruptMessageTemplate) {
        List<Map<String, Object>> entries = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index).trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    entries.add(OBJECT_MAPPER.readValue(line, MAP_TYPE));
                } catch (JsonProcessingException exception) {
                    if (corruptMessageTemplate != null) {
                        LOGGER.warning(corruptMessageTemplate, path, index + 1, exception);
                    }
                }
            }
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        return entries;
    }

    private static UploadResult uploadFailure(String reason, boolean retryable) {
        UploadResult result = new UploadResult();
        result.setOk(false);
        result.setReason(reason);
        result.setRetryable(retryable);
        return result;
    }

    private static final class ScoredEntry {
        private final double score;
        private final Map<String, Object> entry;

        private ScoredEntry(double score, Map<String, Object> entry) {
            this.score = score;
            this.entry = entry;
        }
    }
}
