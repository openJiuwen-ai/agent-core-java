/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.experience;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests active-context synthesis from experience records.
 *
 * <p>Mirrors Python's {@code ActiveContextSynthesizer} behavior in
 * {@code openjiuwen/auto_harness/experience/synthesizer.py}.</p>
 */
class ActiveContextSynthesizerTest {

    @TempDir
    private Path tempDir;

    @Test
    void synthesizeReturnsEmptyForNoExperiences() {
        ActiveContextSynthesizer synthesizer = new ActiveContextSynthesizer(tempDir.toString());

        String result = synthesizer.synthesize(List.of()).join();

        assertThat(result).isEmpty();
    }

    @Test
    void synthesizeGroupsByTypeOrderAndSortsByTimeWeight() {
        double now = System.currentTimeMillis() / 1000.0;
        Experience recentOptimization = experience(ExperienceType.OPTIMIZATION, "recent-opt", "new summary",
                "success", now);
        Experience olderOptimization = experience(ExperienceType.OPTIMIZATION, "old-opt", "old summary",
                "success", now - 2.0 * 86_400.0);
        Experience failure = experience(ExperienceType.FAILURE, "failure", "failed", "retry", now);
        Experience insight = experience(ExperienceType.INSIGHT, "insight", "learned", "", now);
        ActiveContextSynthesizer synthesizer = new ActiveContextSynthesizer(tempDir.toString());

        String result = synthesizer.synthesize(List.of(failure, olderOptimization, insight, recentOptimization)).join();

        assertThat(result.indexOf("### 近期优化经验")).isLessThan(result.indexOf("### 失败教训"));
        assertThat(result.indexOf("### 失败教训")).isLessThan(result.indexOf("### 关键洞察"));
        assertThat(result.indexOf("- recent-opt: new summary (success)"))
                .isLessThan(result.indexOf("- old-opt: old summary (success)"));
        assertThat(result).contains("- insight: learned");
    }

    @Test
    void formatLineOmitsOutcomeWhenEmpty() {
        Experience experience = experience(ExperienceType.INSIGHT, "topic", "summary", "", 1.0);

        String line = ActiveContextSynthesizer.formatLine(experience);

        assertThat(line).isEqualTo("- topic: summary");
    }

    @Test
    void synthesizeTruncatesSectionByTokenBudget() {
        ActiveContextSynthesizer synthesizer = new ActiveContextSynthesizer(tempDir.toString());

        String result = synthesizer.synthesize(List.of(
                experience(ExperienceType.OPTIMIZATION, "topic", "summary", "ok", 1.0)), 1).join();

        assertThat(result).isEqualTo("###");
    }

    @Test
    void loadAndSynthesizeReadsRecentExperiences() {
        ExperienceStore store = new ExperienceStore(tempDir);
        store.record(experience(ExperienceType.OPTIMIZATION, "loaded-topic", "loaded summary", "success",
                System.currentTimeMillis() / 1000.0)).join();
        ActiveContextSynthesizer synthesizer = new ActiveContextSynthesizer(tempDir.toString());

        String result = synthesizer.loadAndSynthesize(30).join();

        assertThat(result).contains("### 近期优化经验");
        assertThat(result).contains("- loaded-topic: loaded summary (success)");
    }

    @Test
    void timeWeightUsesPythonAgeBrackets() {
        double now = 10_000_000.0;

        assertThat(ActiveContextSynthesizer.timeWeight(now - 60.0, now)).isEqualTo(1.0);
        assertThat(ActiveContextSynthesizer.timeWeight(now - 2.0 * 86_400.0, now)).isEqualTo(0.5);
        assertThat(ActiveContextSynthesizer.timeWeight(now - 8.0 * 86_400.0, now)).isEqualTo(0.2);
    }

    private static Experience experience(ExperienceType type, String topic, String summary, String outcome,
                                         double timestamp) {
        return Experience.builder()
                .type(type)
                .topic(topic)
                .summary(summary)
                .outcome(outcome)
                .timestamp(timestamp)
                .build();
    }
}
