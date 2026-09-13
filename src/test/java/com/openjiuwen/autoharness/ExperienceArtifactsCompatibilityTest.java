
package com.openjiuwen.autoharness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.autoharness.artifacts.ArtifactStore;
import com.openjiuwen.autoharness.experience.ActiveContextSynthesizer;
import com.openjiuwen.autoharness.experience.ExperienceStore;
import com.openjiuwen.autoharness.schema.Experience;
import com.openjiuwen.autoharness.schema.ExperienceType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class ExperienceArtifactsCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void artifactStoreShouldHandleSessionAndTaskScopes() {
        ArtifactStore store = new ArtifactStore();
        store.put("pipeline", "meta", "");
        store.put("report", "ok", "task-1");

        assertThat(store.has("pipeline", "")).isTrue();
        assertThat(store.require("report", "task-1")).isEqualTo("ok");
        assertThatThrownBy(() -> store.require("missing", "")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing artifact 'missing' in session");
        assertThatThrownBy(() -> store.require("missing", "task-1")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing artifact 'missing' in task=task-1");
        store.resetTask("task-1");
        assertThat(store.has("report", "task-1")).isFalse();
    }

    @Test
    void artifactStoreShouldFallbackToSessionDefaultAndPutMany() {
        ArtifactStore store = new ArtifactStore();
        store.put("shared", "session-value", "");
        store.putMany(Map.of("first", 1, "second", 2), "task-1");

        assertThat(store.get("shared", "task-1", "missing")).isEqualTo("session-value");
        assertThat(store.get("unknown", "task-1", "fallback")).isEqualTo("fallback");
        assertThat(store.require("first", "task-1")).isEqualTo(1);
        assertThat(store.require("second", "task-1")).isEqualTo(2);
        assertThat(store.has("first", "")).isFalse();
        store.resetTask("task-1");
        assertThat(store.get("first", "task-1", "fallback")).isEqualTo("fallback");
    }

    @Test
    void experienceDefaultsShouldMatchPythonSchema() {
        Experience first = Experience.builder().topic("a").build();
        Experience second = Experience.builder().topic("b").build();
        Experience empty = new Experience();

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getId()).hasSize(12).matches("[0-9a-f]{12}");
        assertThat(empty.getId()).hasSize(12).matches("[0-9a-f]{12}");
        assertThat(empty.getTimestamp()).isPositive();
        assertThat(empty.getType()).isEqualTo(ExperienceType.OPTIMIZATION);
        assertThat(empty.getFilesChanged()).isEmpty();
    }

    @Test
    void experienceStoreShouldPreserveSchemaDefaultsAndFieldsWhenRecording() throws Exception {
        ExperienceStore store = new ExperienceStore(tempDir.toString());
        Experience generated = Experience.builder().type(ExperienceType.OPTIMIZATION).topic("release-note")
                .summary("published pr").prUrl("https://gitcode.com/openJiuwen/agent-core/pulls/1")
                .filesChanged(List.of("openjiuwen/auto_harness/schema.py")).build();

        String generatedId = store.record(generated);
        Experience recordedGenerated = store.get(generatedId);
        String persistedJson = Files.readString(tempDir.resolve("experiences.jsonl"));

        assertThat(generatedId).isEqualTo(generated.getId()).hasSize(12).matches("[0-9a-f]{12}");
        assertThat(persistedJson).contains("\"type\":\"optimization\"");
        assertThat(persistedJson).doesNotContain("\"type\":\"OPTIMIZATION\"");
        assertThat(recordedGenerated.getPrUrl()).isEqualTo("https://gitcode.com/openJiuwen/agent-core/pulls/1");
        assertThat(recordedGenerated.getFilesChanged()).containsExactly("openjiuwen/auto_harness/schema.py");

        Experience blankId = Experience.builder().id("").type(ExperienceType.FAILURE).topic("verify-failure")
                .summary("gate failed").prUrl("https://gitcode.com/openJiuwen/agent-core/pulls/2")
                .filesChanged(List.of("openjiuwen/auto_harness/stages/verify.py")).build();

        String repairedId = store.record(blankId);
        Experience recordedBlank = store.get(repairedId);

        assertThat(repairedId).hasSize(12).matches("[0-9a-f]{12}");
        assertThat(recordedBlank.getPrUrl()).isEqualTo("https://gitcode.com/openJiuwen/agent-core/pulls/2");
        assertThat(recordedBlank.getFilesChanged()).containsExactly("openjiuwen/auto_harness/stages/verify.py");
    }

    @Test
    void experienceStoreAndSynthesizerShouldPersistAndSummarize() throws Exception {
        ExperienceStore store = new ExperienceStore(tempDir.toString());
        store.record(Experience.builder().type(ExperienceType.OPTIMIZATION).topic("topic-a").summary("improved plan")
                .build());
        store.record(Experience.builder().type(ExperienceType.FAILURE).topic("topic-b").summary("failed verify")
                .outcome("needs fix").build());

        List<Experience> recent = store.listRecent(10);
        assertThat(recent).hasSize(2);
        assertThat(store.search("verify", 5)).hasSize(1);

        ActiveContextSynthesizer synthesizer = new ActiveContextSynthesizer(tempDir.toString());
        String summary = synthesizer.synthesize(recent, 2000);
        assertThat(summary).contains("近期优化经验");
        assertThat(summary).contains("失败教训");
        assertThat(summary).contains("topic-a");
        assertThat(summary).contains("topic-b");
    }

    @Test
    void experienceStoreShouldDeduplicateSameTypeAndTopicWithinWindowOnly() throws Exception {
        ExperienceStore store = new ExperienceStore(tempDir.toString());

        String first = store.record(Experience.builder().type(ExperienceType.FAILURE).topic("same topic").build());
        String duplicate = store.record(Experience.builder().type(ExperienceType.FAILURE).topic("same topic").build());
        String differentType =
            store.record(Experience.builder().type(ExperienceType.OPTIMIZATION).topic("same topic").build());

        assertThat(first).isNotBlank();
        assertThat(duplicate).isEmpty();
        assertThat(differentType).isNotBlank();
    }

    @Test
    void experienceStoreShouldSearchByTokenizedHitsAndRecency() throws Exception {
        ExperienceStore store = new ExperienceStore(tempDir.toString());
        long now = System.currentTimeMillis() / 1000;
        store.record(Experience.builder().type(ExperienceType.OPTIMIZATION).id("old").topic("fix timeout")
                .summary("increased limit").timestamp(now - 31L * 86_400L).build());
        store.record(Experience.builder().type(ExperienceType.INSIGHT).id("new").topic("fix timeout")
                .summary("newer signal").timestamp(now).build());
        store.record(Experience.builder().type(ExperienceType.INSIGHT).id("other").topic("refactor logging")
                .summary("structured logger").timestamp(now).build());

        assertThat(store.search("", 5)).isEmpty();
        assertThat(store.search("fix", 1)).extracting(Experience::getId).containsExactly("new");
        assertThat(store.search("missing", 5)).isEmpty();
        assertThat(store.listRecent(1)).extracting(Experience::getId).containsExactly("new");
        assertThat(store.get("nope")).isNull();
    }

    @Test
    void experienceStoreShouldSkipMalformedJsonlLinesWhenReading() throws Exception {
        Files.writeString(tempDir.resolve("experiences.jsonl"), """
                {"id":"valid-1","type":"OPTIMIZATION","topic":"valid","summary":"kept","timestamp":200}
                {not valid json}
                {"id":"valid-2","type":"INSIGHT","topic":"newer","summary":"also kept","timestamp":300}
                """);
        ExperienceStore store = new ExperienceStore(tempDir.toString());

        assertThat(store.get("valid-1")).isNotNull();
        assertThat(store.listRecent(10)).extracting(Experience::getId).containsExactly("valid-2", "valid-1");
        assertThat(store.search("kept", 10)).extracting(Experience::getId).containsExactlyInAnyOrder("valid-1",
                "valid-2");
    }

    @Test
    void experienceStoreHelpersShouldMirrorPythonScoringFunctions() {
        Experience exp =
            Experience.builder().topic("fix timeout").summary("increased limit").details("was 60s").build();
        long now = System.currentTimeMillis() / 1000;

        assertThat(ExperienceStore.tokenize("Fix the BUG now")).contains("fix", "the", "bug");
        assertThat(ExperienceStore.tokenize("a bb ccc")).doesNotContain("a").contains("bb");
        assertThat(ExperienceStore.countHits(List.of("fix", "timeout"), exp)).isEqualTo(2);
        assertThat(ExperienceStore.countHits(List.of("missing"), exp)).isZero();
        assertThat(ExperienceStore.recencyScore(now - 60, now)).isGreaterThan(0.99);
        assertThat(ExperienceStore.recencyScore(now - 31L * 86_400L, now)).isZero();
    }

    @Test
    void activeContextSynthesizerShouldSortWithinTypeByTimeWeightAndLimitChars() {
        ActiveContextSynthesizer synthesizer = new ActiveContextSynthesizer(tempDir.toString());
        long now = System.currentTimeMillis() / 1000;

        String summary = synthesizer.synthesize(List.of(
                Experience.builder().type(ExperienceType.OPTIMIZATION).topic("old").summary("old summary")
                        .timestamp(now - 8L * 86_400L).build(),
                Experience.builder().type(ExperienceType.OPTIMIZATION).topic("new").summary("new summary")
                        .timestamp(now - 60).build(),
                Experience.builder().type(ExperienceType.OPTIMIZATION).topic("medium").summary("medium summary")
                        .timestamp(now - 2L * 86_400L).build()),
                2000);

        assertThat(summary.indexOf("new: new summary")).isLessThan(summary.indexOf("medium: medium summary"));
        assertThat(summary.indexOf("medium: medium summary")).isLessThan(summary.indexOf("old: old summary"));
        assertThat(synthesizer.synthesize(List.of(Experience.builder().type(ExperienceType.INSIGHT).topic("long")
                .summary("abcdefghijklmnopqrstuvwxyz").timestamp(now).build()), 5).length()).isLessThanOrEqualTo(16);
    }
}
