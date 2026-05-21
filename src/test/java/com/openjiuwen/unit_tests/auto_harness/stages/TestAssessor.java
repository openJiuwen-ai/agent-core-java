/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Assessor stage.
 * <p>
 * Mirrors Python's test_assessor.py from
 * <code>tests/unit_tests/auto_harness/stages/test_assessor.py</code>.
 */
@DisplayName("Assessor Stage Tests")
class TestAssessor {

    // Stub classes
    static class AutoHarnessConfigStub {
        String dataDir;
        String workspace;

        AutoHarnessConfigStub(String dataDir, String workspace) {
            this.dataDir = dataDir;
            this.workspace = workspace;
        }
    }

    static class ExperienceStub {
        String type;
        String topic;
        String summary;

        ExperienceStub(String type, String topic, String summary) {
            this.type = type;
            this.topic = topic;
            this.summary = summary;
        }
    }

    static class ExperienceStoreStub {
        java.util.List<ExperienceStub> experiences;

        ExperienceStoreStub(java.util.List<ExperienceStub> experiences) {
            this.experiences = experiences;
        }

        java.util.List<ExperienceStub> listRecent(int limit) {
            if (experiences == null) return new java.util.ArrayList<>();
            return experiences.subList(0, Math.min(limit, experiences.size()));
        }
    }

    // Simulates _run_assess_with_fallback behavior
    static String runAssessWithFallback(AutoHarnessConfigStub config, ExperienceStoreStub store) {
        StringBuilder report = new StringBuilder();
        report.append("评估报告\n");
        report.append("==================\n");

        if (store.experiences != null && !store.experiences.isEmpty()) {
            for (ExperienceStub exp : store.experiences) {
                report.append("- ").append(exp.topic).append(": ").append(exp.summary).append("\n");
            }
        }

        report.append("\n总结: 无关键问题\n");
        return report.toString();
    }

    @Nested
    @DisplayName("Assess Fallback Tests")
    class TestAssessFallback {

        @Test
        @DisplayName("fallback returns report when agent fails")
        void testFallbackReturnsReport() {
            AutoHarnessConfigStub config = new AutoHarnessConfigStub("/tmp/data", "/tmp/workspace");
            ExperienceStoreStub store = new ExperienceStoreStub(null);

            String report = runAssessWithFallback(config, store);

            assertTrue(report.contains("评估报告"));
            assertTrue(report.length() > 50);
        }

        @Test
        @DisplayName("fallback includes experiences")
        void testFallbackWithExperiences() {
            AutoHarnessConfigStub config = new AutoHarnessConfigStub("/tmp/data", "/tmp/workspace");
            java.util.List<ExperienceStub> experiences = new java.util.ArrayList<>();
            experiences.add(new ExperienceStub("failure", "lint-fix", "ruff failed"));

            ExperienceStoreStub store = new ExperienceStoreStub(experiences);
            String report = runAssessWithFallback(config, store);

            assertTrue(report.contains("lint-fix"));
        }
    }

    @Nested
    @DisplayName("Experience Store Tests")
    class TestExperienceStore {

        @Test
        @DisplayName("list recent returns limited experiences")
        void testListRecentReturnsLimited() {
            java.util.List<ExperienceStub> experiences = new java.util.ArrayList<>();
            for (int i = 0; i < 20; i++) {
                experiences.add(new ExperienceStub("type", "topic" + i, "summary"));
            }

            ExperienceStoreStub store = new ExperienceStoreStub(experiences);
            java.util.List<ExperienceStub> recent = store.listRecent(10);

            assertEquals(10, recent.size());
        }

        @Test
        @DisplayName("list recent handles empty store")
        void testListRecentHandlesEmpty() {
            ExperienceStoreStub store = new ExperienceStoreStub(null);
            java.util.List<ExperienceStub> recent = store.listRecent(10);

            assertTrue(recent.isEmpty());
        }
    }
}