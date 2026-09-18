/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.sharing.backends.SharingBackend;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Skill-scoped facade for the sharing path.
 *
 * <p>Mirrors Python's {@code ExperienceSharer} in
 * {@code openjiuwen/agent_evolving/sharing/experience_sharer.py}.</p>
 */
public class ExperienceSharer {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int DEFAULT_UPLOAD_RETRIES = 3;
    private static final double DEFAULT_BACKOFF_SECS = 0.5d;

    private final SharingBackend backend;
    private final Path localCacheDir;
    private final int maxUploadRetries;
    private final double backoffBaseSecs;
    private final Map<String, List<SharedExperience>> pendingUploads = new HashMap<>();
    private final Map<String, Set<PendingKey>> pendingKeys = new HashMap<>();
    private final Object lock = new Object();

    private SkillSharingContextProvider skillSharingContextProvider;

    public ExperienceSharer(SharingBackend backend) {
        this(backend, null, DEFAULT_UPLOAD_RETRIES, DEFAULT_BACKOFF_SECS, null);
    }

    public ExperienceSharer(SharingBackend backend, String localCacheDir) {
        this(backend, localCacheDir, DEFAULT_UPLOAD_RETRIES, DEFAULT_BACKOFF_SECS, null);
    }

    public ExperienceSharer(SharingBackend backend, String localCacheDir, SkillSharingContextProvider provider) {
        this(backend, localCacheDir, DEFAULT_UPLOAD_RETRIES, DEFAULT_BACKOFF_SECS, provider);
    }

    public ExperienceSharer(
            SharingBackend backend,
            String localCacheDir,
            int maxUploadRetries,
            double backoffBaseSecs,
            SkillSharingContextProvider provider
    ) {
        this.backend = backend;
        this.localCacheDir = expand(localCacheDir);
        this.maxUploadRetries = Math.max(maxUploadRetries, 1);
        this.backoffBaseSecs = Math.max(backoffBaseSecs, 0.0d);
        this.skillSharingContextProvider = provider;
    }

    public void setSkillSharingContextProvider(SkillSharingContextProvider provider) {
        this.skillSharingContextProvider = provider;
    }

    public SharingBackend getBackend() {
        return backend;
    }

    public Path getLocalCacheDir() {
        return localCacheDir;
    }

    public CompletionStage<String> resolveSkillId(String skillName) {
        SkillSharingContextProvider provider = skillSharingContextProvider;
        if (provider == null || isBlank(skillName)) {
            return CompletableFuture.completedFuture("");
        }
        try {
            SkillSharingContext context = provider.provide(skillName).toCompletableFuture().join();
            return CompletableFuture.completedFuture(context == null ? "" : trimToEmpty(context.getSkillId()));
        } catch (Exception exception) {
            LOGGER.warning(
                    "[ExperienceSharer] resolve_skill_id failed for skill=%s: %s",
                    skillName,
                    exception);
            return CompletableFuture.completedFuture("");
        }
    }

    public boolean hasPending(String skillName) {
        synchronized (lock) {
            return !pendingUploads.getOrDefault(skillName, List.of()).isEmpty();
        }
    }

    public void stageForUpload(String skillName, SharedExperience experience) {
        if (isBlank(skillName) || experience == null) {
            return;
        }
        String recordId = experience.getRecord() == null ? "" : trimToEmpty(experience.getRecord().getId());
        PendingKey key = new PendingKey(skillName, recordId);
        synchronized (lock) {
            Set<PendingKey> keys = pendingKeys.computeIfAbsent(skillName, ignored -> new HashSet<>());
            if (keys.contains(key)) {
                LOGGER.debug(
                        "[ExperienceSharer] stage_for_upload deduplicated skill=%s record=%s",
                        skillName,
                        recordId);
                return;
            }
            keys.add(key);
            pendingUploads.computeIfAbsent(skillName, ignored -> new ArrayList<>()).add(experience);
            LOGGER.debug(
                    "[ExperienceSharer] staged 1 experience for skill=%s (queue=%d)",
                    skillName,
                    pendingUploads.get(skillName).size());
        }
    }

    public int discardPendingUploads(String skillName) {
        int count;
        synchronized (lock) {
            count = pendingUploads.getOrDefault(skillName, List.of()).size();
            pendingUploads.remove(skillName);
            pendingKeys.remove(skillName);
        }
        if (count > 0) {
            LOGGER.info(
                    "[ExperienceSharer] discarded %d pending experience(s) for skill=%s",
                    count,
                    skillName);
        }
        return count;
    }

    public CompletionStage<UploadResult> flushPendingUploads(String skillName) {
        List<SharedExperience> experiences;
        synchronized (lock) {
            experiences = pendingUploads.remove(skillName);
            pendingKeys.remove(skillName);
        }
        if (experiences == null || experiences.isEmpty()) {
            UploadResult result = new UploadResult();
            result.setOk(true);
            return CompletableFuture.completedFuture(result);
        }

        SharedSkillBundle bundle = SharedSkillBundle.make(skillName, experiences);
        syncSkillPackage(bundle, skillName);
        if (isBlank(bundle.getSkillId())) {
            String reason = "skill_id unavailable";
            LOGGER.warning(
                    "[ExperienceSharer] skipping upload for skill=%s: %s",
                    skillName,
                    reason);
            UploadResult result = new UploadResult();
            result.setOk(false);
            result.setReason(reason);
            return CompletableFuture.completedFuture(result);
        }

        int attempt = 0;
        UploadResult lastResult = uploadFailure("upload not attempted");
        while (attempt < maxUploadRetries) {
            attempt += 1;
            UploadResult result = backend.uploadBundle(bundle).toCompletableFuture().join();
            if (result.isOk()) {
                mirrorBundle(bundle, "uploaded");
                LOGGER.info(
                        "[ExperienceSharer] flushed bundle %s for skill=%s id=%s after %d attempt(s)",
                        isBlank(result.getBundleId()) ? bundle.getBundleId() : result.getBundleId(),
                        skillName,
                        bundle.getSkillId(),
                        attempt);
                return CompletableFuture.completedFuture(result);
            }

            lastResult = result;
            LOGGER.warning(
                    "[ExperienceSharer] upload attempt %d/%d rejected for skill=%s id=%s: %s",
                    attempt,
                    maxUploadRetries,
                    skillName,
                    bundle.getSkillId(),
                    result.getReason());
            if (!result.isRetryable()) {
                return CompletableFuture.completedFuture(result);
            }
            if (attempt < maxUploadRetries && backoffBaseSecs > 0.0d) {
                try {
                    Thread.sleep((long) (backoffBaseSecs * 1000 * Math.pow(2, attempt - 1)));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        LOGGER.error(
                "[ExperienceSharer] giving up upload for skill=%s id=%s after %d attempts (%s)",
                skillName,
                bundle.getSkillId(),
                maxUploadRetries,
                lastResult.getReason());
        return CompletableFuture.completedFuture(lastResult);
    }

    public CompletionStage<List<SharedSkillBundle>> downloadRelevant(
            String skillId,
            QueryKeywords query,
            int topK,
            String skillName
    ) {
        String resolvedId = trimToEmpty(skillId);
        if (resolvedId.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        try {
            List<SharedSkillBundle> bundles = backend.downloadBundles(resolvedId, query, topK).toCompletableFuture().join();
            for (SharedSkillBundle bundle : bundles) {
                mirrorBundle(bundle, "downloaded");
            }
            if (!bundles.isEmpty()) {
                SharedSkillBundle first = bundles.get(0);
                LOGGER.info(
                        "[ExperienceSharer] downloaded %d bundle(s) for skill=%s id=%s (top_k=%d)",
                        bundles.size(),
                        !isBlank(skillName) ? skillName : (!isBlank(first.getSkillName()) ? first.getSkillName() : "?"),
                        resolvedId,
                        topK);
            }
            return CompletableFuture.completedFuture(bundles);
        } catch (Exception exception) {
            LOGGER.warning(
                    "[ExperienceSharer] backend download failed for skill=%s id=%s: %s",
                    !isBlank(skillName) ? skillName : "?",
                    resolvedId,
                    exception);
            return CompletableFuture.completedFuture(List.of());
        }
    }

    public CompletionStage<List<SkillSearchResult>> searchSkills(QueryKeywords query, int topK) {
        try {
            return CompletableFuture.completedFuture(backend.searchSkills(query, topK).toCompletableFuture().join());
        } catch (Exception exception) {
            LOGGER.warning("[ExperienceSharer] search_skills failed: %s", exception);
            return CompletableFuture.completedFuture(List.of());
        }
    }

    public CompletionStage<byte[]> downloadSkillPackage(String skillId) {
        String resolvedId = trimToEmpty(skillId);
        if (resolvedId.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return CompletableFuture.completedFuture(backend.downloadSkillPackage(resolvedId).toCompletableFuture().join());
        } catch (Exception exception) {
            LOGGER.warning(
                    "[ExperienceSharer] download_skill_package failed for skill_id=%s: %s",
                    resolvedId,
                    exception);
            return CompletableFuture.completedFuture(null);
        }
    }

    public CompletionStage<SkillPackageMeta> getSkillPackageMeta(String skillId) {
        String resolvedId = trimToEmpty(skillId);
        try {
            return CompletableFuture.completedFuture(backend.getSkillPackageMeta(resolvedId).toCompletableFuture().join());
        } catch (Exception exception) {
            LOGGER.warning(
                    "[ExperienceSharer] get_skill_package_meta failed for skill_id=%s: %s",
                    skillId,
                    exception);
            return CompletableFuture.completedFuture(null);
        }
    }

    public List<SharedSkillBundle> listCachedBundles(String skillId) {
        String resolvedId = trimToEmpty(skillId);
        if (localCacheDir == null || resolvedId.isEmpty()) {
            return List.of();
        }
        Path skillDir = localCacheDir.resolve("downloaded").resolve(resolvedId);
        if (!Files.isDirectory(skillDir)) {
            return List.of();
        }
        List<SharedSkillBundle> bundles = new ArrayList<>();
        try (var stream = Files.list(skillDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(bundleFile -> {
                        try {
                            Map<String, Object> data = OBJECT_MAPPER.readValue(
                                    Files.readString(bundleFile, StandardCharsets.UTF_8),
                                    MAP_TYPE);
                            bundles.add(SharedSkillBundle.fromDict(data));
                        } catch (IOException | RuntimeException exception) {
                            LOGGER.warning(
                                    "[ExperienceSharer] cached bundle decode failed for %s: %s",
                                    bundleFile,
                                    exception);
                        }
                    });
        } catch (IOException exception) {
            LOGGER.warning(
                    "[ExperienceSharer] cached bundle decode failed for %s: %s",
                    skillDir,
                    exception);
        }
        return bundles;
    }

    private void syncSkillPackage(SharedSkillBundle bundle, String skillName) {
        SkillSharingContextProvider provider = skillSharingContextProvider;
        if (provider == null) {
            return;
        }

        SkillSharingContext context;
        try {
            context = provider.provide(skillName).toCompletableFuture().join();
        } catch (Exception exception) {
            LOGGER.warning(
                    "[ExperienceSharer] skill_sharing_context_provider failed for skill=%s: %s",
                    skillName,
                    exception);
            return;
        }
        if (context == null) {
            return;
        }

        String skillId = trimToEmpty(context.getSkillId());
        if (skillId.isEmpty()) {
            LOGGER.debug(
                    "[ExperienceSharer] skill_sharing_context_provider returned empty skill_id for skill=%s",
                    skillName);
            return;
        }

        bundle.setSkillId(skillId);
        if (!isBlank(context.getSkillName())) {
            bundle.setSkillName(context.getSkillName());
        }

        boolean alreadyPresent;
        try {
            alreadyPresent = backend.hasSkillPackage(skillId).toCompletableFuture().join();
        } catch (Exception exception) {
            LOGGER.warning(
                    "[ExperienceSharer] backend.has_skill_package failed for skill_id=%s: %s",
                    skillId,
                    exception);
            return;
        }
        if (alreadyPresent) {
            LOGGER.debug("[ExperienceSharer] hub already has skill package for skill_id=%s", skillId);
            return;
        }

        byte[] packageBytes = context.getPackageBytes();
        if (packageBytes.length == 0) {
            LOGGER.warning(
                    "[ExperienceSharer] empty skill package for skill=%s id=%s; skipping package upload",
                    skillName,
                    skillId);
            return;
        }

        SkillPackageMeta meta = new SkillPackageMeta();
        meta.setSkillId(skillId);
        meta.setSkillName(!isBlank(context.getSkillName()) ? context.getSkillName() : skillName);
        meta.setDescription(context.getDescription());
        try {
            backend.uploadSkillPackage(skillId, packageBytes, meta).toCompletableFuture().join();
            LOGGER.info(
                    "[ExperienceSharer] uploaded initial skill package for skill=%s id=%s",
                    skillName,
                    skillId);
        } catch (Exception exception) {
            LOGGER.warning(
                    "[ExperienceSharer] upload_skill_package failed for skill_id=%s: %s; bundle upload will continue",
                    skillId,
                    exception);
        }
    }

    private void mirrorBundle(SharedSkillBundle bundle, String kind) {
        String skillId = trimToEmpty(bundle.getSkillId());
        if (localCacheDir == null || isBlank(bundle.getBundleId()) || skillId.isEmpty()) {
            return;
        }
        if (!Objects.equals(kind, "uploaded") && !Objects.equals(kind, "downloaded")) {
            throw new IllegalArgumentException("unsupported mirror kind: " + kind);
        }
        Path targetDir = localCacheDir.resolve(kind).resolve(skillId);
        try {
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(bundle.getBundleId() + ".json");
            Files.writeString(
                    targetFile,
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(bundle.toDict()),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warning(
                    "[ExperienceSharer] mirror %s failed for bundle=%s: %s",
                    kind,
                    bundle.getBundleId(),
                    exception);
        }
    }

    private static Path expand(String path) {
        if (path == null) {
            return null;
        }
        String resolved = path;
        if (resolved.startsWith("~")) {
            resolved = System.getProperty("user.home") + resolved.substring(1);
        }
        return Paths.get(resolved).toAbsolutePath().normalize();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static UploadResult uploadFailure(String reason) {
        UploadResult result = new UploadResult();
        result.setOk(false);
        result.setReason(reason);
        return result;
    }

    private record PendingKey(String skillName, String recordId) {
    }
}
