/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillBranchPreviousStepsTest {

    @Test
    void formatsAssistantToolHistoryAndSkipsUserTurns() {
        String rendered = SkillBranchPreviousSteps.formatPreviousStepsForBranch(
                List.of(
                        Map.of(
                                "role", "assistant",
                                "content", List.of(
                                        Map.of("type", "text", "text", "Need to inspect"),
                                        Map.of("type", "image_url", "image_url", "ignored")
                                ),
                                "tool_calls", List.of(Map.of("name", "browser", "arguments", Map.of("url", "x")))
                        ),
                        Map.of(
                                "role", "tool",
                                "name", "browser",
                                "tool_call_id", "abc",
                                "content", "opened page"
                        ),
                        Map.of("role", "user", "content", "ignored user"),
                        Map.of("role", "assistant", "content", "Done")
                ),
                null,
                10
        );

        assertEquals(
                String.join("\n",
                        "--- Step 1 (assistant) ---",
                        "Need to inspect",
                        "Tool call: browser({\"url\":\"x\"})",
                        "Tool result (browser): opened page",
                        "--- Step 2 (assistant) ---",
                        "Done"),
                rendered
        );
    }

    @Test
    void skipToolCallIdRemovesMatchingToolResultAndTruncatesTurns() {
        String rendered = SkillBranchPreviousSteps.formatPreviousStepsForBranch(
                List.of(
                        Map.of("role", "assistant", "content", "Step one"),
                        Map.of("role", "tool", "name", "one", "tool_call_id", "drop", "content", "hidden"),
                        Map.of("role", "assistant", "content", "Step two"),
                        Map.of("role", "assistant", "content", "Step three")
                ),
                "drop",
                2
        );

        assertTrue(rendered.startsWith("... (1 earlier assistant turn(s) omitted)"));
        assertTrue(!rendered.contains("hidden"));
        assertTrue(rendered.contains("--- Step 1 (assistant) ---\nStep two"));
        assertTrue(rendered.endsWith("--- Step 2 (assistant) ---\nStep three"));
    }

    @Test
    void emptyOrNoAssistantContentUsesPythonFallbacks() {
        assertEquals("(no previous steps)", SkillBranchPreviousSteps.formatPreviousStepsForBranch(List.of(), null, 10));
        assertEquals(
                "(no previous steps)",
                SkillBranchPreviousSteps.formatPreviousStepsForBranch(
                        List.of(Map.of("role", "user", "content", "only user")),
                        null,
                        10
                )
        );
    }
}
