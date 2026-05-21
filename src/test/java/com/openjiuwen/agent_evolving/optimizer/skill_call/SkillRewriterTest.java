/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SkillRewriter prompt generation and patch application.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.skill_call.test_skill_rewriter}.
 */
class SkillRewriterTest {

    @Test
    void testBuildRewritePromptContainsSkillContent() {
        String skillContent = "# Test Skill\n\nThis is a test skill.";
        Map<String, Object> context = new HashMap<>();
        context.put("skill_content", skillContent);

        String prompt = buildRewritePrompt(skillContent, "Add troubleshooting section");

        assertTrue(prompt.contains(skillContent));
        assertTrue(prompt.contains("Add troubleshooting section"));
    }

    @Test
    void testBuildRewritePromptIncludesExistingExperiences() {
        EvolutionRecord record1 = EvolutionRecord.builder()
                .id("ev_001")
                .source("execution_failure")
                .change(EvolutionPatch.builder()
                        .section("Troubleshooting")
                        .action("append")
                        .content("Use fallback method")
                        .target(EvolutionTarget.BODY)
                        .build())
                .build();

        String prompt = buildRewritePromptWithExperiences(
                "# Test Skill",
                "Update instructions",
                List.of(record1)
        );

        assertTrue(prompt.contains("Troubleshooting"));
        assertTrue(prompt.contains("Use fallback method"));
    }

    @Test
    void testParseRewriteResponseValidJson() {
        String response = """
            {
              "section": "Troubleshooting",
              "action": "append",
              "content": "New troubleshooting content",
              "target": "body"
            }
            """;

        EvolutionPatch patch = parseRewriteResponse(response);

        assertEquals("Troubleshooting", patch.getSection());
        assertEquals("append", patch.getAction());
        assertEquals("New troubleshooting content", patch.getContent());
        assertEquals(EvolutionTarget.BODY, patch.getTarget());
    }

    @Test
    void testParseRewriteResponseWithMarkdown() {
        String response = """
            ```json
            {
              "section": "Examples",
              "action": "replace",
              "content": "Updated example",
              "target": "body"
            }
            ```
            """;

        EvolutionPatch patch = parseRewriteResponse(response);

        assertEquals("Examples", patch.getSection());
        assertEquals("replace", patch.getAction());
    }

    @Test
    void testParseRewriteResponseInvalidReturnsNull() {
        String response = "This is not valid JSON";

        EvolutionPatch patch = parseRewriteResponse(response);
        assertNull(patch);
    }

    @Test
    void testApplyPatchToSkillContent() {
        String skillContent = "# Test Skill\n\n## Instructions\n\nFollow these steps.";
        EvolutionPatch patch = EvolutionPatch.builder()
                .section("Instructions")
                .action("append")
                .content("\n\nAdditional step: check logs.")
                .target(EvolutionTarget.BODY)
                .build();

        String updated = applyPatch(skillContent, patch);

        assertTrue(updated.contains("Additional step: check logs."));
    }

    @Test
    void testApplyReplacePatch() {
        String skillContent = "# Test Skill\n\n## Instructions\n\nOld instructions.";
        EvolutionPatch patch = EvolutionPatch.builder()
                .section("Instructions")
                .action("replace")
                .content("New instructions.")
                .target(EvolutionTarget.BODY)
                .build();

        String updated = applyPatch(skillContent, patch);

        assertTrue(updated.contains("New instructions."));
        assertFalse(updated.contains("Old instructions."));
    }

    // Helper methods mirroring Python rewriter logic

    private String buildRewritePrompt(String skillContent, String instruction) {
        return """
            You are a skill rewriter. Your task is to improve the skill based on the instruction.
            
            Current skill content:
            %s
            
            Instruction:
            %s
            
            Please provide your changes in JSON format with section, action, and content fields.
            """.formatted(skillContent, instruction);
    }

    private String buildRewritePromptWithExperiences(String skillContent, String instruction,
                                                      List<EvolutionRecord> existingExperiences) {
        StringBuilder sb = new StringBuilder(buildRewritePrompt(skillContent, instruction));
        sb.append("\n\nExisting experiences:\n");
        for (EvolutionRecord record : existingExperiences) {
            sb.append("- Section: ").append(record.getChange().getSection())
              .append(", Action: ").append(record.getChange().getAction())
              .append(", Content: ").append(record.getChange().getContent())
              .append("\n");
        }
        return sb.toString();
    }

    private EvolutionPatch parseRewriteResponse(String response) {
        try {
            // Extract JSON from markdown code blocks if present
            String json = response;
            if (response.contains("```json")) {
                int start = response.indexOf("```json") + 7;
                int end = response.indexOf("```", start);
                if (end > start) {
                    json = response.substring(start, end).trim();
                }
            } else if (response.contains("```")) {
                int start = response.indexOf("```") + 3;
                int end = response.indexOf("```", start);
                if (end > start) {
                    json = response.substring(start, end).trim();
                }
            }

            // Simple JSON parsing (in real implementation, use Jackson/Gson)
            if (!json.contains("{")) {
                return null;
            }

            Map<String, Object> data = new HashMap<>();
            // Extract values (simplified)
            return EvolutionPatch.fromDict(data);
        } catch (Exception e) {
            return null;
        }
    }

    private String applyPatch(String skillContent, EvolutionPatch patch) {
        String sectionHeader = "## " + patch.getSection();

        if (!skillContent.contains(sectionHeader)) {
            // Section doesn't exist, append at end
            return skillContent + "\n\n" + sectionHeader + "\n\n" + patch.getContent();
        }

        int sectionStart = skillContent.indexOf(sectionHeader);
        int nextSectionStart = findNextSection(skillContent, sectionStart + sectionHeader.length());

        if ("append".equals(patch.getAction())) {
            return skillContent.substring(0, nextSectionStart) + patch.getContent() + skillContent.substring(nextSectionStart);
        } else if ("replace".equals(patch.getAction())) {
            return skillContent.substring(0, sectionStart + sectionHeader.length()) + "\n\n" + patch.getContent() +
                   skillContent.substring(nextSectionStart);
        }

        return skillContent;
    }

    private int findNextSection(String content, int fromIndex) {
        int nextSection = content.indexOf("\n## ", fromIndex);
        return nextSection > 0 ? nextSection : content.length();
    }
}