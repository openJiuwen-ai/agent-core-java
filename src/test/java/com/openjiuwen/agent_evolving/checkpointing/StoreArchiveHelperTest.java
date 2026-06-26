/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreArchiveHelperTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void createSkillWritesSkillAndEmptyEvolutionLog() throws Exception {
        FakeStore store = new FakeStore(tempDir.resolve("skills"));
        StoreArchiveHelper helper = new StoreArchiveHelper(store);

        Path skillDir = helper.createSkill(
                        "demo_skill",
                        "sample description",
                        "Body text",
                        null)
                .toCompletableFuture()
                .join();

        assertNotNull(skillDir);
        assertTrue(Files.isDirectory(skillDir));
        assertTrue(Files.isDirectory(skillDir.resolve("evolution")));
        assertTrue(Files.exists(skillDir.resolve("SKILL.md")));
        assertTrue(Files.exists(skillDir.resolve("evolutions.json")));
        String skillMd = Files.readString(skillDir.resolve("SKILL.md"));
        assertTrue(skillMd.contains("name: demo_skill"));
        assertTrue(skillMd.contains("description: sample description"));
        assertTrue(skillMd.contains("# demo_skill"));
        assertTrue(skillMd.contains("Body text"));
        Map<?, ?> persisted = OBJECT_MAPPER.readValue(skillDir.resolve("evolutions.json").toFile(), Map.class);
        assertEquals("demo_skill", persisted.get("skill_id"));
        assertEquals(List.of(), persisted.get("entries"));
    }

    @Test
    void createSkillRejectsInvalidName() {
        FakeStore store = new FakeStore(tempDir.resolve("skills"));
        StoreArchiveHelper helper = new StoreArchiveHelper(store);

        Path skillDir = helper.createSkill("bad/name", "desc", "body", null)
                .toCompletableFuture()
                .join();

        assertNull(skillDir);
        assertFalse(Files.exists(tempDir.resolve("skills").resolve("bad/name")));
    }

    @Test
    void archiveSkillBodyCopiesSkillMarkdown() throws Exception {
        FakeStore store = new FakeStore(tempDir.resolve("skills"));
        Path skillDir = store.prepareSkill("demo", "# demo\n\ncontent\n");
        StoreArchiveHelper helper = new StoreArchiveHelper(store);

        String archived = helper.archiveSkillBody("demo").toCompletableFuture().join();

        assertNotNull(archived);
        Path archivedPath = skillDir.resolve("archive").resolve(archived);
        assertTrue(archived.startsWith("SKILL.v"));
        assertEquals("# demo\n\ncontent\n", Files.readString(archivedPath));
    }

    @Test
    void archiveEvolutionsCopiesEvolutionJsonAndListArchivesSortsDescending() throws Exception {
        FakeStore store = new FakeStore(tempDir.resolve("skills"));
        Path skillDir = store.prepareSkill("demo", "# demo\n");
        Files.writeString(skillDir.resolve("archive").resolve("SKILL.v20240609T010101.md"), "older");
        Files.writeString(skillDir.resolve("archive").resolve("SKILL.v20240609T020202.md"), "newer");
        StoreArchiveHelper helper = new StoreArchiveHelper(store);

        String archived = helper.archiveEvolutions("demo").toCompletableFuture().join();
        List<String> archives = helper.listArchives("demo");

        assertNotNull(archived);
        assertTrue(archived.startsWith("evolutions.v"));
        assertTrue(Files.exists(skillDir.resolve("archive").resolve(archived)));
        assertEquals(
                archives,
                new ArrayList<>(archives.stream().sorted(Comparator.reverseOrder()).toList()));
        assertTrue(archives.contains(archived));
    }

    @Test
    void clearEvolutionsPersistsEmptyLogAndTriggersProjectionRender() throws Exception {
        FakeStore store = new FakeStore(tempDir.resolve("skills"));
        Path skillDir = store.prepareSkill("demo", "# demo\n");
        Files.writeString(
                skillDir.resolve("evolutions.json"),
                "{\"skill_id\":\"demo\",\"entries\":[{\"id\":\"ev1\"}]}",
                StandardCharsets.UTF_8);
        StoreArchiveHelper helper = new StoreArchiveHelper(store);

        helper.clearEvolutions("demo").toCompletableFuture().join();

        Map<?, ?> persisted = OBJECT_MAPPER.readValue(skillDir.resolve("evolutions.json").toFile(), Map.class);
        assertEquals("demo", persisted.get("skill_id"));
        assertEquals(List.of(), persisted.get("entries"));
        assertEquals(List.of("demo"), store.renderedNames);
    }

    private static final class FakeStore implements StoreArchiveHelper.StoreArchiveStore {

        private final Path root;
        private final List<String> renderedNames = new ArrayList<>();

        private FakeStore(Path root) {
            this.root = root;
        }

        private Path prepareSkill(String name, String skillMdContent) throws IOException {
            Path skillDir = resolveSkillDir(name, true);
            Files.createDirectories(skillDir.resolve("archive"));
            Files.writeString(skillDir.resolve("SKILL.md"), skillMdContent, StandardCharsets.UTF_8);
            Files.writeString(
                    skillDir.resolve("evolutions.json"),
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(EvolutionLog.empty(name).toDict()),
                    StandardCharsets.UTF_8);
            return skillDir;
        }

        @Override
        public Path resolveSkillDir(String name, boolean create) {
            return root.resolve(name);
        }

        @Override
        public Path findSkillMd(Path skillDir) {
            Path skillMd = skillDir.resolve("SKILL.md");
            return Files.exists(skillMd) ? skillMd : null;
        }

        @Override
        public CompletionStage<String> readFileText(Path path) {
            try {
                return CompletableFuture.completedFuture(Files.readString(path, StandardCharsets.UTF_8));
            } catch (IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        @Override
        public CompletionStage<Void> writeFileText(Path path, String content) {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, content, StandardCharsets.UTF_8);
                return CompletableFuture.completedFuture(null);
            } catch (IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        @Override
        public CompletionStage<Void> saveEvolutionLog(String name, EvolutionLog evolutionLog, Path skillDir) {
            Path targetDir = skillDir == null ? resolveSkillDir(name, true) : skillDir;
            try {
                Files.createDirectories(targetDir);
                String content = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(evolutionLog.toDict());
                Files.writeString(targetDir.resolve("evolutions.json"), content, StandardCharsets.UTF_8);
                return CompletableFuture.completedFuture(null);
            } catch (IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        @Override
        public CompletionStage<Void> renderEvolutionMarkdown(String name) {
            renderedNames.add(name);
            return CompletableFuture.completedFuture(null);
        }
    }
}
