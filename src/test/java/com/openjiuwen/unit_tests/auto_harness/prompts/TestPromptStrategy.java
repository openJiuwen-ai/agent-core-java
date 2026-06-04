/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.prompts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prompt strategy tests.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.auto_harness.prompts.test_prompt_strategy}.</p>
 */
@DisplayName("Prompt Strategy Tests")
class TestPromptStrategy {
    private static final String ASSESS_PROMPT = "auto_harness/prompts/assess.md";
    private static final String PLAN_PROMPT = "auto_harness/prompts/plan.md";
    private static final String IDENTITY_PROMPT = "auto_harness/prompts/identity.md";
    private static final String PLAN_SKILL = "auto_harness/skills/plan/SKILL.md";
    private static final String IMPLEMENT_SKILL = "auto_harness/skills/implement/SKILL.md";

    @Test
    @DisplayName("assess prompt prefers GitHub before web search")
    void testAssessPromptPrefersGithubBeforeWebSearch() throws IOException {
        String content = readResource(ASSESS_PROMPT);

        assertAll(
            () -> assertTrue(content.contains("\u4f18\u5148\u901a\u8fc7 bash \u5de5\u5177\u4f7f\u7528")),
            () -> assertTrue(content.contains("`gh repo view`")),
            () -> assertTrue(content.contains("`gh api`")),
            () -> assertTrue(content.contains("\u7f51\u9875\u641c\u7d22\u548c\u9875\u9762\u6293\u53d6\u4f5c\u4e3a\u8865\u5145"))
        );
    }

    @Test
    @DisplayName("assess prompt avoids COMMITS=1 for empty snapshots")
    void testAssessPromptAvoidsCommits1ForEmptySnapshots() throws IOException {
        String content = readResource(ASSESS_PROMPT);

        assertAll(
            () -> assertTrue(content.contains("make check COMMITS=1")),
            () -> assertTrue(content.contains("\u4e0d\u8981\u8fd0\u884c")),
            () -> assertTrue(content.contains("No Python files selected")),
            () -> assertTrue(content.contains("uv run ruff check <files>")),
            () -> assertTrue(content.contains("uv run mypy <files>"))
        );
    }

    @Test
    @DisplayName("plan prompt prefers GitHub evidence for competitor tasks")
    void testPlanPromptPrefersGithubEvidenceForCompetitorTasks() throws IOException {
        String content = readResource(PLAN_PROMPT);

        assertAll(
            () -> assertTrue(content.contains("\u4f18\u5148\u901a\u8fc7 bash \u5de5\u5177\u4f7f\u7528 `gh repo view`")),
            () -> assertTrue(content.contains("`gh api`")),
            () -> assertTrue(content.contains("\u7f51\u9875\u641c\u7d22\u548c\u9875\u9762\u6293\u53d6\u4ec5\u4f5c\u8865\u5145"))
        );
    }

    @Test
    @DisplayName("plan prompt and skill merge dependent tasks")
    void testPlanPromptAndSkillMergeDependentTasks() throws IOException {
        String prompt = readResource(PLAN_PROMPT);
        String skill = readResource(PLAN_SKILL);

        for (String content : new String[] {prompt, skill}) {
            assertAll(
                () -> assertContainsAny(
                    content,
                    "\u76f4\u63a5\u4f9d\u8d56\u5173\u7cfb",
                    "\u76f4\u63a5\u4ee3\u7801\u4f9d\u8d56"
                ),
                () -> assertContainsAny(
                    content,
                    "\u540c\u4e00\u4e2a worktree",
                    "\u540c\u4e00\u4e2a worktree \u5185"
                ),
                () -> assertTrue(content.contains("\u4e0d\u8981\u62c6\u6210\u591a\u4e2a\u4efb\u52a1")),
                () -> assertContainsAny(content, "\u94fe\u5f0f\u4efb\u52a1\u7ec4", "A -> B -> C")
            );
        }
    }

    @Test
    @DisplayName("plan prompt and skill require single task output")
    void testPlanPromptAndSkillRequireSingleTaskOutput() throws IOException {
        String prompt = readResource(PLAN_PROMPT);
        String skill = readResource(PLAN_SKILL);

        assertAll(
            () -> assertTrue(prompt.contains("\u672c\u8f6e\u53ea\u8f93\u51fa 1 \u4e2a\u4efb\u52a1")),
            () -> assertTrue(prompt.contains("\u6570\u7ec4\u4e2d\u53ea\u80fd\u6709 1 \u4e2a\u4efb\u52a1\u5bf9\u8c61")),
            () -> assertTrue(skill.contains("\u672c\u8f6e\u53ea\u5141\u8bb8\u8f93\u51fa 1 \u4e2a task")),
            () -> assertTrue(skill.contains("JSON \u6570\u7ec4\u4e2d\u53ea\u80fd\u6709 1 \u4e2a\u4efb\u52a1\u5bf9\u8c61"))
        );
    }

    @Test
    @DisplayName("assess and plan prompts define repo edit scope")
    void testAssessAndPlanPromptsDefineRepoEditScope() throws IOException {
        String assess = readResource(ASSESS_PROMPT);
        String plan = readResource(PLAN_PROMPT);

        for (String content : new String[] {assess, plan}) {
            assertAll(
                () -> assertTrue(content.contains("`openjiuwen/harness/**`")),
                () -> assertTrue(content.contains("`openjiuwen/core/**`")),
                () -> assertTrue(content.contains("`openjiuwen/harness/cli/README.md`")),
                () -> assertTrue(content.contains("`tests/**`")),
                () -> assertTrue(content.contains("`examples/**`")),
                () -> assertTrue(content.contains("`docs/en/`")),
                () -> assertTrue(content.contains("`docs/zh/`")),
                () -> assertTrue(content.contains("`openjiuwen/auto_harness/**`"))
            );
        }
    }

    @Test
    @DisplayName("implement skill defines repo edit scope")
    void testImplementSkillDefinesRepoEditScope() throws IOException {
        String content = readResource(IMPLEMENT_SKILL);

        assertAll(
            () -> assertTrue(content.contains("`openjiuwen/harness/**`")),
            () -> assertTrue(content.contains("`openjiuwen/core/**`")),
            () -> assertTrue(content.contains("`openjiuwen/harness/cli/README.md`")),
            () -> assertTrue(content.contains("`tests/**`")),
            () -> assertTrue(content.contains("`examples/**`")),
            () -> assertTrue(content.contains("`docs/en/`")),
            () -> assertTrue(content.contains("`docs/zh/`")),
            () -> assertTrue(content.contains("`openjiuwen/auto_harness/**`")),
            () -> assertTrue(content.contains("\u8303\u56f4\u51b2\u7a81"))
        );
    }

    @Test
    @DisplayName("identity prompt describes GitHub-first policy")
    void testIdentityPromptDescribesGithubFirstPolicy() throws IOException {
        String content = readResource(IDENTITY_PROMPT);

        assertAll(
            () -> assertTrue(content.contains("\u4f18\u5148\u7528 `gh` \u67e5\u770b\u5b98\u65b9\u4ed3\u5e93")),
            () -> assertTrue(content.contains("\u7f51\u9875\u641c\u7d22\u53ea\u4f5c\u8865\u5145\u6838\u5bf9"))
        );
    }

    private static String readResource(String name) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(name)) {
            assertNotNull(input, name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertContainsAny(String content, String first, String second) {
        assertTrue(content.contains(first) || content.contains(second));
    }
}
