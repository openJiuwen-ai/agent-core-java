/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.prompts;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's prompt strategy checks in
 * {@code tests/unit_tests/auto_harness/prompts/test_prompt_strategy.py}.
 */
class PromptStrategyPythonParityTest {

    @Test
    void assessPromptPrefersGithubBeforeWebSearch() throws IOException {
        String content = resource("openjiuwen/auto_harness/prompts/assess.md");

        assertTrue(content.contains("优先通过 bash 工具使用"));
        assertTrue(content.contains("`gh repo view`"));
        assertTrue(content.contains("`gh api`"));
        assertTrue(content.contains("网页搜索和页面抓取作为补充"));
    }

    @Test
    void assessPromptAvoidsCommitsOneForEmptySnapshots() throws IOException {
        String content = resource("openjiuwen/auto_harness/prompts/assess.md");

        assertTrue(content.contains("make check COMMITS=1"));
        assertTrue(content.contains("不要运行"));
        assertTrue(content.contains("No Python files selected"));
        assertTrue(content.contains("uv run ruff check <files>"));
        assertTrue(content.contains("uv run mypy <files>"));
    }

    @Test
    void planPromptPrefersGithubEvidenceForCompetitorTasks() throws IOException {
        String content = resource("openjiuwen/auto_harness/prompts/plan.md");

        assertTrue(content.contains("优先通过 bash 工具使用 `gh repo view`"));
        assertTrue(content.contains("`gh api`"));
        assertTrue(content.contains("网页搜索和页面抓取仅作补充"));
    }

    @Test
    void planPromptAndSkillMergeDependentTasks() throws IOException {
        String prompt = resource("openjiuwen/auto_harness/prompts/plan.md");
        String skill = resource("openjiuwen/auto_harness/skills/plan/SKILL.md");

        for (String content : new String[]{prompt, skill}) {
            assertTrue(content.contains("直接依赖关系") || content.contains("直接代码依赖"));
            assertTrue(content.contains("同一个 worktree") || content.contains("同一个 worktree 内"));
            assertTrue(content.contains("不要拆成多个任务"));
            assertTrue(content.contains("链式任务组") || content.contains("A -> B -> C"));
        }
    }

    @Test
    void planPromptAndSkillRequireSingleTaskOutput() throws IOException {
        String prompt = resource("openjiuwen/auto_harness/prompts/plan.md");
        String skill = resource("openjiuwen/auto_harness/skills/plan/SKILL.md");

        assertTrue(prompt.contains("本轮只输出 1 个任务"));
        assertTrue(prompt.contains("数组中只能有 1 个任务对象"));
        assertTrue(skill.contains("本轮只允许输出 1 个 task"));
        assertTrue(skill.contains("JSON 数组中只能有 1 个任务对象"));
    }

    @Test
    void assessAndPlanPromptsDefineRepoEditScope() throws IOException {
        String assess = resource("openjiuwen/auto_harness/prompts/assess.md");
        String plan = resource("openjiuwen/auto_harness/prompts/plan.md");

        for (String content : new String[]{assess, plan}) {
            assertEditScope(content);
        }
    }

    @Test
    void implementSkillDefinesRepoEditScope() throws IOException {
        String content = resource("openjiuwen/auto_harness/skills/implement/SKILL.md");

        assertEditScope(content);
        assertTrue(content.contains("范围冲突"));
    }

    @Test
    void identityPromptDescribesGithubFirstPolicy() throws IOException {
        String content = resource("openjiuwen/auto_harness/prompts/identity.md");

        assertTrue(content.contains("优先用 `gh` 查看官方仓库"));
        assertTrue(content.contains("网页搜索只作补充核对"));
    }

    private static void assertEditScope(String content) {
        assertTrue(content.contains("`openjiuwen/harness/**`"));
        assertTrue(content.contains("`openjiuwen/core/**`"));
        assertTrue(content.contains("`openjiuwen/harness/cli/README.md`"));
        assertTrue(content.contains("`tests/**`"));
        assertTrue(content.contains("`examples/**`"));
        assertTrue(content.contains("`docs/en/`"));
        assertTrue(content.contains("`docs/zh/`"));
        assertTrue(content.contains("`openjiuwen/auto_harness/**`"));
    }

    private static String resource(String path) throws IOException {
        ClassLoader loader = PromptStrategyPythonParityTest.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
