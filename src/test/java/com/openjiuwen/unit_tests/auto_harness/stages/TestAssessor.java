/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.auto_harness.schema.ExperienceType;
import com.openjiuwen.auto_harness.stages.AssessStage;
import com.openjiuwen.core.session.stream.OutputSchema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Assessor stage.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.auto_harness.stages.test_assessor}.
 */
@DisplayName("Assessor Stage Tests")
class TestAssessor {

    @Nested
    @DisplayName("Assess Fallback Tests")
    class TestAssessFallback {

        @Test
        @DisplayName("fallback returns report when agent fails")
        void testFallbackReturnsReport(@TempDir Path tempDir) {
            AutoHarnessConfig config = config(tempDir);
            ExperienceStore store = new ExperienceStore(tempDir.resolve("experience").toString());

            String report = AssessStage.runAssessWithFallback(config, store, failingFactory());

            assertTrue(report.contains("\u8bc4\u4f30\u62a5\u544a"));
            assertTrue(report.length() > 50);
        }

        @Test
        @DisplayName("fallback includes experiences")
        void testFallbackWithExperiences(@TempDir Path tempDir) {
            AutoHarnessConfig config = config(tempDir);
            ExperienceStore store = new ExperienceStore(tempDir.resolve("experience").toString());
            Experience experience = new Experience();
            experience.setType(ExperienceType.FAILURE);
            experience.setTopic("lint-fix");
            experience.setSummary("ruff failed");
            store.record(experience);

            String report = AssessStage.runAssessWithFallback(config, store, failingFactory());

            assertTrue(report.contains("lint-fix"));
        }
    }

    @Nested
    @DisplayName("Assess With Agent Tests")
    class TestAssessWithAgent {

        @Test
        @DisplayName("build query includes Python check strategy")
        void testBuildQueryIncludesPythonCheckStrategy(@TempDir Path tempDir) {
            AutoHarnessConfig config = config(tempDir);
            ExperienceStore store = new ExperienceStore(tempDir.resolve("experience").toString());

            String query = AssessStage.buildQuery(
                    config,
                    store,
                    "\u4f7f\u7528 staged files \u8fd0\u884c make check"
            );

            assertTrue(query.contains("Python \u68c0\u67e5\u7b56\u7565\u5efa\u8bae"));
            assertTrue(query.contains("\u4f7f\u7528 staged files \u8fd0\u884c make check"));
            assertTrue(query.contains("`openjiuwen/harness/**`"));
            assertTrue(query.contains("`openjiuwen/core/**`"));
            assertTrue(query.contains("`openjiuwen/harness/cli/README.md`"));
            assertTrue(query.contains("`tests/**`"));
            assertTrue(query.contains("`examples/**`"));
            assertTrue(query.contains("`docs/en/`"));
            assertTrue(query.contains("`docs/zh/`"));
            assertTrue(query.contains("`openjiuwen/auto_harness/**`"));
        }

        @Test
        @DisplayName("agent report is returned when long enough")
        void testAssessWithAgent(@TempDir Path tempDir) {
            AutoHarnessConfig config = config(tempDir);
            ExperienceStore store = new ExperienceStore(tempDir.resolve("experience").toString());
            String longReport = ("# \u8bc4\u4f30\u62a5\u544a\n## \u6784\u5efa\u72b6\u6001\nOK\n").repeat(10);

            String report = AssessStage.runAssessWithFallback(config, store, chunksFactory(List.of(chunk(longReport))));

            assertTrue(report.contains("\u8bc4\u4f30\u62a5\u544a"));
            assertEquals(longReport, report);
        }

        @Test
        @DisplayName("short agent report triggers fallback")
        void testShortReportTriggersFallback(@TempDir Path tempDir) {
            AutoHarnessConfig config = config(tempDir);
            ExperienceStore store = new ExperienceStore(tempDir.resolve("experience").toString());

            String report = AssessStage.runAssessWithFallback(config, store, chunksFactory(List.of(chunk("too short"))));

            assertTrue(report.contains("\u8bc4\u4f30\u62a5\u544a"));
        }
    }

    @Nested
    @DisplayName("Assess Stream Tests")
    class TestAssessStream {

        @Test
        @DisplayName("run assess stream yields chunks")
        void testAssessStreamYieldsChunks(@TempDir Path tempDir) {
            AutoHarnessConfig config = config(tempDir);
            ExperienceStore store = new ExperienceStore(tempDir.resolve("experience").toString());
            List<Object> chunks = List.of(chunk("part1"), chunk("part2"));

            Iterator<Object> iterator = AssessStage.runAssessStream(config, store, chunksFactory(chunks));
            List<Object> collected = new ArrayList<>();
            iterator.forEachRemaining(collected::add);

            assertEquals(2, collected.size());
            OutputSchema first = (OutputSchema) collected.get(0);
            assertEquals("part1", ((Map<?, ?>) first.getPayload()).get("content"));
        }
    }

    @Nested
    @DisplayName("Assess Check Strategy Tests")
    class TestAssessCheckStrategy {

        @Test
        @DisplayName("format strategy prefers staged make targets")
        void testFormatStrategyPrefersStagedMakeTargets() {
            String strategy = AssessStage.formatPythonCheckStrategy(
                    List.of("openjiuwen/auto_harness/agent.py"),
                    List.of(),
                    List.of()
            );

            assertTrue(strategy.contains("`make check`"));
            assertTrue(strategy.contains("`make type-check`"));
            assertTrue(strategy.contains("staged"));
        }

        @Test
        @DisplayName("format strategy uses explicit tools for worktree delta")
        void testFormatStrategyUsesExplicitToolsForWorktreeDelta() {
            String strategy = AssessStage.formatPythonCheckStrategy(
                    List.of(),
                    List.of("openjiuwen/auto_harness/agent.py"),
                    List.of("tests/unit_tests/auto_harness/test_agent.py")
            );

            assertTrue(strategy.contains("\u4e0d\u8981\u8fd0\u884c `make check COMMITS=1`"));
            assertTrue(strategy.contains("`uv run ruff check <files>`"));
            assertTrue(strategy.contains("`uv run mypy <files>`"));
        }

        @Test
        @DisplayName("format strategy marks empty snapshot as not applicable")
        void testFormatStrategyMarksEmptySnapshotAsNotApplicable() {
            String strategy = AssessStage.formatPythonCheckStrategy(List.of(), List.of(), List.of());

            assertTrue(strategy.contains("No Python files selected"));
            assertTrue(strategy.contains("\u672a\u6267\u884c"));
        }
    }

    private static AutoHarnessConfig config(Path tempDir) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.toString());
        config.setWorkspace(tempDir.toString());
        return config;
    }

    private static AssessStage.AssessAgentFactory failingFactory() {
        return ignored -> inputs -> {
            throw new RuntimeException("no model");
        };
    }

    private static AssessStage.AssessAgentFactory chunksFactory(List<Object> chunks) {
        return ignored -> inputs -> chunks.iterator();
    }

    private static OutputSchema chunk(String content) {
        return new OutputSchema("llm_output", 0, Map.of("content", content));
    }
}
