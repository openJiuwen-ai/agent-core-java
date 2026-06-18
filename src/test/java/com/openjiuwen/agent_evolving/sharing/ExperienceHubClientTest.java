/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.sharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionStore;
import com.openjiuwen.agent_evolving.sharing.backends.SharingBackend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code ExperienceHubClient} in
 * {@code openjiuwen/agent_evolving/sharing/hub_client.py}.
 */
class ExperienceHubClientTest {

    @TempDir
    Path tempDir;

    @Test
    void searchSkillsDelegatesToSharerDefaultTopK() {
        FakeSharingBackend backend = new FakeSharingBackend();
        SkillSearchResult result = new SkillSearchResult();
        result.setSkillId("sk_python_debug");
        result.setSkillName("python-debug");
        backend.searchResults = List.of(result);
        ExperienceHubClient client = new ExperienceHubClient(backend, store("skills"));
        QueryKeywords query = query(List.of("python", "debug"), "debug");

        List<SkillSearchResult> results = client.searchSkills(query).toCompletableFuture().join();

        assertNotNull(client.getSharer());
        assertEquals(1, results.size());
        assertEquals("sk_python_debug", results.getFirst().getSkillId());
        assertEquals(query, backend.lastSearchQuery);
        assertEquals(5, backend.lastSearchTopK);
    }

    @Test
    void installSkillReturnsNullForBlankIdAndMissingPackage() {
        FakeSharingBackend backend = new FakeSharingBackend();
        ExperienceHubClient client = new ExperienceHubClient(backend, store("installer"));

        Path blankResult = client.installSkill("   ").toCompletableFuture().join();
        Path missingResult = client.installSkill(" sk_missing ").toCompletableFuture().join();

        assertNull(blankResult);
        assertNull(missingResult);
        assertEquals("sk_missing", backend.lastDownloadSkillId);
    }

    @Test
    void installSkillUsesExplicitSkillNameBeforePackageMeta() throws Exception {
        FakeSharingBackend backend = new FakeSharingBackend();
        backend.packageBytesBySkillId.put("sk_hub_skill", packageBytes("hub-original"));
        SkillPackageMeta meta = new SkillPackageMeta();
        meta.setSkillId("sk_hub_skill");
        meta.setSkillName("meta-skill");
        backend.packageMetaBySkillId.put("sk_hub_skill", meta);
        ExperienceHubClient client = new ExperienceHubClient(backend, store("installer-explicit"));

        Path installed = client.installSkill(" sk_hub_skill ", " explicit-skill ").toCompletableFuture().join();

        assertNotNull(installed);
        assertEquals(tempDir.resolve("installer-explicit").resolve("explicit-skill"), installed);
        assertTrue(Files.isRegularFile(installed.resolve("SKILL.md")));
        assertTrue(Files.isRegularFile(installed.resolve("scripts").resolve("helper.py")));
        assertEquals("sk_hub_skill", backend.lastDownloadSkillId);
        assertEquals("sk_hub_skill", backend.lastMetaSkillId);
    }

    @Test
    void installSkillFallsBackToPackageMetaWhenNameMissing() throws Exception {
        FakeSharingBackend backend = new FakeSharingBackend();
        backend.packageBytesBySkillId.put("sk_hub_skill", packageBytes("hub-original"));
        SkillPackageMeta meta = new SkillPackageMeta();
        meta.setSkillId("sk_hub_skill");
        meta.setSkillName("meta-skill");
        backend.packageMetaBySkillId.put("sk_hub_skill", meta);
        ExperienceHubClient client = new ExperienceHubClient(backend, store("installer-meta"));

        Path installed = client.installSkill("sk_hub_skill").toCompletableFuture().join();

        assertNotNull(installed);
        assertEquals(tempDir.resolve("installer-meta").resolve("meta-skill"), installed);
        assertTrue(Files.isRegularFile(installed.resolve("SKILL.md")));
        assertTrue(Files.isRegularFile(installed.resolve("scripts").resolve("helper.py")));
    }

    @Test
    void blankExplicitSkillNameDoesNotFallBackToPackageMeta() {
        FakeSharingBackend backend = new FakeSharingBackend();
        backend.packageBytesBySkillId.put("sk_hub_skill", new byte[] {1, 2, 3});
        SkillPackageMeta meta = new SkillPackageMeta();
        meta.setSkillId("sk_hub_skill");
        meta.setSkillName("meta-skill");
        backend.packageMetaBySkillId.put("sk_hub_skill", meta);
        RecordingEvolutionStore store = new RecordingEvolutionStore(tempDir.resolve("recording-store"));
        ExperienceHubClient client = new ExperienceHubClient(backend, store);

        Path installed = client.installSkill("sk_hub_skill", "   ").toCompletableFuture().join();

        assertNotNull(installed);
        assertNull(store.lastSkillName);
    }

    private EvolutionStore store(String name) {
        return new EvolutionStore(tempDir.resolve(name).toString());
    }

    private byte[] packageBytes(String skillName) throws Exception {
        Path publisherRoot = Files.createDirectories(tempDir.resolve("publisher-" + skillName));
        Path skillDir = Files.createDirectories(publisherRoot.resolve(skillName));
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                "# " + skillName + "\n\nContent\n",
                StandardCharsets.UTF_8);
        Files.createDirectories(skillDir.resolve("scripts"));
        Files.writeString(skillDir.resolve("scripts").resolve("helper.py"), "print('hi')", StandardCharsets.UTF_8);
        return new EvolutionStore(publisherRoot.toString()).packSkillForSharing(skillName).toCompletableFuture().join();
    }

    private static QueryKeywords query(List<String> keywords, String intent) {
        QueryKeywords query = new QueryKeywords();
        query.setKeywords(keywords);
        query.setIntent(intent);
        return query;
    }

    private static UploadResult uploadResult() {
        UploadResult result = new UploadResult();
        result.setOk(true);
        return result;
    }

    private static final class FakeSharingBackend implements SharingBackend {
        private final Map<String, byte[]> packageBytesBySkillId = new HashMap<>();
        private final Map<String, SkillPackageMeta> packageMetaBySkillId = new HashMap<>();
        private List<SkillSearchResult> searchResults = List.of();
        private QueryKeywords lastSearchQuery;
        private int lastSearchTopK;
        private String lastDownloadSkillId;
        private String lastMetaSkillId;

        @Override
        public CompletionStage<UploadResult> uploadBundle(SharedSkillBundle bundle) {
            return CompletableFuture.completedFuture(uploadResult());
        }

        @Override
        public CompletionStage<List<SharedSkillBundle>> downloadBundles(String skillId, QueryKeywords query, int topK) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<Boolean> hasSkillPackage(String skillId) {
            return CompletableFuture.completedFuture(packageBytesBySkillId.containsKey(skillId));
        }

        @Override
        public CompletionStage<Void> uploadSkillPackage(String skillId, byte[] packageBytes, SkillPackageMeta meta) {
            packageBytesBySkillId.put(skillId, packageBytes == null ? null : packageBytes.clone());
            packageMetaBySkillId.put(skillId, meta);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<byte[]> downloadSkillPackage(String skillId) {
            lastDownloadSkillId = skillId;
            byte[] bytes = packageBytesBySkillId.get(skillId);
            return CompletableFuture.completedFuture(bytes == null ? null : bytes.clone());
        }

        @Override
        public CompletionStage<SkillPackageMeta> getSkillPackageMeta(String skillId) {
            lastMetaSkillId = skillId;
            return CompletableFuture.completedFuture(packageMetaBySkillId.get(skillId));
        }

        @Override
        public CompletionStage<List<SkillSearchResult>> searchSkills(QueryKeywords query, int topK) {
            lastSearchQuery = query;
            lastSearchTopK = topK;
            return CompletableFuture.completedFuture(searchResults);
        }
    }

    private static final class RecordingEvolutionStore extends EvolutionStore {
        private final Path installedPath;
        private String lastSkillName = "not-called";

        private RecordingEvolutionStore(Path root) {
            super(root.toString());
            this.installedPath = root.resolve("installed");
        }

        @Override
        public CompletionStage<Path> installSkillPackage(byte[] packageBytes, String skillName) {
            lastSkillName = skillName;
            return CompletableFuture.completedFuture(installedPath);
        }
    }
}
