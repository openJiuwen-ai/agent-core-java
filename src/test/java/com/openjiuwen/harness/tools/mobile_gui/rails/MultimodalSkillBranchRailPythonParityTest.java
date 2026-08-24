/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.mobile_gui.MobileGuiRuntimeSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/tools/mobile_gui/test_multimodal_skill_branch_rail.py}.
 */
class MultimodalSkillBranchRailPythonParityTest {

    @TempDir
    Path tempDir;

    @Test
    void branchRailRewritesToolMessageWithPlannerFields() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MobileGuiRuntimeSettings settings = settings(Map.of(
                "MULTIMODAL_SKILL_CONSULT_MODE", "branch",
                "MULTIMODAL_SKILL_BRANCH_MAX_IMAGES", "2",
                "MULTIMODAL_SKILL_BRANCH_MAX_CONSULTS_PER_SKILL", "2"
        ));
        MultimodalSkillBranchRail rail = new MultimodalSkillBranchRail(settings, request -> {
            calls.incrementAndGet();
            assertEquals("demo", request.skillName());
            assertEquals("Find the repo", request.instruction());
            assertEquals("abc123", request.liveScreenshotBase64());
            assertEquals(1, request.manifest().size());
            assertEquals(2, request.maxImages());
            return MultimodalSkillBranchRail.BranchDecision.success(Map.of(
                    "skill_applicability", "effective",
                    "subgoal", "open page",
                    "plan", "Tap search.",
                    "do_not_do", "Do not scroll blindly.",
                    "fallback_if_no_progress", "Go back and retry.",
                    "expected_state", "Search visible.",
                    "completion_scope", "local_only"
            ), List.of("step"));
        });
        CallbackContext ctx = skillContext("demo", "Do task.\n\n![Step](images/step.png)\n", true);
        extra(ctx).put("pinned_user_goal", "Find the repo");
        extra(ctx).put("vlm_grounding_base64", "abc123");

        rail.afterToolCall(ctx);

        assertEquals(1, calls.get());
        String content = toolMessage(ctx).getContentAsString();
        assertTrue(content.contains("Skill consult: demo"));
        assertTrue(content.contains("Applicability: effective"));
        assertTrue(content.contains("Subgoal: open page"));
        assertTrue(content.contains("Plan: Tap search."));
        assertTrue(content.contains("Do not do: Do not scroll blindly."));
        assertTrue(content.contains("Completion scope: local_only"));
        assertFalse(content.contains("original"));
    }

    @Test
    void branchRailNoopInInlineMode() {
        AtomicInteger calls = new AtomicInteger();
        MultimodalSkillBranchRail rail = new MultimodalSkillBranchRail(
                settings(Map.of("MULTIMODAL_SKILL_CONSULT_MODE", "inline")),
                request -> {
                    calls.incrementAndGet();
                    return MultimodalSkillBranchRail.BranchDecision.failure("unexpected");
                }
        );
        CallbackContext ctx = skillContext("demo", "# No images\n", true);

        rail.afterToolCall(ctx);

        assertEquals("original", toolMessage(ctx).getContent());
        assertEquals(0, calls.get());
    }

    @Test
    void branchRailIgnoresNonSkillTool() {
        AtomicInteger calls = new AtomicInteger();
        MultimodalSkillBranchRail rail = new MultimodalSkillBranchRail(settings(Map.of()), request -> {
            calls.incrementAndGet();
            return MultimodalSkillBranchRail.BranchDecision.failure("unexpected");
        });
        CallbackContext ctx = skillContext("demo", "![A](images/a.png)", true);
        ctx.put("tool_name", "read_file");

        rail.afterToolCall(ctx);

        assertEquals("original", toolMessage(ctx).getContent());
        assertEquals(0, calls.get());
    }

    @Test
    void branchRailIgnoresFailedSkillTool() {
        AtomicInteger calls = new AtomicInteger();
        MultimodalSkillBranchRail rail = new MultimodalSkillBranchRail(settings(Map.of()), request -> {
            calls.incrementAndGet();
            return MultimodalSkillBranchRail.BranchDecision.failure("unexpected");
        });
        CallbackContext ctx = skillContext("demo", "![A](images/a.png)", false);

        rail.afterToolCall(ctx);

        assertEquals("original", toolMessage(ctx).getContent());
        assertEquals(0, calls.get());
    }

    @Test
    void branchRailSkipsWhenSkillHasNoLocalImages() {
        AtomicInteger calls = new AtomicInteger();
        MultimodalSkillBranchRail rail = new MultimodalSkillBranchRail(settings(Map.of()), request -> {
            calls.incrementAndGet();
            return MultimodalSkillBranchRail.BranchDecision.failure("unexpected");
        });
        CallbackContext ctx = skillContext("text-only", "# No images\n", true);

        rail.afterToolCall(ctx);

        assertEquals("original", toolMessage(ctx).getContent());
        assertEquals(0, calls.get());
    }

    @Test
    void branchRailEnforcesPerSkillConsultLimit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MultimodalSkillBranchRail rail = new MultimodalSkillBranchRail(settings(Map.of(
                "MULTIMODAL_SKILL_BRANCH_MAX_CONSULTS_PER_SKILL", "1"
        )), request -> {
            calls.incrementAndGet();
            return MultimodalSkillBranchRail.BranchDecision.failure("unexpected");
        });
        CallbackContext ctx = skillContext("demo", "![A](images/a.png)", true);
        extra(ctx).put(MultimodalSkillBranchRail.CONSULT_COUNTS_KEY, new LinkedHashMap<>(Map.of("demo", 1)));

        rail.afterToolCall(ctx);

        assertEquals(0, calls.get());
        String content = toolMessage(ctx).getContentAsString();
        assertTrue(content.contains("Consult limit reached"));
        assertFalse(content.contains("original"));
    }

    @Test
    void branchRailWritesFailureMessageWhenBranchFails() throws Exception {
        MultimodalSkillBranchRail rail = new MultimodalSkillBranchRail(settings(Map.of()), request ->
                MultimodalSkillBranchRail.BranchDecision.failure("Stage 2 parse failed"));
        CallbackContext ctx = skillContext("demo", "![A](images/a.png)", true);

        rail.afterToolCall(ctx);

        String content = toolMessage(ctx).getContentAsString();
        assertTrue(content.contains("Skill consult: demo"));
        assertTrue(content.contains("Branch consult failed: Stage 2 parse failed"));
        assertFalse(content.contains("original"));
    }

    @Test
    void branchRailNoopWhenModelUnavailable() throws Exception {
        MultimodalSkillBranchRail rail = new MultimodalSkillBranchRail(settings(Map.of()), request -> null);
        CallbackContext ctx = skillContext("demo", "![A](images/a.png)", true);

        rail.afterToolCall(ctx);

        assertEquals("original", toolMessage(ctx).getContent());
    }

    private CallbackContext skillContext(String skillName, String skillMarkdown, boolean success) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tool_name", "skill_tool");
        values.put("tool_args", Map.of("skill_name", skillName));
        values.put("tool_msg", new ToolMessage("original", "tc1", "skill_tool"));
        values.put("tool_result", ToolOutput.of(success, Map.of(
                "skill_directory", skillDir(skillName).toString(),
                "skill_content", skillMarkdown
        ), success ? null : "failed"));
        values.put("extra", new LinkedHashMap<String, Object>());
        return new CallbackContext(new DeepAgent(), values);
    }

    private Path skillDir(String skillName) {
        try {
            Path skillDir = tempDir.resolve("skills").resolve(skillName);
            Path imageDir = skillDir.resolve("images");
            Files.createDirectories(imageDir);
            Files.writeString(imageDir.resolve("step.png"), "x");
            Files.writeString(imageDir.resolve("a.png"), "x");
            return skillDir;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extra(CallbackContext ctx) {
        return (Map<String, Object>) ctx.get("extra");
    }

    private static ToolMessage toolMessage(CallbackContext ctx) {
        return (ToolMessage) ctx.get("tool_msg");
    }

    private static MobileGuiRuntimeSettings settings(Map<String, String> env) {
        return MobileGuiRuntimeSettings.fromEnvironment(env);
    }
}
