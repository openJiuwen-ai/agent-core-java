/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing;

import com.openjiuwen.agentevolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agentevolving.checkpointing.EvolutionStore;
import com.openjiuwen.agentevolving.checkpointing.SkillPackage;
import com.openjiuwen.agentevolving.sharing.backends.LocalFileBackend;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_evolving.sharing.test_sharing_module} in
 * {@code tests/unit_tests/agent_evolving/sharing/test_sharing_module.py}.
 */
class SharingModulePythonParityTest {

    @TempDir
    Path tempDir;

    @Test
    void localFileBackendUploadAndDownload() throws Exception {
        LocalFileBackend backend = new LocalFileBackend(tempDir.resolve("hub"));
        String skillId = "sk_testupload01";
        SharedSkillBundle bundle = bundle(
                skillId,
                "python-debug",
                List.of(sharedExperience(makeRecord(), List.of("IndexError", "bounds"), "check loop upper bound")));
        byte[] packageBytes = SkillPackage.packSkillDirectory(writeSkillDir(tempDir, "python-debug"));
        backend.uploadSkillPackage(skillId, packageBytes, packageMeta(skillId, "python-debug", "debug"))
                .toCompletableFuture()
                .join();

        UploadResult uploadResult = backend.uploadBundle(bundle).toCompletableFuture().join();
        List<SharedSkillBundle> results = backend.downloadBundles(
                skillId,
                query(List.of("IndexError", "bounds"), "debug"),
                3).toCompletableFuture().join();

        assertThat(uploadResult.isOk()).isTrue();
        assertThat(uploadResult.getBundleId()).isEqualTo(bundle.getBundleId());
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getBundleId()).isEqualTo(bundle.getBundleId());
    }

    @Test
    void differentSkillIdsDoNotCollide() {
        LocalFileBackend backend = new LocalFileBackend(tempDir.resolve("hub"));
        SharedSkillBundle first = bundle(
                "sk_testskillid01",
                "ppt-creator",
                List.of(sharedExperience(makeRecord(), List.of("layout", "slide"), "fix slide layout")));
        SharedSkillBundle second = bundle(
                "sk_testskillid02",
                "ppt-creator",
                List.of(sharedExperience(
                        makeRecord("execution_failure", 0.8),
                        List.of("font", "theme"),
                        "fix font theme")));

        assertThat(backend.uploadBundle(first).toCompletableFuture().join().isOk()).isTrue();
        assertThat(backend.uploadBundle(second).toCompletableFuture().join().isOk()).isTrue();

        List<SharedSkillBundle> firstResults = backend.downloadBundles(
                "sk_testskillid01",
                query(List.of("layout", "slide"), "ppt"),
                3).toCompletableFuture().join();
        List<SharedSkillBundle> secondResults = backend.downloadBundles(
                "sk_testskillid02",
                query(List.of("font", "theme"), "ppt"),
                3).toCompletableFuture().join();

        assertThat(firstResults).hasSize(1);
        assertThat(secondResults).hasSize(1);
        assertThat(firstResults.get(0).getBundleId()).isEqualTo(first.getBundleId());
        assertThat(secondResults.get(0).getBundleId()).isEqualTo(second.getBundleId());
    }

    @Test
    void skillPackageUploadIsImmutable() throws Exception {
        LocalFileBackend backend = new LocalFileBackend(tempDir.resolve("hub"));
        String skillId = "sk_immutable001";
        byte[] first = SkillPackage.packSkillDirectory(writeSkillDir(tempDir, "demo-a", "# A\n"));
        byte[] second = SkillPackage.packSkillDirectory(writeSkillDir(tempDir, "demo-b", "# B\n"));
        SkillPackageMeta meta = packageMeta(skillId, "demo", "demo");

        backend.uploadSkillPackage(skillId, first, meta).toCompletableFuture().join();
        backend.uploadSkillPackage(skillId, second, meta).toCompletableFuture().join();

        byte[] downloaded = backend.downloadSkillPackage(skillId).toCompletableFuture().join();

        assertThat(downloaded).isEqualTo(first);
    }

    @Test
    void localFileBackendRejectsDuplicateOnUpload() {
        LocalFileBackend backend = new LocalFileBackend(tempDir.resolve("hub"));
        String skillId = "sk_duplicate001";
        SharedSkillBundle firstBundle = bundle(
                skillId,
                "python-debug",
                List.of(sharedExperience(
                        makeRecord(),
                        List.of("IndexError", "bounds", "loop"),
                        "check loop upper bound")));
        SharedSkillBundle duplicateBundle = bundle(
                skillId,
                "python-debug",
                List.of(sharedExperience(
                        makeRecord(),
                        List.of("IndexError", "bounds", "loop"),
                        "another attempt")));

        UploadResult first = backend.uploadBundle(firstBundle).toCompletableFuture().join();
        UploadResult rejected = backend.uploadBundle(duplicateBundle).toCompletableFuture().join();

        assertThat(first.isOk()).isTrue();
        assertThat(rejected.isOk()).isFalse();
        assertThat(rejected.getReason()).contains("overlap existing bundle");
    }

    @Test
    void experienceSharerReportsDuplicateOnFlush() throws Exception {
        LocalFileBackend backend = new LocalFileBackend(tempDir.resolve("hub"));
        EvolutionStore store = storeWithSkill("skills", "python-debug");
        String skillId = store.ensureSkillId("python-debug").toCompletableFuture().join();
        SharedSkillBundle existing = bundle(
                skillId,
                "python-debug",
                List.of(sharedExperience(
                        makeRecord(),
                        List.of("IndexError", "bounds", "loop"),
                        "check loop upper bound")));
        assertThat(backend.uploadBundle(existing).toCompletableFuture().join().isOk()).isTrue();

        ExperienceSharer sharer = new ExperienceSharer(backend, null, skillContextProvider(store));
        ShareStager stager = new ShareStager(new KeywordExtractor(), sharer);
        EvolutionRecord duplicate = makeRecord(
                "user_correction",
                0.8,
                List.of("IndexError", "bounds", "loop"),
                "check loop upper bound");
        StagingResult staged = stager.screenAndStage("python-debug", List.of(duplicate), null)
                .toCompletableFuture()
                .join();

        UploadResult result = sharer.flushPendingUploads("python-debug").toCompletableFuture().join();

        assertThat(staged.hasShareable()).isTrue();
        assertThat(result.isOk()).isFalse();
        assertThat(result.getReason()).contains("overlap existing bundle");
    }

    @Test
    void experienceSharerUploadsInitialPackageOnce() throws Exception {
        LocalFileBackend backend = new LocalFileBackend(tempDir.resolve("hub"));
        EvolutionStore store = storeWithSkill("skills-upload", "python-debug");
        ExperienceSharer sharer = new ExperienceSharer(backend, null, skillContextProvider(store));
        ShareStager stager = new ShareStager(new KeywordExtractor(), sharer);

        StagingResult staged = stager.screenAndStage("python-debug", List.of(makeRecord()), null)
                .toCompletableFuture()
                .join();
        UploadResult result = sharer.flushPendingUploads("python-debug").toCompletableFuture().join();
        String skillId = store.ensureSkillId("python-debug").toCompletableFuture().join();

        assertThat(staged.hasShareable()).isTrue();
        assertThat(result.isOk()).isTrue();
        assertThat(backend.hasSkillPackage(skillId).toCompletableFuture().join()).isTrue();
        assertThat(backend.downloadSkillPackage(skillId).toCompletableFuture().join()).isNotEmpty();
    }

    @Test
    void searchSkillsAndInstall() throws Exception {
        LocalFileBackend backend = new LocalFileBackend(tempDir.resolve("hub"));
        EvolutionStore publisherStore = storeWithSkill("publisher", "ppt-creator", "# PPT Creator\nMake slides.\n");
        String skillId = publisherStore.ensureSkillId("ppt-creator").toCompletableFuture().join();
        byte[] packageBytes = publisherStore.packSkillForSharing("ppt-creator").toCompletableFuture().join();
        backend.uploadSkillPackage(
                skillId,
                packageBytes,
                packageMeta(skillId, "ppt-creator", "Create presentations")).toCompletableFuture().join();
        SharedSkillBundle bundle = bundle(
                skillId,
                "ppt-creator",
                List.of(sharedExperience(makeRecord(), List.of("ppt", "slide", "layout"), "fix slide layout")));
        assertThat(backend.uploadBundle(bundle).toCompletableFuture().join().isOk()).isTrue();

        EvolutionStore installerStore = new EvolutionStore(tempDir.resolve("installer").toString());
        ExperienceHubClient client = new ExperienceHubClient(backend, installerStore);
        List<SkillSearchResult> results = client.searchSkills(
                query(List.of("ppt", "slide"), "presentation"),
                3).toCompletableFuture().join();
        Path installed = client.installSkill(skillId).toCompletableFuture().join();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSkillId()).isEqualTo(skillId);
        assertThat(installed).isNotNull();
        assertThat(Files.isRegularFile(installed.resolve("SKILL.md"))).isTrue();
        assertThat(Files.isRegularFile(installed.resolve("scripts").resolve("helper.py"))).isTrue();
        assertThat(installerStore.readSkillId("ppt-creator").toCompletableFuture().join()).isEqualTo(skillId);
    }

    @Test
    void shareStagerDropsExecutionFailureWithoutSuccessfulTool() {
        ExperienceSharer sharer = new ExperienceSharer(new LocalFileBackend(tempDir.resolve("hub")));
        ShareStager stager = new ShareStager(new KeywordExtractor(), sharer);
        EvolutionRecord record = makeRecord("execution_failure", 0.8);
        List<Map<String, Object>> failedOnly = List.of(
                Map.of("role", "user", "content", "run it"),
                Map.of("role", "assistant", "tool_calls", List.of(Map.of(
                        "id", "tc_1",
                        "name", "bash",
                        "arguments", "{}"))),
                Map.of(
                        "role", "tool",
                        "tool_call_id", "tc_1",
                        "name", "bash",
                        "content", "Error: command failed with exit code 1"));

        StagingResult result = stager.screenAndStage("python-debug", List.of(record), failedOnly)
                .toCompletableFuture()
                .join();

        assertThat(result.hasShareable()).isFalse();
        assertThat(result.getDroppedForShare().get(0).reason())
                .contains("execution failure without successful follow-up tool call");
    }

    @Test
    void ensureSkillIdInContentAddsFrontmatterField() {
        String content = "---\nname: demo\ndescription: d\n---\n\n# Body\n";

        SkillPackage.SkillIdContent result = SkillPackage.ensureSkillIdInContent(content);

        assertThat(result.skillId()).startsWith("sk_");
        assertThat(result.content()).contains("skill_id: " + result.skillId());
    }

    @Test
    void keywordExtractorParseFromPatch() {
        EvolutionPatch patch = new EvolutionPatch(
                "Troubleshooting",
                "append",
                "body",
                EvolutionTarget.BODY,
                null,
                null,
                null,
                null,
                null,
                List.of("a", "b"),
                "one line");

        KeywordExtractor.KeywordSummary summary = KeywordExtractor.parseFromOptimizerOutput(patch);

        assertThat(summary.keywords()).containsExactly("a", "b");
        assertThat(summary.summary()).isEqualTo("one line");
    }

    private EvolutionStore storeWithSkill(String rootName, String skillName) throws Exception {
        return storeWithSkill(rootName, skillName, "# Skill\n");
    }

    private EvolutionStore storeWithSkill(String rootName, String skillName, String body) throws Exception {
        Path root = tempDir.resolve(rootName);
        writeSkillDir(root, skillName, body);
        return new EvolutionStore(root.toString());
    }

    private static SkillSharingContextProvider skillContextProvider(EvolutionStore store) {
        return skillName -> CompletableFuture.completedFuture(new SkillSharingContext(
                store.ensureSkillId(skillName).toCompletableFuture().join(),
                store.packSkillForSharing(skillName).toCompletableFuture().join(),
                skillName,
                EvolutionStore.extractDescriptionFromSkillMd(
                        store.readPristineSkillContent(skillName).toCompletableFuture().join())));
    }

    private static SharedSkillBundle bundle(String skillId, String skillName, List<SharedExperience> experiences) {
        SharedSkillBundle bundle = SharedSkillBundle.make(skillName, experiences);
        bundle.setSkillId(skillId);
        return bundle;
    }

    private static SharedExperience sharedExperience(
            EvolutionRecord record,
            List<String> keywords,
            String summary
    ) {
        SharedExperience shared = new SharedExperience();
        shared.setRecord(record);
        shared.setKeywords(keywords);
        shared.setSummary(summary);
        return shared;
    }

    private static EvolutionRecord makeRecord() {
        return makeRecord("user_correction", 0.8);
    }

    private static EvolutionRecord makeRecord(String source, double score) {
        return makeRecord(source, score, List.of("IndexError", "bounds"), "check loop upper bound");
    }

    private static EvolutionRecord makeRecord(
            String source,
            double score,
            List<String> keywords,
            String summary
    ) {
        EvolutionPatch patch = new EvolutionPatch(
                "Troubleshooting",
                "append",
                "## Fix\n- check bounds",
                EvolutionTarget.BODY,
                null,
                null,
                null,
                null,
                null,
                keywords,
                summary);
        return EvolutionRecord.make(source, "ctx", patch, score, null, summary);
    }

    private static QueryKeywords query(List<String> keywords, String intent) {
        QueryKeywords query = new QueryKeywords();
        query.setKeywords(keywords);
        query.setIntent(intent);
        return query;
    }

    private static SkillPackageMeta packageMeta(String skillId, String skillName, String description) {
        SkillPackageMeta meta = new SkillPackageMeta();
        meta.setSkillId(skillId);
        meta.setSkillName(skillName);
        meta.setDescription(description);
        return meta;
    }

    private static Path writeSkillDir(Path base, String name) throws Exception {
        return writeSkillDir(base, name, "# Skill\n");
    }

    private static Path writeSkillDir(Path base, String name, String body) throws Exception {
        Path skillDir = Files.createDirectories(base.resolve(name));
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                "---\nname: " + name + "\ndescription: test skill\n---\n\n" + body,
                StandardCharsets.UTF_8);
        Path scripts = Files.createDirectories(skillDir.resolve("scripts"));
        Files.writeString(scripts.resolve("helper.py"), "print('ok')\n", StandardCharsets.UTF_8);
        return skillDir;
    }
}
