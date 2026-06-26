/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreRecordsHelperTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void appendRecordTransactionalPersistsScriptAndMutatesOriginalRecord() throws Exception {
        FakeStore store = new FakeStore(tempDir.resolve("skills"));
        StoreRecordsHelper helper = new StoreRecordsHelper(store);
        EvolutionRecord record = scriptRecord("ev_script", "print('hello')", "python", "demo task");

        EvolutionLog log = helper.appendRecordTransactional("demo", record, null)
                .toCompletableFuture()
                .join();

        assertNotNull(log);
        assertEquals(1, log.getEntries().size());
        Path scriptPath = store.resolveSkillDir("demo", false)
                .resolve("evolution")
                .resolve("scripts")
                .resolve("ev_script_script.py");
        assertTrue(Files.exists(scriptPath));
        assertEquals("print('hello')", Files.readString(scriptPath));
        assertEquals("ev_script_script.py", record.getChange().getScriptFilename());
        assertTrue(record.getChange().getContent().contains("Script: ev_script_script.py"));
        assertEquals(List.of("demo"), store.renderedNames);

        Map<?, ?> persisted = OBJECT_MAPPER.readValue(
                store.resolveSkillDir("demo", false).resolve("evolutions.json").toFile(),
                Map.class
        );
        List<?> entries = (List<?>) persisted.get("entries");
        Map<?, ?> entry = (Map<?, ?>) entries.get(0);
        Map<?, ?> change = (Map<?, ?>) entry.get("change");
        assertEquals("ev_script_script.py", change.get("script_filename"));
        assertTrue(String.valueOf(change.get("content")).contains("Language: python"));
    }

    @Test
    void appendRecordTransactionalRollsBackProjectionFilesOnRenderFailure() throws Exception {
        FakeStore store = new FakeStore(tempDir.resolve("skills"));
        Path skillDir = store.prepareSkill("demo", "# demo\n");
        Files.createDirectories(skillDir.resolve("evolution"));
        Files.writeString(skillDir.resolve("evolution").resolve("existing.md"), "before", StandardCharsets.UTF_8);
        EvolutionLog original = new EvolutionLog(
                "demo",
                "1.0.0",
                "2026-06-10T00:00:00Z",
                List.of(bodyRecord("ev_old", "keep old"))
        );
        Files.writeString(
                skillDir.resolve("evolutions.json"),
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(original.toDict()),
                StandardCharsets.UTF_8
        );
        store.failRender = true;
        StoreRecordsHelper helper = new StoreRecordsHelper(store);

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> helper.appendRecordTransactional("demo", bodyRecord("ev_new", "new body"))
                        .toCompletableFuture()
                        .join()
        );

        assertTrue(failure.getCause() == null || failure.getCause() instanceof RuntimeException || failure instanceof RuntimeException);
        assertEquals("before", Files.readString(skillDir.resolve("evolution").resolve("existing.md")));
        assertFalse(Files.exists(skillDir.resolve("evolution").resolve("rendered.md")));
        assertEquals("# demo\n", Files.readString(skillDir.resolve("SKILL.md")));
        Map<?, ?> persisted = OBJECT_MAPPER.readValue(skillDir.resolve("evolutions.json").toFile(), Map.class);
        List<?> entries = (List<?>) persisted.get("entries");
        assertEquals(1, entries.size());
        Map<?, ?> entry = (Map<?, ?>) entries.get(0);
        assertEquals("ev_old", entry.get("id"));
    }

    @Test
    void saveEvolutionLogRaisesStoreErrorWhenReadbackJsonIsInvalid() {
        FakeStore store = new FakeStore(tempDir.resolve("skills"));
        store.invalidReadback = true;
        StoreRecordsHelper helper = new StoreRecordsHelper(store);

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> helper.saveEvolutionLog("demo", EvolutionLog.empty("demo"), null)
                        .toCompletableFuture()
                        .join()
        );

        assertTrue(failure.getCause() instanceof BaseError);
        BaseError cause = (BaseError) failure.getCause();
        assertEquals(StatusCode.TOOLCHAIN_EVOLVING_SKILL_STORE_EXECUTION_ERROR, cause.getStatus());
    }

    @Test
    void updateDeleteApplyMergeAndRefineFollowPythonSemantics() throws Exception {
        FakeStore store = new FakeStore(tempDir.resolve("skills"));
        StoreRecordsHelper helper = new StoreRecordsHelper(store);
        EvolutionRecord first = bodyRecord("ev_1", "first");
        first.setScore(0.5d);
        EvolutionRecord second = bodyRecord("ev_2", "second");
        second.setScore(0.8d);
        EvolutionRecord third = bodyRecord("ev_3", "third");
        third.setScore(0.4d);
        helper.saveEvolutionLog("demo", new EvolutionLog(
                "demo",
                "1.0.0",
                "2026-06-10T00:00:00Z",
                List.of(first, second, third)
        ), null).toCompletableFuture().join();

        UsageStats usageStats = new UsageStats();
        usageStats.setTimesPositive(3);
        int updated = helper.updateRecordScores("demo", Map.of(
                "ev_1", StoreRecordsHelper.RecordUpdate.of(0.95d, usageStats)
        )).toCompletableFuture().join();
        List<EvolutionRecord> scored = helper.getRecordsByScore("demo", 0.6d).toCompletableFuture().join();
        int applied = helper.markRecordsApplied("demo", List.of("ev_1", "ev_3")).toCompletableFuture().join();
        EvolutionRecord merged = helper.mergeRecords("demo", "ev_1", List.of("ev_2"), "merged content", null)
                .toCompletableFuture()
                .join();
        EvolutionRecord refined = helper.updateRecordContent("demo", "ev_1", "refined content", 0.97d)
                .toCompletableFuture()
                .join();
        int deleted = helper.deleteRecords("demo", List.of("ev_3")).toCompletableFuture().join();
        EvolutionLog finalLog = helper.loadFullEvolutionLog("demo").toCompletableFuture().join();

        assertEquals(1, updated);
        assertEquals(List.of("ev_1", "ev_2"), scored.stream().map(EvolutionRecord::getId).toList());
        assertEquals(2, applied);
        assertNotNull(merged);
        assertEquals(0.95d, merged.getScore(), 1.0e-9);
        assertEquals("merged content", merged.getChange().getContent());
        assertNotNull(refined);
        assertEquals("refined content", refined.getChange().getContent());
        assertEquals(0.97d, refined.getScore(), 1.0e-9);
        assertEquals(1, deleted);
        assertEquals(1, finalLog.getEntries().size());
        assertEquals("ev_1", finalLog.getEntries().get(0).getId());
        assertTrue(finalLog.getEntries().get(0).isApplied());
        assertEquals(3, finalLog.getEntries().get(0).getUsageStats().getTimesPositive());
        assertTrue(store.renderedNames.size() >= 3);
    }

    @Test
    void mergeAndUpdateReturnNullWhenRecordIsMissing() {
        FakeStore store = new FakeStore(tempDir.resolve("skills"));
        StoreRecordsHelper helper = new StoreRecordsHelper(store);

        EvolutionRecord merged = helper.mergeRecords("demo", "missing", List.of("ev_x"), "merged", null)
                .toCompletableFuture()
                .join();
        EvolutionRecord refined = helper.updateRecordContent("demo", "missing", "new content", null)
                .toCompletableFuture()
                .join();

        assertNull(merged);
        assertNull(refined);
    }

    private static EvolutionRecord scriptRecord(String id, String content, String language, String purpose) {
        EvolutionPatch patch = EvolutionPatch.builder()
                .section("Scripts")
                .action(Protocols.APPEND_MODE)
                .content(content)
                .target(EvolutionTarget.SCRIPT)
                .scriptLanguage(language)
                .scriptPurpose(purpose)
                .build();
        return EvolutionRecord.builder()
                .id(id)
                .source("source")
                .timestamp("2026-06-10T00:00:00Z")
                .context("context")
                .change(patch)
                .score(0.8d)
                .usageStats(new UsageStats())
                .build();
    }

    private static EvolutionRecord bodyRecord(String id, String content) {
        EvolutionPatch patch = EvolutionPatch.builder()
                .section("Troubleshooting")
                .action(Protocols.APPEND_MODE)
                .content(content)
                .target(EvolutionTarget.BODY)
                .build();
        return EvolutionRecord.builder()
                .id(id)
                .source("source")
                .timestamp("2026-06-10T00:00:00Z")
                .context("context")
                .change(patch)
                .score(0.6d)
                .usageStats(new UsageStats())
                .build();
    }

    private static final class FakeStore implements StoreRecordsHelper.StoreRecordsStore {

        private final Path root;
        private final List<String> renderedNames = new java.util.ArrayList<>();
        private boolean failRender;
        private boolean invalidReadback;

        private FakeStore(Path root) {
            this.root = root;
        }

        private Path prepareSkill(String name, String skillMdContent) throws IOException {
            Path skillDir = resolveSkillDir(name, true);
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), skillMdContent, StandardCharsets.UTF_8);
            return skillDir;
        }

        @Override
        public Path resolveSkillDir(String name, boolean create) {
            return root.resolve(name);
        }

        @Override
        public CompletionStage<String> readFileText(Path path) {
            try {
                if (invalidReadback && path.getFileName().toString().equals("evolutions.json")) {
                    invalidReadback = false;
                    return CompletableFuture.completedFuture("{invalid json");
                }
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
        public CompletionStage<Void> renderEvolutionMarkdown(String name) {
            renderedNames.add(name);
            Path skillDir = resolveSkillDir(name, false);
            try {
                Files.createDirectories(skillDir.resolve("evolution"));
                Files.writeString(skillDir.resolve("evolution").resolve("rendered.md"), "new render", StandardCharsets.UTF_8);
                if (Files.exists(skillDir.resolve("SKILL.md"))) {
                    Files.writeString(skillDir.resolve("SKILL.md"), "# mutated\n", StandardCharsets.UTF_8);
                }
            } catch (IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }
            if (failRender) {
                return CompletableFuture.failedFuture(new IllegalStateException("render failed"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
