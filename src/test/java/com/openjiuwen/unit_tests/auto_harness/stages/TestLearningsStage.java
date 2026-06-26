/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.SessionResultsArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.stages.LearningsStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code LearningsStage} and {@code run_learnings} in
 * {@code openjiuwen/auto_harness/stages/learnings.py}.</p>
 */
class TestLearningsStage {

    @TempDir
    private Path tempDir;

    @Test
    void metadataMatchesPythonStageShape() {
        LearningsStage stage = new LearningsStage();

        assertThat(stage.name()).isEqualTo("learnings");
        assertThat(stage.slot()).isEqualTo("learnings");
        assertThat(stage.displayName()).isEqualTo("总结经验");
        assertThat(stage.description()).isEqualTo("Record learnings after a session.");
        assertThat(stage.consumes()).containsExactly("session_results");
        assertThat(stage.produces()).containsExactly("session_results");
    }

    @Test
    void streamRecordsParsedLearningAndPreservesSessionResults() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.resolve("data").toString());
        config.setWorkspace(tempDir.toString());
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config, null);
        ExperienceStore store = new ExperienceStore(tempDir.resolve("experience"));
        store.record(Experience.builder()
                .type(ExperienceType.FAILURE)
                .topic("old-timeout")
                .summary("retry once")
                .build()).join();
        orchestrator.setExperienceStore(store);
        SessionContext ctx = new SessionContext(orchestrator);
        CycleResult result = CycleResult.builder()
                .success(true)
                .summary("fixed lint")
                .reverted(false)
                .build();
        ctx.putArtifact("session_results", SessionResultsArtifact.builder()
                .results(List.of(result))
                .build());
        AtomicReference<String> seenResults = new AtomicReference<>();
        AtomicReference<String> seenMemories = new AtomicReference<>();
        AtomicReference<Map<String, Object>> seenInputs = new AtomicReference<>();
        LearningsStage stage = new LearningsStage((ignoredConfig, sessionResults, existingMemories, ignoredRails) -> {
            seenResults.set(sessionResults);
            seenMemories.set(existingMemories);
            return inputs -> {
                seenInputs.set(inputs);
                return List.of(new OutputSchema("message", 0, Map.of(
                        "content",
                        """
                                [
                                  {"type": "insight", "topic": "lint", "summary": "run focused checks", "details": "keep scope small"}
                                ]
                                """
                ))).iterator();
            };
        });

        List<Object> events = collect(stage.stream(ctx));

        assertThat(events.getFirst()).isInstanceOf(OutputSchema.class);
        StageResult stageResult = (StageResult) events.get(1);
        SessionResultsArtifact artifact = (SessionResultsArtifact) stageResult.getArtifacts().get("session_results");
        assertThat(artifact.getResults()).containsExactly(result);
        assertThat(seenResults.get()).contains("- fixed lint (success=True, reverted=False)");
        assertThat(seenMemories.get()).contains("[failure] old-timeout: retry once");
        assertThat(String.valueOf(seenInputs.get().get("query"))).contains("本次 session 结果");
        assertThat(store.listRecent(10).join())
                .extracting(Experience::getTopic)
                .contains("lint");
        Experience recorded = store.search("focused", 5).join().getFirst();
        assertThat(recorded.getType()).isEqualTo(ExperienceType.INSIGHT);
        assertThat(recorded.getSummary()).isEqualTo("run focused checks");
        assertThat(recorded.getDetails()).isEqualTo("keep scope small");
    }

    @Test
    void runLearningsSkipsAgentWhenNoResults() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);

        Iterator<Object> events = LearningsStage.runLearnings(
                new AutoHarnessConfig(),
                List.of(),
                new ExperienceStore(tempDir.resolve("experience-empty")),
                List.of(),
                (config, sessionResults, existingMemories, rails) -> {
                    called.set(true);
                    return inputs -> List.of().iterator();
                }
        );

        assertThat(events.hasNext()).isFalse();
        assertThat(called.get()).isFalse();
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }
}
