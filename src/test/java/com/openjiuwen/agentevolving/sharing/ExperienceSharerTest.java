/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.sharing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.sharing.backends.SharingBackend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExperienceSharerTest {

    @TempDir
    Path tempDir;

    @Test
    void flushPendingUploadsUploadsInitialPackageOnceAndDeduplicatesRecordIds() {
        FakeSharingBackend backend = new FakeSharingBackend();
        ExperienceSharer sharer = new ExperienceSharer(
                backend,
                tempDir.resolve("cache").toString(),
                3,
                0.0d,
                skillName -> CompletableFuture.completedFuture(
                        new SkillSharingContext("sk_python_debug", new byte[] {1, 2, 3}, skillName, "debug"))
        );

        SharedExperience experience = experience("user_correction", List.of("IndexError", "bounds"), "check loop upper bound");
        sharer.stageForUpload("python-debug", experience);
        sharer.stageForUpload("python-debug", experience);

        UploadResult first = sharer.flushPendingUploads("python-debug").toCompletableFuture().join();
        SharedExperience secondExperience = experience("execution_failure", List.of("traceback"), "investigate traceback");
        sharer.stageForUpload("python-debug", secondExperience);
        UploadResult second = sharer.flushPendingUploads("python-debug").toCompletableFuture().join();

        assertTrue(first.isOk());
        assertTrue(second.isOk());
        assertEquals(2, backend.bundleUploadCount);
        assertEquals(1, backend.packageUploadCount);
        assertEquals(1, backend.uploadedBundles.get(0).getExperiences().size());
        assertArrayEquals(new byte[] {1, 2, 3}, backend.downloadSkillPackage("sk_python_debug").toCompletableFuture().join());
        assertFalse(sharer.hasPending("python-debug"));
    }

    @Test
    void flushPendingUploadsReturnsFailureWhenSkillIdUnavailable() {
        FakeSharingBackend backend = new FakeSharingBackend();
        ExperienceSharer sharer = new ExperienceSharer(backend, null, 3, 0.0d, null);
        sharer.stageForUpload("python-debug", experience("user_correction", List.of("IndexError"), "summary"));

        UploadResult result = sharer.flushPendingUploads("python-debug").toCompletableFuture().join();

        assertFalse(result.isOk());
        assertEquals("skill_id unavailable", result.getReason());
        assertEquals(0, backend.bundleUploadCount);
    }

    @Test
    void flushPendingUploadsReturnsNonRetryableBackendFailure() {
        FakeSharingBackend backend = new FakeSharingBackend();
        backend.nextUploadResult = uploadResult(false, "", "overlap existing bundle", false);
        ExperienceSharer sharer = new ExperienceSharer(
                backend,
                null,
                3,
                0.0d,
                skillName -> CompletableFuture.completedFuture(
                        new SkillSharingContext("sk_python_debug", new byte[] {9}, skillName, "debug"))
        );
        sharer.stageForUpload("python-debug", experience("user_correction", List.of("IndexError"), "summary"));

        UploadResult result = sharer.flushPendingUploads("python-debug").toCompletableFuture().join();

        assertFalse(result.isOk());
        assertEquals("overlap existing bundle", result.getReason());
        assertEquals(1, backend.bundleUploadCount);
    }

    @Test
    void downloadRelevantMirrorsBundlesAndListCachedBundlesRoundTrip() throws Exception {
        FakeSharingBackend backend = new FakeSharingBackend();
        SharedSkillBundle bundle = SharedSkillBundle.make(
                "python-debug",
                List.of(experience("user_correction", List.of("IndexError", "bounds"), "check loop upper bound")));
        bundle.setSkillId("sk_python_debug");
        backend.downloadResults = List.of(bundle);
        ExperienceSharer sharer = new ExperienceSharer(backend, tempDir.resolve("cache").toString());

        List<SharedSkillBundle> downloaded = sharer.downloadRelevant(
                "sk_python_debug",
                query(List.of("IndexError"), "debug"),
                3,
                "python-debug"
        ).toCompletableFuture().join();
        List<SharedSkillBundle> cached = sharer.listCachedBundles("sk_python_debug");

        assertEquals(1, downloaded.size());
        assertEquals(bundle.getBundleId(), downloaded.get(0).getBundleId());
        assertEquals(1, cached.size());
        assertEquals(bundle.getBundleId(), cached.get(0).getBundleId());
        assertTrue(Files.isRegularFile(
                tempDir.resolve("cache")
                        .resolve("downloaded")
                        .resolve("sk_python_debug")
                        .resolve(bundle.getBundleId() + ".json")));
    }

    @Test
    void searchAndPackageHelpersReturnBackendData() {
        FakeSharingBackend backend = new FakeSharingBackend();
        SkillPackageMeta meta = new SkillPackageMeta();
        meta.setSkillId("sk_python_debug");
        meta.setSkillName("python-debug");
        backend.packageMetaBySkillId.put("sk_python_debug", meta);
        backend.packageBytesBySkillId.put("sk_python_debug", new byte[] {4, 5});
        SkillSearchResult result = new SkillSearchResult();
        result.setSkillId("sk_python_debug");
        result.setSkillName("python-debug");
        result.setScore(0.8d);
        backend.searchResults = List.of(result);
        ExperienceSharer sharer = new ExperienceSharer(backend, null);

        List<SkillSearchResult> results = sharer.searchSkills(query(List.of("python"), "debug"), 5)
                .toCompletableFuture()
                .join();
        SkillPackageMeta restoredMeta = sharer.getSkillPackageMeta("sk_python_debug").toCompletableFuture().join();
        byte[] restoredBytes = sharer.downloadSkillPackage("sk_python_debug").toCompletableFuture().join();

        assertEquals(1, results.size());
        assertEquals("sk_python_debug", results.get(0).getSkillId());
        assertEquals("python-debug", restoredMeta.getSkillName());
        assertArrayEquals(new byte[] {4, 5}, restoredBytes);
    }

    private static QueryKeywords query(List<String> keywords, String intent) {
        QueryKeywords query = new QueryKeywords();
        query.setKeywords(keywords);
        query.setIntent(intent);
        return query;
    }

    private static SharedExperience experience(String source, List<String> keywords, String summary) {
        SharedExperience experience = new SharedExperience();
        experience.setRecord(EvolutionRecord.make(
                source,
                "ctx",
                EvolutionPatch.builder()
                        .section("Troubleshooting")
                        .action("append")
                        .content("## Fix\n- check bounds")
                        .keywords(keywords)
                        .summary(summary)
                        .build(),
                0.8d,
                null,
                summary));
        experience.setKeywords(keywords);
        experience.setSummary(summary);
        return experience;
    }

    private static UploadResult uploadResult(boolean ok, String bundleId, String reason, boolean retryable) {
        UploadResult result = new UploadResult();
        result.setOk(ok);
        result.setBundleId(bundleId);
        result.setReason(reason);
        result.setRetryable(retryable);
        return result;
    }

    private static final class FakeSharingBackend implements SharingBackend {
        private final List<SharedSkillBundle> uploadedBundles = new ArrayList<>();
        private final Map<String, byte[]> packageBytesBySkillId = new HashMap<>();
        private final Map<String, SkillPackageMeta> packageMetaBySkillId = new HashMap<>();
        private List<SharedSkillBundle> downloadResults = List.of();
        private List<SkillSearchResult> searchResults = List.of();
        private UploadResult nextUploadResult;
        private int packageUploadCount;
        private int bundleUploadCount;

        @Override
        public CompletionStage<UploadResult> uploadBundle(SharedSkillBundle bundle) {
            bundleUploadCount += 1;
            uploadedBundles.add(bundle);
            if (nextUploadResult != null) {
                UploadResult result = nextUploadResult;
                nextUploadResult = null;
                return CompletableFuture.completedFuture(result);
            }
            UploadResult result = new UploadResult();
            result.setOk(true);
            result.setBundleId(bundle.getBundleId());
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletionStage<List<SharedSkillBundle>> downloadBundles(String skillId, QueryKeywords query, int topK) {
            return CompletableFuture.completedFuture(downloadResults);
        }

        @Override
        public CompletionStage<Boolean> hasSkillPackage(String skillId) {
            return CompletableFuture.completedFuture(packageBytesBySkillId.containsKey(skillId));
        }

        @Override
        public CompletionStage<Void> uploadSkillPackage(String skillId, byte[] packageBytes, SkillPackageMeta meta) {
            packageUploadCount += 1;
            packageBytesBySkillId.put(skillId, packageBytes.clone());
            packageMetaBySkillId.put(skillId, meta);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<byte[]> downloadSkillPackage(String skillId) {
            byte[] bytes = packageBytesBySkillId.get(skillId);
            return CompletableFuture.completedFuture(bytes == null ? null : bytes.clone());
        }

        @Override
        public CompletionStage<SkillPackageMeta> getSkillPackageMeta(String skillId) {
            return CompletableFuture.completedFuture(packageMetaBySkillId.get(skillId));
        }

        @Override
        public CompletionStage<List<SkillSearchResult>> searchSkills(QueryKeywords query, int topK) {
            return CompletableFuture.completedFuture(searchResults);
        }
    }
}
