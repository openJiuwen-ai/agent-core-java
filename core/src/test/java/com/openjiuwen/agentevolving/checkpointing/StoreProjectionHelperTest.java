/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.checkpointing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreProjectionHelperTest {

    @TempDir
    Path tempDir;

    @Test
    void renderEvolutionMarkdownWritesSectionFilesAndInjectsSkillIndex() throws Exception {
        FakeStore store = new FakeStore(tempDir.resolve("skills"));
        Path skillDir = store.prepareSkill("demo", """
                ---
                name: demo
                description: sample
                ---

                # demo
                """);
        store.logs.put("demo", new EvolutionLog(
                "demo",
                "1.0.0",
                "2026-06-09T12:00:00Z",
                List.of(
                        record("ev_body", "Troubleshooting", EvolutionTarget.BODY, "# Title\nUse body details", 0.7, "summary line"),
                        record("ev_script", "Scripts", EvolutionTarget.SCRIPT, "echo hi", 0.9, null)
                )));
        StoreProjectionHelper helper = new StoreProjectionHelper(store);

        helper.renderEvolutionMarkdown("demo").toCompletableFuture().join();

        String sectionMarkdown = Files.readString(skillDir.resolve("evolution").resolve("troubleshooting.md"));
        String scriptIndex = Files.readString(skillDir.resolve("evolution").resolve("scripts").resolve("_index.md"));
        String skillMd = Files.readString(skillDir.resolve("SKILL.md"));

        assertTrue(sectionMarkdown.contains("### [ev_body] summary line"));
        assertTrue(sectionMarkdown.contains("Use body details"));
        assertTrue(scriptIndex.contains("| [ev_script](ev_script) | unknown |  | 2026-06-09 |"));
        assertTrue(skillMd.contains("<!-- evolution-index-start -->"));
        assertTrue(skillMd.contains("This skill has accumulated **2** evolution experiences (1 body, 1 script)."));
        assertTrue(skillMd.contains("evolution/troubleshooting.md#ev_body"));
    }

    @Test
    void clearRenderedOutputsRemovesEvolutionDirAndInjectedIndexBlock() throws Exception {
        FakeStore store = new FakeStore(tempDir.resolve("skills"));
        Path skillDir = store.prepareSkill("demo", """
                ---
                name: demo
                description: sample
                ---

                # demo

                <!-- evolution-index-start -->
                old block
                <!-- evolution-index-end -->
                """);
        Files.createDirectories(skillDir.resolve("evolution").resolve("scripts"));
        Files.writeString(skillDir.resolve("evolution").resolve("troubleshooting.md"), "stale");
        Files.writeString(skillDir.resolve("evolution").resolve("scripts").resolve("_index.md"), "stale");
        StoreProjectionHelper helper = new StoreProjectionHelper(store);

        helper.clearRenderedOutputs(skillDir).toCompletableFuture().join();

        assertFalse(Files.exists(skillDir.resolve("evolution")));
        assertFalse(Files.readString(skillDir.resolve("SKILL.md")).contains("evolution-index-start"));
    }

    @Test
    void formatPendingExperienceTextsFollowPythonLayout() throws Exception {
        FakeStore store = new FakeStore(tempDir.resolve("skills"));
        store.prepareSkill("demo", "# demo\n");
        store.pendingByTarget.put(key("demo", EvolutionTarget.DESCRIPTION), List.of(
                record("ev_desc", "Instructions", EvolutionTarget.DESCRIPTION, "First line\n- detail one", 0.8, null)
        ));
        store.pendingByTarget.put(key("demo", EvolutionTarget.BODY), List.of(
                record("ev_body", "Troubleshooting", EvolutionTarget.BODY, "Fix title\n- detail two", 0.6, null)
        ));
        StoreProjectionHelper helper = new StoreProjectionHelper(store);

        String descText = helper.formatDescExperienceText("demo").toCompletableFuture().join();
        String allDesc = helper.formatAllDescExperiences(List.of("demo")).toCompletableFuture().join().get("demo");
        String bodyText = helper.formatBodyExperienceText("demo").toCompletableFuture().join();
        String pendingSummary = helper.listPendingSummary(List.of("demo")).toCompletableFuture().join();

        assertEquals("- First line\n- detail one", descText);
        assertEquals(descText, allDesc);
        assertTrue(bodyText.contains("# Skill 'demo' body 演进经验"));
        assertTrue(bodyText.contains("1. **[Troubleshooting]** Fix title"));
        assertTrue(pendingSummary.contains("共 2 条 pending 经验"));
        assertTrue(pendingSummary.contains("[description] **First line**"));
        assertTrue(pendingSummary.contains("[body] **Fix title**"));
    }

    @Test
    void extractDescriptionFromSkillMdReadsFrontMatter() {
        String content = """
                ---
                name: demo
                description: sample description
                ---

                # demo
                """;

        assertEquals("sample description", StoreProjectionHelper.extractDescriptionFromSkillMd(content));
        assertEquals("", StoreProjectionHelper.extractDescriptionFromSkillMd("# demo"));
    }

    private static EvolutionRecord record(
            String id,
            String section,
            EvolutionTarget target,
            String content,
            double score,
            String summary
    ) {
        EvolutionPatch patch = EvolutionPatch.builder()
                .section(section)
                .action(Protocols.APPEND_MODE)
                .content(content)
                .target(target)
                .summary(summary)
                .build();
        return EvolutionRecord.builder()
                .id(id)
                .source("source")
                .timestamp("2026-06-09T12:00:00Z")
                .context("context")
                .change(patch)
                .score(score)
                .summary(summary)
                .build();
    }

    private static String key(String name, EvolutionTarget target) {
        return name + "::" + target.getValue();
    }

    private static final class FakeStore implements StoreProjectionHelper.StoreProjectionStore {

        private final Path root;
        private final Map<String, EvolutionLog> logs = new LinkedHashMap<>();
        private final Map<String, List<EvolutionRecord>> pendingByTarget = new LinkedHashMap<>();

        private FakeStore(Path root) {
            this.root = root;
        }

        private Path prepareSkill(String name, String skillMdContent) throws IOException {
            Path skillDir = resolveSkillDir(name);
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), skillMdContent, StandardCharsets.UTF_8);
            return skillDir;
        }

        @Override
        public Path resolveSkillDir(String name) {
            return root.resolve(name);
        }

        @Override
        public CompletionStage<EvolutionLog> loadFullEvolutionLog(String name) {
            EvolutionLog log = logs.getOrDefault(name, EvolutionLog.empty(name));
            return CompletableFuture.completedFuture(log);
        }

        @Override
        public CompletionStage<List<EvolutionRecord>> getPendingRecords(String name, EvolutionTarget target) {
            return CompletableFuture.completedFuture(new ArrayList<>(pendingByTarget.getOrDefault(key(name, target), List.of())));
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
    }
}
