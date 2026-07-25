/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.sharing.backends;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.sharing.QueryKeywords;
import com.openjiuwen.agent_evolving.sharing.SharedExperience;
import com.openjiuwen.agent_evolving.sharing.SharedSkillBundle;
import com.openjiuwen.agent_evolving.sharing.SkillPackageMeta;
import com.openjiuwen.agent_evolving.sharing.SkillSearchResult;
import com.openjiuwen.agent_evolving.sharing.UploadResult;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileBackendTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadAndDownloadBundlePreserveSkillScopedIndex() {
        LocalFileBackend backend = new LocalFileBackend(tempDir.resolve("hub"));
        String skillId = "sk_testupload01";
        SharedSkillBundle bundle = bundle(skillId, "python-debug", "user_correction",
                List.of("IndexError", "bounds"), "check loop upper bound");

        SkillPackageMeta meta = new SkillPackageMeta();
        meta.setSkillId(skillId);
        meta.setSkillName("python-debug");
        meta.setDescription("debug");
        backend.uploadSkillPackage(skillId, "package-a".getBytes(), meta).toCompletableFuture().join();

        UploadResult uploadResult = backend.uploadBundle(bundle).toCompletableFuture().join();
        List<SharedSkillBundle> results = backend.downloadBundles(
                skillId,
                query(List.of("IndexError", "bounds"), "debug"),
                3
        ).toCompletableFuture().join();

        assertTrue(uploadResult.isOk());
        assertEquals(bundle.getBundleId(), uploadResult.getBundleId());
        assertEquals(1, results.size());
        assertEquals(bundle.getBundleId(), results.get(0).getBundleId());
        assertTrue(backend.hasSkillPackage(skillId).toCompletableFuture().join());
        assertEquals("python-debug", backend.getSkillPackageMeta(skillId).toCompletableFuture().join().getSkillName());
    }

    @Test
    void differentSkillIdsDoNotCollide() {
        LocalFileBackend backend = new LocalFileBackend(tempDir.resolve("hub"));
        SharedSkillBundle first = bundle(
                "sk_testskillid01",
                "ppt-creator",
                "user_correction",
                List.of("layout", "slide"),
                "fix slide layout");
        SharedSkillBundle second = bundle(
                "sk_testskillid02",
                "ppt-creator",
                "execution_failure",
                List.of("font", "theme"),
                "fix font theme");

        assertTrue(backend.uploadBundle(first).toCompletableFuture().join().isOk());
        assertTrue(backend.uploadBundle(second).toCompletableFuture().join().isOk());

        List<SharedSkillBundle> firstResults = backend.downloadBundles(
                "sk_testskillid01",
                query(List.of("layout", "slide"), "ppt"),
                3
        ).toCompletableFuture().join();
        List<SharedSkillBundle> secondResults = backend.downloadBundles(
                "sk_testskillid02",
                query(List.of("font", "theme"), "ppt"),
                3
        ).toCompletableFuture().join();

        assertEquals(1, firstResults.size());
        assertEquals(1, secondResults.size());
        assertEquals(first.getBundleId(), firstResults.get(0).getBundleId());
        assertEquals(second.getBundleId(), secondResults.get(0).getBundleId());
    }

    @Test
    void uploadSkillPackageIsImmutable() {
        LocalFileBackend backend = new LocalFileBackend(tempDir.resolve("hub"));
        String skillId = "sk_immutable001";
        byte[] first = "package-a".getBytes();
        byte[] second = "package-b".getBytes();
        SkillPackageMeta meta = new SkillPackageMeta();
        meta.setSkillId(skillId);
        meta.setSkillName("demo");
        meta.setDescription("demo");

        backend.uploadSkillPackage(skillId, first, meta).toCompletableFuture().join();
        backend.uploadSkillPackage(skillId, second, meta).toCompletableFuture().join();

        byte[] downloaded = backend.downloadSkillPackage(skillId).toCompletableFuture().join();

        assertNotNull(downloaded);
        assertArrayEquals(first, downloaded);
    }

    @Test
    void uploadBundleRejectsDuplicateKeywordsAtConfiguredThreshold() {
        LocalFileBackend backend = new LocalFileBackend(tempDir.resolve("hub"));
        SharedSkillBundle bundle = bundle(
                "sk_duplicate001",
                "python-debug",
                "user_correction",
                List.of("IndexError", "bounds", "loop"),
                "check loop upper bound");
        SharedSkillBundle duplicate = bundle(
                "sk_duplicate001",
                "python-debug",
                "user_correction",
                List.of("IndexError", "bounds", "loop"),
                "another attempt");

        UploadResult first = backend.uploadBundle(bundle).toCompletableFuture().join();
        UploadResult rejected = backend.uploadBundle(duplicate).toCompletableFuture().join();

        assertTrue(first.isOk());
        assertFalse(rejected.isOk());
        assertTrue(rejected.getReason().contains("overlap existing bundle"));
    }

    @Test
    void searchSkillsUsesGlobalIndexWithPackageMetadata() {
        LocalFileBackend backend = new LocalFileBackend(tempDir.resolve("hub"));
        String skillId = "sk_search001";
        SharedSkillBundle bundle = bundle(
                skillId,
                "ppt-creator",
                "user_correction",
                List.of("ppt", "slide", "layout"),
                "fix slide layout");
        SkillPackageMeta meta = new SkillPackageMeta();
        meta.setSkillId(skillId);
        meta.setSkillName("ppt-creator");
        meta.setDescription("Create presentations");

        backend.uploadSkillPackage(skillId, "package".getBytes(), meta).toCompletableFuture().join();
        assertTrue(backend.uploadBundle(bundle).toCompletableFuture().join().isOk());

        List<SkillSearchResult> results = backend.searchSkills(
                query(List.of("ppt", "slide"), "presentation"),
                3
        ).toCompletableFuture().join();

        assertEquals(1, results.size());
        assertEquals(skillId, results.get(0).getSkillId());
        assertEquals("ppt-creator", results.get(0).getSkillName());
        assertEquals("Create presentations", results.get(0).getDescription());
        assertEquals(1, results.get(0).getExperienceCount());
    }

    private static QueryKeywords query(List<String> keywords, String intent) {
        QueryKeywords query = new QueryKeywords();
        query.setKeywords(keywords);
        query.setIntent(intent);
        return query;
    }

    private static SharedSkillBundle bundle(
            String skillId,
            String skillName,
            String source,
            List<String> keywords,
            String summary
    ) {
        SharedExperience experience = new SharedExperience();
        experience.setRecord(record(source));
        experience.setKeywords(keywords);
        experience.setSummary(summary);

        SharedSkillBundle bundle = SharedSkillBundle.make(skillName, List.of(experience));
        bundle.setSkillId(skillId);
        return bundle;
    }

    private static EvolutionRecord record(String source) {
        EvolutionPatch patch = EvolutionPatch.builder()
                .section("Troubleshooting")
                .action("append")
                .content("## Fix\n- check bounds")
                .keywords(List.of("IndexError", "bounds"))
                .summary("check loop upper bound")
                .build();
        return EvolutionRecord.make(source, "ctx", patch, 0.8d, null, "check loop upper bound");
    }
}
