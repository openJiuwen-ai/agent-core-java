/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.tools.mobile_gui.test_skill_branch_previous_steps} in
 * {@code tests/unit_tests/harness/tools/mobile_gui/test_skill_branch_previous_steps.py}.
 */
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

    @Test
    void omitsInitialUserAndScreenshotObservations() {
        String rendered = SkillBranchPreviousSteps.formatPreviousStepsForBranch(
                List.of(
                        Map.of("role", "user", "content", "Open GitHub and find the README."),
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "text", "text", "Current foreground app: com.android.chrome"),
                                        Map.of("type", "image_url", "image_url", Map.of("url", "data:image/jpeg;base64,AAA"))
                                )
                        ),
                        Map.of("role", "assistant", "content", "I will tap the browser icon.")
                ),
                null,
                10
        );

        assertFalse(rendered.contains("User (task/query)"));
        assertFalse(rendered.contains("Open GitHub"));
        assertFalse(rendered.contains("Current foreground app"));
        assertTrue(rendered.contains("--- Step"));
        assertTrue(rendered.contains("I will tap the browser icon"));
    }

    @Test
    void keepsLastNTurnsWithOmissionNotice() {
        List<Object> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "user", "content", "Do the task."));
        for (int index = 0; index < 12; index++) {
            String toolCallId = "t" + index;
            messages.add(assistant("assistant-" + index, "wait", "{}", toolCallId));
            messages.add(tool(toolCallId, "wait", "ok-" + index));
        }

        String rendered = SkillBranchPreviousSteps.formatPreviousStepsForBranch(messages, null, 10);

        assertFalse(rendered.contains("Do the task"));
        assertTrue(rendered.contains("earlier assistant turn(s) omitted"));
        assertFalse(rendered.contains("\nassistant-0\n"));
        assertFalse(rendered.contains("\nassistant-1\n"));
        assertTrue(rendered.contains("\nassistant-2\n"));
        assertTrue(rendered.contains("\nassistant-11\n"));
        assertTrue(rendered.contains("ok-11"));
        assertTrue(rendered.contains("Tool result (wait)"));
        assertTrue(rendered.contains("--- Step 10 (assistant) ---"));
    }

    @Test
    void skipsMultimodalSkillUserMessage() {
        String rendered = SkillBranchPreviousSteps.formatPreviousStepsForBranch(
                List.of(
                        Map.of(
                                "role", "user",
                                "name", "multimodal_skill_reference",
                                "content", List.of(
                                        Map.of("type", "text", "text", "[Skill reference image: foo]"),
                                        Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64,QQ=="))
                                )
                        ),
                        Map.of("role", "assistant", "content", "Next action.")
                ),
                null,
                10
        );

        assertFalse(rendered.contains("Skill reference image"));
        assertFalse(rendered.contains("base64"));
        assertTrue(rendered.contains("Next action"));
    }

    @Test
    void skipsInFlightSkillToolResultButKeepsToolCall() {
        String rendered = SkillBranchPreviousSteps.formatPreviousStepsForBranch(
                List.of(
                        Map.of("role", "user", "content", "Initial task query"),
                        assistant("Loading skill.", "skill_tool", "{\"skill_name\": \"demo\"}", "tc2")
                ),
                "tc2",
                10
        );

        assertFalse(rendered.contains("Initial task query"));
        assertTrue(rendered.contains("Loading skill."));
        assertTrue(rendered.contains("skill_tool"));
        assertFalse(rendered.contains("Tool result (skill_tool)"));
    }

    @Test
    void includesToolCallAndResultLines() {
        String rendered = SkillBranchPreviousSteps.formatPreviousStepsForBranch(
                List.of(
                        assistant("Tap icon.", "click", "{\"x\":1}", "t0"),
                        tool("t0", "click", "clicked")
                ),
                null,
                10
        );

        assertTrue(rendered.contains("Tap icon."));
        assertTrue(rendered.contains("Tool call: click"));
        assertTrue(rendered.contains("Tool result (click): clicked"));
    }

    @Test
    void assistantWithOnlyToolCallsIsIncluded() {
        String rendered = SkillBranchPreviousSteps.formatPreviousStepsForBranch(
                List.of(
                        assistant("", "wait", "{}", "t1"),
                        tool("t1", "wait", "done")
                ),
                null,
                10
        );

        assertTrue(rendered.contains("Tool call: wait"));
        assertTrue(rendered.contains("Tool result (wait): done"));
    }

    @Test
    void zeroLastNTurnsKeepsAllTurns() {
        List<Object> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "user", "content", "task"));
        for (int index = 0; index < 3; index++) {
            String toolCallId = "t" + index;
            messages.add(assistant("a" + index, "w", "{}", toolCallId));
            messages.add(tool(toolCallId, "w", "r" + index));
        }

        String rendered = SkillBranchPreviousSteps.formatPreviousStepsForBranch(messages, null, 0);

        assertFalse(rendered.contains("earlier assistant turn(s) omitted"));
        assertTrue(rendered.contains("a0"));
        assertTrue(rendered.contains("a2"));
    }

    private static Map<String, Object> assistant(
            String content,
            String toolName,
            String toolArgs,
            String toolCallId
    ) {
        return Map.of(
                "role", "assistant",
                "content", content,
                "tool_calls", List.of(Map.of(
                        "id", toolCallId,
                        "type", "function",
                        "name", toolName,
                        "arguments", toolArgs
                ))
        );
    }

    private static Map<String, Object> tool(String toolCallId, String name, String content) {
        return Map.of(
                "role", "tool",
                "tool_call_id", toolCallId,
                "name", name,
                "content", content
        );
    }
}
