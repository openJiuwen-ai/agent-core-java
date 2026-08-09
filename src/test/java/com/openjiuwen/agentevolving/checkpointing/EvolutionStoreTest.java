/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.checkpointing;

import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.result.ReadFileData;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.sysop.result.WriteFileResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the evolution store facade.
 *
 * <p>Mirrors Python's {@code test_evolution_store} in
 * {@code tests/unit_tests/agent_evolving/checkpointing/test_evolution_store.py}.</p>
 */
class EvolutionStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesAndDeduplicatesBaseDirs() throws Exception {
        Path rootA = Files.createDirectory(tempDir.resolve("a"));
        Path rootB = Files.createDirectory(tempDir.resolve("b"));

        EvolutionStore store = new EvolutionStore(rootA + "; " + rootB + ", " + rootA);

        assertEquals(2, store.getBaseDirs().size());
        assertEquals(store.getBaseDirs().get(0), store.getBaseDir());
    }

    @Test
    void initWithEmptyPathRaises() {
        assertThrows(IllegalArgumentException.class, () -> new EvolutionStore("  "));
    }

    @Test
    void listsSkillNamesAndReadsContent() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-b", "# B");
        prepareSkill(root, "skill-a", "# A");
        Files.createDirectories(root.resolve("_hidden"));
        EvolutionStore store = new EvolutionStore(root.toString());

        assertEquals(List.of("skill-a", "skill-b"), store.listSkillNames());
        assertTrue(store.skillExists("skill-a"));
        assertEquals("# A", join(store.readSkillContent("skill-a")));
        assertEquals("", join(store.readSkillContent("missing")));
    }

    @Test
    void strictReadRequiresSkillDefinition() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        Path skillDir = Files.createDirectory(root.resolve("skill-a"));
        Files.writeString(skillDir.resolve("README.md"), "# fallback", StandardCharsets.UTF_8);
        EvolutionStore store = new EvolutionStore(root.toString());

        assertTrue(store.skillExists("skill-a"));
        assertFalse(store.skillDefinitionExists("skill-a"));
        assertEquals("# fallback", join(store.readSkillContent("skill-a")));

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> join(store.readSkillContent("skill-a", true))
        );
        assertInstanceOf(BaseError.class, failure.getCause());
        assertEquals(StatusCode.TOOLCHAIN_EVOLVING_SKILL_DEFINITION_NOT_FOUND, ((BaseError) failure.getCause()).getStatus());
    }

    @Test
    void recordSummarySerializesAndLegacyJsonStaysCompatible() {
        EvolutionPatch patch = patch(EvolutionTarget.BODY, "Troubleshooting", "Check CSV inputs");
        EvolutionRecord record = EvolutionRecord.make(
                "execution_failure",
                "ctx",
                patch,
                0.6d,
                null,
                "Check CSV encoding and delimiters before parsing.");

        Map<String, Object> payload = record.toDict();
        EvolutionRecord restored = EvolutionRecord.fromDict(payload);
        Map<String, Object> legacyPayload = new LinkedHashMap<>(payload);
        legacyPayload.remove("summary");
        EvolutionRecord legacy = EvolutionRecord.fromDict(legacyPayload);

        assertEquals("Check CSV encoding and delimiters before parsing.", payload.get("summary"));
        assertEquals("Check CSV encoding and delimiters before parsing.", restored.getSummary());
        assertNull(legacy.getSummary());
    }

    @Test
    void loadFullLogHandlesInvalidJson() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        Path skillDir = prepareSkill(root, "skill-a", "# Skill");
        Files.writeString(skillDir.resolve("evolutions.json"), "{not-json", StandardCharsets.UTF_8);

        EvolutionStore store = new EvolutionStore(root.toString());
        EvolutionLog log = join(store.loadFullEvolutionLog("skill-a"));

        assertEquals("skill-a", log.getSkillId());
        assertTrue(log.getEntries().isEmpty());
    }

    @Test
    void appendsRecordAndFiltersByTarget() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill A\n\nContent\n");
        EvolutionStore store = new EvolutionStore(root.toString());

        join(store.appendRecord("skill-a", record("ev_desc", EvolutionTarget.DESCRIPTION, "Instructions", "short desc")));
        join(store.appendRecord("skill-a", record("ev_body", EvolutionTarget.BODY, "Troubleshooting", "body fix")));

        EvolutionLog full = join(store.loadEvolutionLog("skill-a"));
        EvolutionLog body = join(store.loadEvolutionLog("skill-a", EvolutionTarget.BODY));

        assertEquals(2, full.getEntries().size());
        assertEquals(List.of("ev_body"), body.getEntries().stream().map(EvolutionRecord::getId).toList());
    }

    @Test
    void appendRecordMergesWhenMergeTargetHits() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new EvolutionStore(root.toString());

        join(store.appendRecord("skill-a", record("ev_old", EvolutionTarget.BODY, "Troubleshooting", "old")));
        EvolutionRecord replacement = record("ev_new", EvolutionTarget.BODY, "Troubleshooting", "new");
        replacement.getChange().setMergeTarget("ev_old");
        join(store.appendRecord("skill-a", replacement));

        EvolutionLog log = join(store.loadEvolutionLog("skill-a"));
        assertEquals(List.of("ev_new"), log.getEntries().stream().map(EvolutionRecord::getId).toList());
        assertEquals("new", log.getEntries().get(0).getChange().getContent());
    }

    @Test
    void appendRecordRollsBackLogOnFailure() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new InvalidReadbackStore(root.toString());

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> join(store.appendRecord("skill-a", record("ev_1", EvolutionTarget.BODY, "Troubleshooting", "fix")))
        );

        assertInstanceOf(BaseError.class, failure.getCause());
        assertTrue(join(store.loadEvolutionLog("skill-a")).getEntries().isEmpty());
    }

    @Test
    void appendRecordRollsBackScriptFileAndKeepsPayloadOnFailure() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new InvalidReadbackStore(root.toString());
        EvolutionRecord script = record("ev_script", EvolutionTarget.SCRIPT, "Scripts", "print('new')");
        script.getChange().setScriptLanguage("python");
        String originalContent = script.getChange().getContent();

        assertThrows(CompletionException.class, () -> join(store.appendRecord("skill-a", script)));

        Path scriptsDir = root.resolve("skill-a").resolve("evolution").resolve("scripts");
        assertNoFiles(scriptsDir, ".py");
        assertEquals(originalContent, script.getChange().getContent());
        assertNull(script.getChange().getScriptFilename());
    }

    @Test
    void appendRecordRollsBackProjectionOnFailure() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        Path skillDir = prepareSkill(root, "skill-a", "# Skill A\n\n## Troubleshooting\n- old\n");
        String original = Files.readString(skillDir.resolve("SKILL.md"), StandardCharsets.UTF_8);
        EvolutionStore store = new ProjectionFailingStore(root.toString());

        assertThrows(CompletionException.class, () -> join(store.appendRecord("skill-a", record("ev_1", EvolutionTarget.BODY, "Troubleshooting", "fix"))));

        assertEquals(original, Files.readString(skillDir.resolve("SKILL.md"), StandardCharsets.UTF_8));
        assertTrue(join(store.loadEvolutionLog("skill-a")).getEntries().isEmpty());
    }

    @Test
    void saveEvolutionLogRaisesWhenReadbackIsInvalidJson() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new InvalidReadbackStore(root.toString());

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> join(store.saveEvolutionLog("skill-a", EvolutionLog.empty("skill-a")))
        );

        assertInstanceOf(BaseError.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("read back"));
    }

    @Test
    void formattingHelpersAndPendingSummaryFollowPythonFacade() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        prepareSkill(root, "skill-b", "# Skill");
        EvolutionStore store = new EvolutionStore(root.toString());

        join(store.appendRecord("skill-a", record("ev_desc", EvolutionTarget.DESCRIPTION, "Instructions", "Title\n- line1")));
        join(store.appendRecord("skill-a", record("ev_body", EvolutionTarget.BODY, "Troubleshooting", "Body fix")));

        assertTrue(join(store.formatDescExperienceText("skill-a")).contains("Title"));
        assertTrue(join(store.formatBodyExperienceText("skill-a")).contains("Body fix"));
        assertTrue(join(store.formatAllDescExperiences(List.of("skill-a", "skill-b"))).containsKey("skill-a"));
        assertFalse(join(store.formatAllDescExperiences(List.of("skill-a", "skill-b"))).containsKey("skill-b"));
        assertTrue(join(store.listPendingSummary(List.of("skill-a", "skill-b"))).contains("description: 1"));
    }

    @Test
    void pendingSummaryReturnsEmptyTextWhenNoRecords() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new EvolutionStore(root.toString());

        assertFalse(join(store.listPendingSummary(List.of("skill-a"))).isBlank());
    }

    @Test
    void readFileTextUsesSysOperationWhenAvailable() throws Exception {
        FakeFs fs = new FakeFs();
        fs.readContent = 123;
        EvolutionStore store = new EvolutionStore(tempDir.toString());
        store.setSysOperation(new FakeSysOperation(fs));

        assertEquals("123", join(store.readFileText(tempDir.resolve("missing.txt"))));
        assertEquals(tempDir.resolve("missing.txt").toString(), fs.lastReadPath);
    }

    @Test
    void writeFileTextUsesSysOperationWhenAvailable() {
        FakeFs fs = new FakeFs();
        EvolutionStore store = new EvolutionStore(tempDir.toString());
        store.setSysOperation(new FakeSysOperation(fs));

        join(store.writeFileText(tempDir.resolve("x.txt"), "hello"));

        assertEquals(tempDir.resolve("x.txt").toString(), fs.lastWritePath);
        assertEquals("hello", fs.lastWriteContent);
    }

    @Test
    void writeFileTextRaisesOnSysOperationFailure() {
        FakeFs fs = new FakeFs();
        fs.writeCode = 1;
        fs.writeMessage = "disk full";
        EvolutionStore store = new EvolutionStore(tempDir.toString());
        store.setSysOperation(new FakeSysOperation(fs));

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> join(store.writeFileText(tempDir.resolve("x.txt"), "hello"))
        );

        assertInstanceOf(BaseError.class, failure.getCause());
        assertEquals(StatusCode.TOOLCHAIN_EVOLVING_SKILL_STORE_EXECUTION_ERROR, ((BaseError) failure.getCause()).getStatus());
        assertTrue(failure.getCause().getMessage().contains("disk full"));
    }

    @Test
    void writeFileTextWithoutSysOperationUsesLocalFile() throws Exception {
        EvolutionStore store = new EvolutionStore(tempDir.toString());
        Path target = tempDir.resolve("x.txt");

        join(store.writeFileText(target, "hello"));

        assertEquals("hello", Files.readString(target, StandardCharsets.UTF_8));
        assertEquals("hello", join(store.readFileText(target)));
    }

    @Test
    void persistScriptWritesFileAndReplacesContent() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        Path skillDir = prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new EvolutionStore(root.toString());
        EvolutionRecord script = record("ev_script_1", EvolutionTarget.SCRIPT, "Scripts", "import matplotlib\nprint('chart')");
        script.getChange().setScriptLanguage("python");
        script.getChange().setScriptPurpose("chart generation");

        join(new StoreRecordsHelper(store).persistScript(skillDir, script));

        List<Path> files = Files.list(skillDir.resolve("evolution").resolve("scripts")).filter(path -> path.toString().endsWith(".py")).toList();
        assertEquals(1, files.size());
        assertTrue(Files.readString(files.get(0), StandardCharsets.UTF_8).contains("import matplotlib"));
        assertTrue(script.getChange().getContent().startsWith("Script:"));
        assertNotNull(script.getChange().getScriptFilename());
    }

    @Test
    void persistScriptUsesProvidedFilename() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        Path skillDir = prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new EvolutionStore(root.toString());
        EvolutionRecord script = record("ev_script_2", EvolutionTarget.SCRIPT, "Scripts", "console.log('hi')");
        script.getChange().setScriptFilename("hello.js");
        script.getChange().setScriptLanguage("javascript");

        join(new StoreRecordsHelper(store).persistScript(skillDir, script));

        Path scriptPath = skillDir.resolve("evolution").resolve("scripts").resolve("hello.js");
        assertTrue(Files.exists(scriptPath));
        assertTrue(Files.readString(scriptPath, StandardCharsets.UTF_8).contains("console.log"));
    }

    @Test
    void appendScriptRecordPersistsAndRenders() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill A\n\n## Troubleshooting\n- old\n");
        EvolutionStore store = new EvolutionStore(root.toString());
        EvolutionRecord script = record("ev_s1", EvolutionTarget.SCRIPT, "Scripts", "import pandas\ndf = pandas.read_csv('data.csv')");
        script.getChange().setScriptLanguage("python");
        script.getChange().setScriptPurpose("data processing");

        join(store.appendRecord("skill-a", script));

        Path scriptsDir = root.resolve("skill-a").resolve("evolution").resolve("scripts");
        assertEquals(2, Files.list(scriptsDir).count());
        assertTrue(Files.readString(root.resolve("skill-a").resolve("SKILL.md"), StandardCharsets.UTF_8).contains("evolution-index-start"));
    }

    @Test
    void concurrentAppendRecordHasNoDataLoss() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new EvolutionStore(root.toString());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int index = 0; index < 10; index++) {
            futures.add(store.appendRecord("skill-a", record("ev_" + index, EvolutionTarget.BODY, "Troubleshooting", "record " + index))
                    .toCompletableFuture());
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        assertEquals(10, join(store.loadFullEvolutionLog("skill-a")).getEntries().size());
    }

    @Test
    void skillLockIsolationAllowsDifferentSkills() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        prepareSkill(root, "skill-b", "# Skill");
        EvolutionStore store = new EvolutionStore(root.toString());

        CompletableFuture.allOf(
                store.appendRecord("skill-a", record("ev_a", EvolutionTarget.BODY, "Troubleshooting", "a")).toCompletableFuture(),
                store.appendRecord("skill-b", record("ev_b", EvolutionTarget.BODY, "Troubleshooting", "b")).toCompletableFuture()
        ).join();

        assertEquals(1, join(store.loadFullEvolutionLog("skill-a")).getEntries().size());
        assertEquals(1, join(store.loadFullEvolutionLog("skill-b")).getEntries().size());
    }

    @Test
    void renderCreatesSectionFiles() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new EvolutionStore(root.toString());

        join(store.appendRecord("skill-a", record("ev_body_1", EvolutionTarget.BODY, "Troubleshooting", "fix bug")));
        join(store.appendRecord("skill-a", record("ev_body_2", EvolutionTarget.BODY, "Examples", "example case")));

        Path evolutionDir = root.resolve("skill-a").resolve("evolution");
        assertTrue(Files.exists(evolutionDir.resolve("troubleshooting.md")));
        assertTrue(Files.exists(evolutionDir.resolve("examples.md")));
        assertTrue(Files.readString(evolutionDir.resolve("troubleshooting.md"), StandardCharsets.UTF_8).contains("Auto-generated"));
    }

    @Test
    void renderCreatesScriptIndex() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new EvolutionStore(root.toString());
        EvolutionRecord script = record("ev_s1", EvolutionTarget.SCRIPT, "Scripts", "print('hello')");
        script.getChange().setScriptLanguage("python");
        script.getChange().setScriptPurpose("greeting");

        join(store.appendRecord("skill-a", script));

        String index = Files.readString(root.resolve("skill-a").resolve("evolution").resolve("scripts").resolve("_index.md"), StandardCharsets.UTF_8);
        assertTrue(index.contains("Script Index"));
        assertTrue(index.contains("python"));
        assertTrue(index.contains("greeting"));
    }

    @Test
    void renderUpdatesSkillMdIndexBlock() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill A\n\nSome content\n");
        EvolutionStore store = new EvolutionStore(root.toString());

        join(store.appendRecord("skill-a", record("ev_1", EvolutionTarget.BODY, "Troubleshooting", "body fix")));

        String skillMd = Files.readString(root.resolve("skill-a").resolve("SKILL.md"), StandardCharsets.UTF_8);
        assertTrue(skillMd.contains("<!-- evolution-index-start -->"));
        assertTrue(skillMd.contains("Evolution Experiences"));
        assertTrue(skillMd.contains("### Experience Index"));
        assertTrue(skillMd.contains("[evolution/troubleshooting.md#ev_1](evolution/troubleshooting.md#ev_1)"));
        assertFalse(skillMd.contains("### Script Assets"));
    }

    @Test
    void renderUpdatesSkillMdIndexWithScriptAssets() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill A\n\nSome content\n");
        EvolutionStore store = new EvolutionStore(root.toString());
        EvolutionRecord script = record("ev_script", EvolutionTarget.SCRIPT, "Scripts", "print('validate csv')");
        script.getChange().setScriptLanguage("python");
        script.getChange().setScriptPurpose("CSV validation helper");
        script.getChange().setScriptFilename("validate_csv.py");

        join(store.appendRecord("skill-a", script));

        String skillMd = Files.readString(root.resolve("skill-a").resolve("SKILL.md"), StandardCharsets.UTF_8);
        assertTrue(skillMd.contains("Scripts are implementation aids, not mandatory steps."));
        assertTrue(skillMd.contains("### Script Assets"));
        assertTrue(skillMd.contains("evolution/scripts/_index.md"));
        assertTrue(skillMd.contains("[evolution/scripts/validate_csv.py](evolution/scripts/validate_csv.py)"));
        assertTrue(skillMd.contains("CSV validation helper"));
    }

    @Test
    void fullExperienceIndexIncludesLowScoreRecordsAndAnchors() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill A\n\nSome content\n");
        EvolutionStore store = new EvolutionStore(root.toString());
        EvolutionRecord low = record("ev_low", EvolutionTarget.BODY, "Troubleshooting", "### Legacy title\n- details");
        low.setSummary("Use explicit retry budget before rerunning flaky tools.");
        low.setScore(0.2d);
        EvolutionRecord high = record("ev_high", EvolutionTarget.DESCRIPTION, "Instructions", "# Match this skill when users mention audits\n- details");
        high.setScore(0.9d);

        join(store.appendRecord("skill-a", low));
        join(store.appendRecord("skill-a", high));

        String skillMd = Files.readString(root.resolve("skill-a").resolve("SKILL.md"), StandardCharsets.UTF_8);
        String troubleshooting = Files.readString(root.resolve("skill-a").resolve("evolution").resolve("troubleshooting.md"), StandardCharsets.UTF_8);
        String instructions = Files.readString(root.resolve("skill-a").resolve("evolution").resolve("instructions.md"), StandardCharsets.UTF_8);
        assertTrue(skillMd.contains("Use explicit retry budget before rerunning flaky tools."));
        assertTrue(skillMd.contains("| 0.20 | [evolution/troubleshooting.md#ev_low](evolution/troubleshooting.md#ev_low) |"));
        assertTrue(skillMd.contains("Match this skill when users mention audits"));
        assertTrue(troubleshooting.contains("<a id=\"ev_low\"></a>"));
        assertTrue(instructions.contains("<a id=\"ev_high\"></a>"));
    }

    @Test
    void renderReplacesExistingIndexBlock() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill A\n\nContent\n\n<!-- evolution-index-start -->\nold index\n<!-- evolution-index-end -->\n");
        EvolutionStore store = new EvolutionStore(root.toString());

        join(store.appendRecord("skill-a", record("ev_new", EvolutionTarget.BODY, "Troubleshooting", "new fix")));

        String skillMd = Files.readString(root.resolve("skill-a").resolve("SKILL.md"), StandardCharsets.UTF_8);
        assertFalse(skillMd.contains("old index"));
        assertEquals(1, countOccurrences(skillMd, "evolution-index-start"));
    }

    @Test
    void deleteMarkMergeAndUpdatePreserveFacadeBehavior() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new EvolutionStore(root.toString());
        join(store.appendRecord("skill-a", record("ev_1", EvolutionTarget.BODY, "Troubleshooting", "one")));
        join(store.appendRecord("skill-a", record("ev_2", EvolutionTarget.BODY, "Troubleshooting", "two")));
        join(store.appendRecord("skill-a", record("ev_3", EvolutionTarget.BODY, "Troubleshooting", "three")));

        int marked = join(store.markRecordsApplied("skill-a", List.of("ev_1")));
        EvolutionRecord merged = join(store.mergeRecords("skill-a", "ev_2", List.of("ev_3"), "two+three", 0.9d));
        EvolutionRecord updated = join(store.updateRecordContent("skill-a", "ev_2", "two+three+updated", 0.8d));
        int deleted = join(store.deleteRecords("skill-a", List.of("ev_1")));

        EvolutionLog log = join(store.loadFullEvolutionLog("skill-a"));
        assertEquals(1, marked);
        assertNotNull(merged);
        assertEquals("two+three", merged.getChange().getContent());
        assertNotNull(updated);
        assertEquals("two+three+updated", updated.getChange().getContent());
        assertEquals(0.8d, updated.getScore(), 0.0001d);
        assertEquals(1, deleted);
        assertEquals(List.of("ev_2"), log.getEntries().stream().map(EvolutionRecord::getId).toList());
    }

    @Test
    void mergeAndUpdateClearStaleSummary() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new EvolutionStore(root.toString());
        join(store.appendRecord("skill-a", record("ev_1", EvolutionTarget.BODY, "Troubleshooting", "one", "old one summary")));
        join(store.appendRecord("skill-a", record("ev_2", EvolutionTarget.BODY, "Troubleshooting", "two", "old two summary")));

        EvolutionRecord merged = join(store.mergeRecords("skill-a", "ev_1", List.of("ev_2"), "merged content"));
        EvolutionRecord updated = join(store.updateRecordContent("skill-a", "ev_1", "updated content"));

        assertNotNull(merged);
        assertNull(merged.getSummary());
        assertNotNull(updated);
        assertNull(updated.getSummary());
    }

    @Test
    void updateRecordScoresAndGetRecordsByScore() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new EvolutionStore(root.toString());
        EvolutionRecord low = record("ev_low", EvolutionTarget.BODY, "Troubleshooting", "low");
        low.setScore(0.2d);
        EvolutionRecord high = record("ev_high", EvolutionTarget.BODY, "Troubleshooting", "high");
        high.setScore(0.7d);
        join(store.appendRecord("skill-a", low));
        join(store.appendRecord("skill-a", high));
        UsageStats stats = new UsageStats();
        stats.setTimesPresented(3);
        stats.setTimesUsed(2);
        stats.setTimesPositive(1);

        int updated = join(store.updateRecordScores("skill-a", Map.of("ev_low", StoreRecordsHelper.RecordUpdate.of(0.9d, stats))));
        List<EvolutionRecord> ranked = join(store.getRecordsByScore("skill-a", 0.5d));

        assertEquals(1, updated);
        assertEquals(List.of("ev_low", "ev_high"), ranked.stream().map(EvolutionRecord::getId).toList());
        assertEquals(3, ranked.get(0).getUsageStats().getTimesPresented());
        assertEquals(2, ranked.get(0).getUsageStats().getTimesUsed());
    }

    @Test
    void packSkillForSharingOmitsEvolutionIndexBlock() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill A\n\nContent\n");
        EvolutionStore store = new EvolutionStore(root.toString());
        join(store.appendRecord("skill-a", record("ev_1", EvolutionTarget.BODY, "Troubleshooting", "body fix", "check bounds")));
        String localSkillMd = Files.readString(root.resolve("skill-a").resolve("SKILL.md"), StandardCharsets.UTF_8);
        byte[] packageBytes = join(store.packSkillForSharing("skill-a"));
        Path unpacked = Files.createDirectory(tempDir.resolve("unpacked"));
        SkillPackage.unpackSkillPackage(packageBytes, unpacked);

        String packedSkillMd = Files.readString(unpacked.resolve("SKILL.md"), StandardCharsets.UTF_8);
        assertTrue(localSkillMd.contains("evolution-index-start"));
        assertFalse(packedSkillMd.contains("evolution-index-start"));
        assertFalse(packedSkillMd.contains("Experience Index"));
        assertTrue(packedSkillMd.contains("# Skill A"));
    }

    @Test
    void createSkillArchiveAndClearKeepFacadeStable() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        EvolutionStore store = new EvolutionStore(root.toString());

        Path created = join(store.createSkill("skill-a", "desc", "body text"));
        join(store.appendRecord("skill-a", record("ev_1", EvolutionTarget.BODY, "Troubleshooting", "body fix")));
        String bodyArchive = join(store.archiveSkillBody("skill-a"));
        String evoArchive = join(store.archiveEvolutions("skill-a"));
        join(store.clearEvolutions("skill-a"));

        assertEquals(root.resolve("skill-a"), created);
        assertNotNull(bodyArchive);
        assertNotNull(evoArchive);
        assertTrue(Files.exists(root.resolve("skill-a").resolve("archive").resolve(bodyArchive)));
        assertTrue(Files.exists(root.resolve("skill-a").resolve("archive").resolve(evoArchive)));
        assertTrue(join(store.loadFullEvolutionLog("skill-a")).getEntries().isEmpty());
    }

    @Test
    void deleteOrClearLastRecordRemovesStaleProjectionOutputs() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        EvolutionStore store = new EvolutionStore(root.toString());
        join(store.createSkill("skill-a", "desc", "body text"));
        EvolutionRecord first = record("ev_1", EvolutionTarget.BODY, "Troubleshooting", "body fix");
        join(store.appendRecord("skill-a", first));
        Path skillMd = root.resolve("skill-a").resolve("SKILL.md");
        Path section = root.resolve("skill-a").resolve("evolution").resolve("troubleshooting.md");
        assertTrue(Files.exists(section));
        assertTrue(Files.readString(skillMd, StandardCharsets.UTF_8).contains("evolution-index-start"));

        join(store.deleteRecords("skill-a", List.of(first.getId())));
        assertFalse(Files.exists(section));
        assertFalse(Files.readString(skillMd, StandardCharsets.UTF_8).contains("evolution-index-start"));

        join(store.appendRecord("skill-a", record("ev_2", EvolutionTarget.BODY, "Troubleshooting", "body fix 2")));
        assertTrue(Files.exists(section));
        join(store.clearEvolutions("skill-a"));
        assertFalse(Files.exists(section));
        assertFalse(Files.readString(skillMd, StandardCharsets.UTF_8).contains("evolution-index-start"));
    }

    @Test
    void createSkillRejectsExistingOrInvalidNames() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("skills"));
        prepareSkill(root, "skill-a", "# Skill");
        EvolutionStore store = new EvolutionStore(root.toString());

        assertNull(join(store.createSkill("skill-a", "desc", "body")));
        assertNull(join(store.createSkill("../escape", "desc", "body")));
    }

    @Test
    void skillIdAndSharingPackageUsePristineSkillContent() throws Exception {
        Path publisherRoot = Files.createDirectory(tempDir.resolve("publisher"));
        Path installerRoot = Files.createDirectory(tempDir.resolve("installer"));
        prepareSkill(publisherRoot, "skill-a", "# Skill A\n\nContent\n");
        Files.createDirectories(publisherRoot.resolve("skill-a").resolve("scripts"));
        Files.writeString(publisherRoot.resolve("skill-a").resolve("scripts").resolve("helper.py"), "print('hi')", StandardCharsets.UTF_8);
        EvolutionStore publisher = new EvolutionStore(publisherRoot.toString());
        EvolutionStore installer = new EvolutionStore(installerRoot.toString());

        String skillId = join(publisher.ensureSkillId("skill-a"));
        join(publisher.appendRecord("skill-a", record("ev_body", EvolutionTarget.BODY, "Troubleshooting", "body fix")));
        String pristine = join(publisher.readPristineSkillContent("skill-a"));
        byte[] packageBytes = join(publisher.packSkillForSharing("skill-a"));
        Path installed = join(installer.installSkillPackage(packageBytes, "skill-a"));

        assertTrue(skillId.startsWith("sk_"));
        assertFalse(pristine.contains("evolution-index-start"));
        assertNotNull(installed);
        assertTrue(Files.isRegularFile(installed.resolve("SKILL.md")));
        assertTrue(Files.isRegularFile(installed.resolve("scripts").resolve("helper.py")));
        assertEquals(skillId, join(installer.readSkillId("skill-a")));
        assertFalse(Files.readString(installed.resolve("SKILL.md"), StandardCharsets.UTF_8).contains("evolution-index-start"));
    }

    private static Path prepareSkill(Path root, String name, String content) throws Exception {
        Path skillDir = Files.createDirectories(root.resolve(name));
        Files.writeString(skillDir.resolve("SKILL.md"), content, StandardCharsets.UTF_8);
        return skillDir;
    }

    private static EvolutionRecord record(
            String id,
            EvolutionTarget target,
            String section,
            String content
    ) {
        return record(id, target, section, content, null);
    }

    private static EvolutionRecord record(
            String id,
            EvolutionTarget target,
            String section,
            String content,
            String summary
    ) {
        return EvolutionRecord.builder()
                .id(id)
                .source("execution_failure")
                .timestamp("2026-01-01T00:00:00+00:00")
                .context("ctx")
                .change(patch(target, section, content))
                .score(0.8d)
                .usageStats(new UsageStats())
                .summary(summary)
                .build();
    }

    private static EvolutionPatch patch(EvolutionTarget target, String section, String content) {
        return EvolutionPatch.builder()
                .section(section)
                .action(Protocols.APPEND_MODE)
                .content(content)
                .target(target)
                .build();
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static void assertNoFiles(Path path, String suffix) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        assertFalse(Files.list(path).anyMatch(item -> item.getFileName().toString().endsWith(suffix)));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    /**
     * Mirrors Python's mocked {@code sys_operation.fs()} collaborator in
     * {@code tests/unit_tests/agent_evolving/checkpointing/test_evolution_store.py}.
     */
    private static final class FakeSysOperation {
        private final FakeFs fs;

        private FakeSysOperation(FakeFs fs) {
            this.fs = fs;
        }

        public FakeFs fs() {
            return fs;
        }
    }

    /**
     * Mirrors Python's mocked fs object in
     * {@code tests/unit_tests/agent_evolving/checkpointing/test_evolution_store.py}.
     */
    private static final class FakeFs {
        private Object readContent = "";
        private int writeCode;
        private String writeMessage = "ok";
        private String lastReadPath;
        private String lastWritePath;
        private String lastWriteContent;

        public CompletableFuture<ReadFileResult> readFile(String path) {
            lastReadPath = path;
            ReadFileResult result = new ReadFileResult();
            result.setCode(0);
            result.setMessage("ok");
            result.setData(ReadFileData.builder().path(path).content(readContent).mode("text").build());
            return CompletableFuture.completedFuture(result);
        }

        public CompletableFuture<WriteFileResult> writeFile(String path, String content) {
            lastWritePath = path;
            lastWriteContent = content;
            WriteFileResult result = new WriteFileResult();
            result.setCode(writeCode);
            result.setMessage(writeMessage);
            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * Mirrors Python's invalid readback helper in
     * {@code tests/unit_tests/agent_evolving/checkpointing/test_evolution_store.py}.
     */
    private static final class InvalidReadbackStore extends EvolutionStore {
        private InvalidReadbackStore(String skillsBaseDir) {
            super(skillsBaseDir);
        }

        @Override
        public CompletionStage<String> readFileText(Path path) {
            if (path != null && "evolutions.json".equals(path.getFileName().toString())) {
                return CompletableFuture.completedFuture("{not-json");
            }
            return super.readFileText(path);
        }
    }

    /**
     * Mirrors Python's projection write failure branch in
     * {@code tests/unit_tests/agent_evolving/checkpointing/test_evolution_store.py}.
     */
    private static final class ProjectionFailingStore extends EvolutionStore {
        private ProjectionFailingStore(String skillsBaseDir) {
            super(skillsBaseDir);
        }

        @Override
        public CompletionStage<Void> renderEvolutionMarkdown(String name) {
            return CompletableFuture.failedFuture(new IllegalStateException("projection failed"));
        }
    }
}
