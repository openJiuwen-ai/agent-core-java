/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.checkpointing;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionLog;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.FileEvolutionStore;
import com.openjiuwen.agent_evolving.checkpointing.UsageStats;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for EvolutionStore.
 * <p>
 * Mirrors Python's {@code test_evolution_store.py} in
 * {@code tests/unit_tests/agent_evolving/checkpointing/}.
 */
@DisplayName("EvolutionStore Tests")
class TestEvolutionStore {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("init path parse and deduplicate")
    void testInitPathParseAndDeduplicate() throws Exception {
        Path a = tempDir.resolve("a");
        Path b = tempDir.resolve("b");

        FileEvolutionStore store = new FileEvolutionStore(List.of(a, b, a));

        assertThat(store.getBaseDirs()).hasSize(2);
        assertThat(store.getBaseDir()).isEqualTo(a.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("init with empty path raises")
    void testInitWithEmptyPathRaises() {
        assertThatThrownBy(() -> new FileEvolutionStore(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skills_base_dir is empty");
    }

    @Test
    @DisplayName("list skill names and read content")
    void testListSkillNamesAndReadContent() throws Exception {
        FileEvolutionStore store = storeWithSkills();

        assertThat(store.listSkillNames()).containsExactly("skill-a", "skill-b");
        assertThat(store.skillExists("skill-a")).isTrue();
        assertThat(store.readSkillContent("skill-a")).contains("# A");
        assertThat(store.readSkillContent("missing")).isEmpty();
    }

    @Test
    @DisplayName("load full log handles invalid json")
    void testLoadFullLogHandlesInvalidJson() throws Exception {
        FileEvolutionStore store = storeWithSkills();
        Files.writeString(skillDir("skill-a").resolve("evolutions.json"), "{invalid", java.nio.charset.StandardCharsets.UTF_8);

        EvolutionLog log = store.loadFullEvolutionLog("skill-a");
        assertThat(log.getEntries()).isEmpty();
    }

    @Test
    @DisplayName("append record and load with target filter")
    void testAppendRecordAndLoadWithTargetFilter() throws Exception {
        FileEvolutionStore store = storeWithSkills();
        store.appendRecord("skill-a", record("ev_desc", EvolutionTarget.DESCRIPTION, "desc one"));
        store.appendRecord("skill-a", record("ev_body", EvolutionTarget.BODY, "body one"));

        EvolutionLog filtered = store.loadEvolutionLog("skill-a", EvolutionTarget.BODY);
        assertThat(filtered.getEntries()).hasSize(1);
        assertThat(filtered.getEntries().getFirst().getId()).isEqualTo("ev_body");
    }

    @Test
    @DisplayName("append record merges when merge target hit")
    void testAppendRecordMergesWhenMergeTargetHit() throws Exception {
        FileEvolutionStore store = storeWithSkills();
        store.appendRecord("skill-a", record("ev_old", EvolutionTarget.DESCRIPTION, "old"));
        EvolutionPatch patch = patch(EvolutionTarget.DESCRIPTION, "desc merged");
        patch.setMergeTarget("ev_old");
        store.appendRecord("skill-a", record("ev_new", patch));

        EvolutionLog log = store.loadFullEvolutionLog("skill-a");
        assertThat(log.getEntries()).extracting(EvolutionRecord::getId).containsExactly("ev_new");
        assertThat(log.getEntries().getFirst().getChange().getContent()).isEqualTo("desc merged");
    }

    @Test
    @DisplayName("formatting helpers and pending summary")
    void testFormattingHelpersAndPendingSummary() throws Exception {
        FileEvolutionStore store = storeWithSkills();
        store.appendRecord("skill-a", record("ev_desc", EvolutionTarget.DESCRIPTION, "标题\nbody 演进经验"));
        store.appendRecord("skill-a", record("ev_body", EvolutionTarget.BODY, "body one"));
        store.appendRecord("skill-b", record("ev_skip", EvolutionTarget.DESCRIPTION, "ignore"));

        assertThat(store.formatDescExperienceText("skill-a")).contains("标题");
        assertThat(store.formatBodyExperienceText("skill-a")).contains("body 演进经验");
        assertThat(store.formatAllDescExperiences(List.of("skill-a", "skill-b")).get("skill-a")).contains("标题");
        assertThat(store.listPendingSummary(List.of("skill-a", "skill-b"))).contains("skill-a");
        assertThat(store.listPendingSummary(List.of("skill-a", "skill-b"))).contains("description: 1");
    }

    @Test
    @DisplayName("pending summary returns empty text when no records")
    void testPendingSummaryReturnsEmptyTextWhenNoRecords() throws Exception {
        FileEvolutionStore store = storeWithSkills();

        assertThat(store.listPendingSummary(List.of("skill-a"))).isEqualTo("当前所有 Skill 暂无演进信息。");
    }

    @Test
    @DisplayName("read file text with sys operation")
    void testReadFileTextWithSysOperation() {
        FileEvolutionStore store = new FileEvolutionStore(List.of(tempDir));
        Path file = tempDir.resolve("x.txt");
        store.writeFileText(file, "123");

        assertThat(store.readFileText(file)).isEqualTo("123");
    }

    @Test
    @DisplayName("write file text with sys operation")
    void testWriteFileTextWithSysOperation() {
        FileEvolutionStore store = new FileEvolutionStore(List.of(tempDir));
        Path file = tempDir.resolve("y.txt");

        store.writeFileText(file, "hello");

        assertThat(file).exists();
        assertThat(store.readFileText(file)).isEqualTo("hello");
    }

    @Test
    @DisplayName("write file text without sys operation")
    void testWriteFileTextWithoutSysOperation() {
        FileEvolutionStore store = new FileEvolutionStore(List.of(tempDir));
        Path file = tempDir.resolve("z.txt");

        store.writeFileText(file, "hello");

        assertThat(file).exists();
        assertThat(store.readFileText(file)).isEqualTo("hello");
    }

    @Test
    @DisplayName("persist script writes file and replaces content")
    void testPersistScriptWritesFileAndReplacesContent() throws Exception {
        FileEvolutionStore store = storeWithSkills();
        EvolutionRecord record = recordScript("ev_script", "python", "plot", "print('hello')");

        store.appendRecord("skill-a", record);

        Path scriptPath = skillDir("skill-a").resolve("evolution").resolve("scripts").resolve("ev_script_script.py");
        assertThat(scriptPath).exists();
        assertThat(Files.readString(scriptPath)).contains("print('hello')");
        assertThat(store.loadFullEvolutionLog("skill-a").getEntries().getFirst().getChange().getContent())
                .contains("Script: ev_script_script.py");
    }

    @Test
    @DisplayName("persist script uses provided filename")
    void testPersistScriptUsesProvidedFilename() throws Exception {
        FileEvolutionStore store = storeWithSkills();
        EvolutionPatch patch = patch(EvolutionTarget.SCRIPT, "console.log('x')");
        patch.setScriptFilename("custom.js");
        patch.setScriptLanguage("javascript");
        EvolutionRecord record = record("ev_script2", patch);

        store.appendRecord("skill-a", record);

        Path scriptPath = skillDir("skill-a").resolve("evolution").resolve("scripts").resolve("custom.js");
        assertThat(scriptPath).exists();
        assertThat(Files.readString(scriptPath)).contains("console.log");
    }

    @Test
    @DisplayName("append script record persists and renders")
    void testAppendScriptRecordPersistsAndRenders() throws Exception {
        FileEvolutionStore store = storeWithSkills();

        store.appendRecord("skill-a", recordScript("ev_001", "python", "desc", "print('hello')"));

        Path scriptFile = skillDir("skill-a").resolve("evolution").resolve("scripts").resolve("ev_001_script.py");
        assertThat(scriptFile).exists();
        assertThat(skillDir("skill-a").resolve("evolution").resolve("scripts").resolve("_index.md")).exists();
        assertThat(store.readSkillContent("skill-a")).contains("Evolution Experiences");
    }

    @Test
    @DisplayName("concurrent append record no data loss")
    void testConcurrentAppendRecordNoDataLoss() throws Exception {
        FileEvolutionStore store = storeWithSkills();
        var executor = Executors.newFixedThreadPool(4);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int index = i;
            futures.add(CompletableFuture.runAsync(() ->
                    store.appendRecord("skill-a", record("ev_" + index, EvolutionTarget.DESCRIPTION, "desc " + index)),
                    executor));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(store.loadFullEvolutionLog("skill-a").getEntries()).hasSize(10);
    }

    @Test
    @DisplayName("skill lock isolation")
    void testSkillLockIsolation() throws Exception {
        FileEvolutionStore store = storeWithSkills();

        store.appendRecord("skill-a", record("ev_a", EvolutionTarget.DESCRIPTION, "a"));
        store.appendRecord("skill-b", record("ev_b", EvolutionTarget.DESCRIPTION, "b"));

        assertThat(store.loadFullEvolutionLog("skill-a").getEntries()).hasSize(1);
        assertThat(store.loadFullEvolutionLog("skill-b").getEntries()).hasSize(1);
    }

    @Test
    @DisplayName("render creates section files")
    void testRenderCreatesSectionFiles() throws Exception {
        FileEvolutionStore store = storeWithSkills();
        store.appendRecord("skill-a", record("ev_a", EvolutionTarget.DESCRIPTION, "fix bug"));
        store.appendRecord("skill-a", record("ev_b", EvolutionTarget.BODY, "example"));

        assertThat(skillDir("skill-a").resolve("evolution").resolve("troubleshooting.md")).exists();
        assertThat(skillDir("skill-a").resolve("evolution").resolve("examples.md")).exists();
        assertThat(Files.readString(skillDir("skill-a").resolve("evolution").resolve("troubleshooting.md")))
                .contains("fix bug").contains("Auto-generated");
    }

    @Test
    @DisplayName("render creates script index")
    void testRenderCreatesScriptIndex() throws Exception {
        FileEvolutionStore store = storeWithSkills();
        store.appendRecord("skill-a", recordScript("ev_script", "python", "greeting", "print('hello')"));

        Path indexPath = skillDir("skill-a").resolve("evolution").resolve("scripts").resolve("_index.md");
        assertThat(indexPath).exists();
        String index = Files.readString(indexPath);
        assertThat(index).contains("Script Index").contains("python").contains("greeting");
    }

    @Test
    @DisplayName("render updates skill md index block")
    void testRenderUpdatesSkillMdIndexBlock() throws Exception {
        FileEvolutionStore store = storeWithSkills();
        store.appendRecord("skill-a", record("ev_a", EvolutionTarget.DESCRIPTION, "title"));

        String skillMd = store.readSkillContent("skill-a");
        assertThat(skillMd).contains("<!-- evolution-index-start -->")
                .contains("<!-- evolution-index-end -->")
                .contains("Evolution Experiences")
                .contains("**1**");
    }

    @Test
    @DisplayName("render replaces existing index block")
    void testRenderReplacesExistingIndexBlock() throws Exception {
        FileEvolutionStore store = storeWithSkills();
        Files.writeString(skillDir("skill-a").resolve("SKILL.md"), """
                # A

                <!-- evolution-index-start -->
                old index
                <!-- evolution-index-end -->
                """, java.nio.charset.StandardCharsets.UTF_8);
        store.appendRecord("skill-a", record("ev_b", EvolutionTarget.DESCRIPTION, "new index"));

        String skillMd = store.readSkillContent("skill-a");
        assertThat(skillMd).doesNotContain("old index");
        assertThat(skillMd).contains("Evolution Experiences");
        assertThat(skillMd.split("evolution-index-start", -1)).hasSize(2);
    }

    private FileEvolutionStore storeWithSkills() throws Exception {
        Files.createDirectories(skillDir("skill-a"));
        Files.createDirectories(skillDir("skill-b"));
        Files.writeString(skillDir("skill-a").resolve("SKILL.md"), "# A\n", java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(skillDir("skill-b").resolve("SKILL.md"), "# B\n", java.nio.charset.StandardCharsets.UTF_8);
        return new FileEvolutionStore(List.of(tempDir));
    }

    private Path skillDir(String name) {
        return tempDir.resolve(name);
    }

    private static EvolutionRecord record(String id, EvolutionTarget target, String content) {
        return record(id, patch(target, content));
    }

    private static EvolutionRecord recordScript(String id, String language, String purpose, String content) {
        EvolutionPatch patch = patch(EvolutionTarget.SCRIPT, content);
        patch.setScriptLanguage(language);
        patch.setScriptPurpose(purpose);
        return record(id, patch);
    }

    private static EvolutionRecord record(String id, EvolutionPatch patch) {
        return EvolutionRecord.builder()
                .id(id)
                .source("test")
                .timestamp("2026-01-01T00:00:00Z")
                .context("")
                .change(patch)
                .score(0.7)
                .usageStats(new UsageStats())
                .build();
    }

    private static EvolutionPatch patch(EvolutionTarget target, String content) {
        return EvolutionPatch.builder()
                .section(target == EvolutionTarget.DESCRIPTION ? "Troubleshooting" : "Examples")
                .action("append")
                .content(content)
                .target(target)
                .build();
    }
}
