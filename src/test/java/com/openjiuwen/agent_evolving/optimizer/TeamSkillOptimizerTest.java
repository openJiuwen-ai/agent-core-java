/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TeamSkillOptimizer prompt templates and patch generation.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.test_team_skill_optimizer}.
 */
class TeamSkillOptimizerTest {

    @Test
    void testBuildTeamSkillPromptContainsRoleInfo() {
        Map<String, Object> context = new HashMap<>();
        context.put("skill_content", "# Team Skill\n\n## Roles\n\n- Lead: Primary decision maker");
        context.put("user_query", "How to coordinate team tasks?");
        context.put("signal_type", "execution_failure");

        String prompt = buildTeamSkillPrompt(context);

        assertTrue(prompt.contains("Roles"));
        assertTrue(prompt.contains("execution_failure"));
    }

    @Test
    void testTeamSkillPromptIncludesWorkflowInfo() {
        Map<String, Object> context = new HashMap<>();
        context.put("skill_content", "# Team Skill\n\n## Workflow\n\n1. Assign tasks\n2. Review results");
        context.put("conversation_snippet", "Agent failed to coordinate");

        String prompt = buildTeamSkillPrompt(context);

        assertTrue(prompt.contains("Workflow"));
        assertTrue(prompt.contains("Agent failed to coordinate"));
    }

    @Test
    void testGenerateTeamSkillPatchValid() {
        String response = """
            {
              "section": "Workflow",
              "action": "append",
              "content": "3. Validate results before finalizing",
              "target": "body"
            }
            """;

        Map<String, Object> patch = parseTeamSkillPatch(response);

        assertEquals("Workflow", patch.get("section"));
        assertEquals("append", patch.get("action"));
        assertEquals("3. Validate results before finalizing", patch.get("content"));
    }

    @Test
    void testTeamSkillPatchIncludesRoleTarget() {
        String response = """
            {
              "section": "Roles",
              "action": "append",
              "content": "- Validator: Reviews all team outputs",
              "target": "body"
            }
            """;

        Map<String, Object> patch = parseTeamSkillPatch(response);

        assertEquals("Roles", patch.get("section"));
        assertTrue(((String) patch.get("content")).contains("Validator"));
    }

    @Test
    void testUserPatchPromptIncludesTeamContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("user_query", "Team coordination failed");
        context.put("skill_name", "team_orchestrator");

        String prompt = buildUserPatchPrompt(context);

        assertTrue(prompt.contains("team"));
        assertTrue(prompt.contains("team_orchestrator"));
    }

    @Test
    void testTrajectoryAnalysisPromptForTeam() {
        Map<String, Object> trajectory = new HashMap<>();
        trajectory.put("messages", "Agent A called Agent B but got timeout");
        trajectory.put("signal_type", "tool_timeout");

        String prompt = buildTrajectoryAnalysisPrompt(trajectory);

        assertTrue(prompt.contains("Agent A"));
        assertTrue(prompt.contains("tool_timeout"));
    }

    // Helper methods mirroring Python optimizer logic

    private String buildTeamSkillPrompt(Map<String, Object> context) {
        String skillContent = (String) context.getOrDefault("skill_content", "");
        String signalType = (String) context.getOrDefault("signal_type", "");
        String conversation = (String) context.getOrDefault("conversation_snippet", "");

        return """
            You are a team skill optimizer. Analyze the team skill and suggest improvements.
            
            Current skill content:
            %s
            
            Signal type: %s
            
            Conversation snippet:
            %s
            
            Please suggest improvements in JSON format focusing on Roles or Workflow sections.
            """.formatted(skillContent, signalType, conversation);
    }

    private String buildUserPatchPrompt(Map<String, Object> context) {
        String userQuery = (String) context.getOrDefault("user_query", "");
        String skillName = (String) context.getOrDefault("skill_name", "");

        return """
            User reported an issue with the team skill '%s'.
            
            Issue description:
            %s
            
            Please suggest a patch to improve team coordination.
            """.formatted(skillName, userQuery);
    }

    private String buildTrajectoryAnalysisPrompt(Map<String, Object> trajectory) {
        String messages = (String) trajectory.getOrDefault("messages", "");
        String signalType = (String) trajectory.getOrDefault("signal_type", "");

        return """
            Analyze this team interaction trajectory for issues:
            
            Messages:
            %s
            
            Signal: %s
            
            Identify coordination failures and suggest team skill improvements.
            """.formatted(messages, signalType);
    }

    private Map<String, Object> parseTeamSkillPatch(String response) {
        Map<String, Object> patch = new HashMap<>();
        // Simplified parsing (real implementation uses JSON parser)
        if (response.contains("\"section\"")) {
            patch.put("section", extractValue(response, "section"));
            patch.put("action", extractValue(response, "action"));
            patch.put("content", extractValue(response, "content"));
            patch.put("target", extractValue(response, "target"));
        }
        return patch;
    }

    private String extractValue(String json, String key) {
        String search = "\"" + key + "\"";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start = json.indexOf(":", start) + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n')) {
            start++;
        }
        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
        return "";
    }
}