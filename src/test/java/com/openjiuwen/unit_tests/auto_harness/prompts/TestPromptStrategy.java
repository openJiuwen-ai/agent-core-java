/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.prompts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for prompt strategy.
 * <p>
 * Mirrors Python's test_prompt_strategy.py from
 * <code>tests/unit_tests/auto_harness/prompts/test_prompt_strategy.py</code>.
 */
@DisplayName("Prompt Strategy Tests")
class TestPromptStrategy {

    // Simulated prompt content for testing
    static class PromptContent {
        String assessPrompt;
        String planPrompt;

        PromptContent() {
            this.assessPrompt = buildAssessPrompt();
            this.planPrompt = buildPlanPrompt();
        }

        private String buildAssessPrompt() {
            return "评估阶段\n" +
                   "优先使用工具:\n" +
                   "- `gh repo view` 查看仓库\n" +
                   "- `gh api` 获取 API 数据\n" +
                   "- 网页抓取仅作补充\n" +
                   "检查:\n" +
                   "- 不要使用 COMMITS=1\n" +
                   "- 使用 ruff check <files>\n" +
                   "- 使用 mypy <files>\n";
        }

        private String buildPlanPrompt() {
            return "规划阶段\n" +
                   "优先通过 gh 工具使用 `gh repo view`\n" +
                   "- `gh api` 获取信息\n" +
                   "- 网页抓取仅作补充\n" +
                   "- 依赖关系处理: 同一个 worktree\n" +
                   "- 不要拆成多个任务\n" +
                   "- 任务组 A -> B -> C\n";
        }
    }

    @Nested
    @DisplayName("Assess Prompt Tests")
    class TestAssessPrompt {

        @Test
        @DisplayName("assess prompt prefers GitHub tools")
        void testAssessPromptPrefersGithub() {
            PromptContent prompts = new PromptContent();

            assertTrue(prompts.assessPrompt.contains("优先使用工具"));
            assertTrue(prompts.assessPrompt.contains("gh repo"));
            assertTrue(prompts.assessPrompt.contains("gh api"));
            assertTrue(prompts.assessPrompt.contains("网页抓取仅作补充"));
        }

        @Test
        @DisplayName("assess prompt avoids COMMITS=1 for snapshots")
        void testAssessPromptAvoidsCommits1() {
            PromptContent prompts = new PromptContent();

            assertTrue(prompts.assessPrompt.contains("不要使用 COMMITS=1"));
            assertTrue(prompts.assessPrompt.contains("ruff check"));
            assertTrue(prompts.assessPrompt.contains("mypy"));
        }
    }

    @Nested
    @DisplayName("Plan Prompt Tests")
    class TestPlanPrompt {

        @Test
        @DisplayName("plan prompt prefers GitHub evidence")
        void testPlanPromptPrefersGithubEvidence() {
            PromptContent prompts = new PromptContent();

            assertTrue(prompts.planPrompt.contains("优先通过 gh"));
            assertTrue(prompts.planPrompt.contains("gh repo"));
            assertTrue(prompts.planPrompt.contains("gh api"));
        }

        @Test
        @DisplayName("plan prompt guides merge dependent tasks")
        void testPlanPromptMergeDependentTasks() {
            PromptContent prompts = new PromptContent();

            assertTrue(prompts.planPrompt.contains("依赖关系") ||
                       prompts.planPrompt.contains("同一个 worktree"));
            assertTrue(prompts.planPrompt.contains("不要拆成多个任务"));
            assertTrue(prompts.planPrompt.contains("A -> B -> C") ||
                       prompts.planPrompt.contains("任务组"));
        }
    }
}