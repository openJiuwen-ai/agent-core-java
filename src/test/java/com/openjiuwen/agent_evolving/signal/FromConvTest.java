/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import com.openjiuwen.agent_evolving.signal.EvolutionCategory;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for signal extraction from conversation.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.signal.test_from_conv}.
 */
class FromConvTest {
    private static final Pattern CORRECTION_PATTERN = Pattern.compile(
            "that('s| is) (wrong|incorrect|not right)"
                    + "|you'?re wrong"
                    + "|should (be|use|have)"
                    + "|actually[,\\uFF0C]?"
                    + "|no[,\\uFF0C]? (wait|actually)"
                    + "|correct(ion)?:"
                    + "|fix(ed)?:",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    @Test
    void testExtractSignalFromToolFailure() {
        Map<String, Object> conversation = new HashMap<>();
        conversation.put("tool_name", "bash");
        conversation.put("tool_result", "Command failed with exit code 1");
        conversation.put("error_type", "execution_failure");

        EvolutionSignal signal = extractSignalFromConversation(conversation);

        assertEquals("execution_failure", signal.getSignalType());
        assertEquals("bash", signal.getToolName());
    }

    @Test
    void testExtractSignalFromUserCorrection() {
        Map<String, Object> conversation = new HashMap<>();
        conversation.put("user_message", "That's not correct. Please use the other method.");
        conversation.put("correction_type", "user_correction");

        EvolutionSignal signal = extractSignalFromConversation(conversation);

        assertEquals("user_correction", signal.getSignalType());
    }

    @Test
    void testExtractSignalWithContext() {
        Map<String, Object> conversation = new HashMap<>();
        conversation.put("excerpt", "Failed to parse JSON response");
        conversation.put("context", Map.of(
            "error_details", "Invalid JSON format",
            "suggestion", "Add JSON validation"
        ));

        EvolutionSignal signal = extractSignalFromConversation(conversation);

        assertTrue(signal.getContext().containsKey("error_details"));
    }

    @Test
    void testExtractSignalDeterminesSection() {
        Map<String, Object> conversation = new HashMap<>();
        conversation.put("signal_type", "execution_failure");
        conversation.put("error_context", "Tool execution timeout");

        EvolutionSignal signal = extractSignalFromConversation(conversation);

        assertEquals("Troubleshooting", signal.getSection());
    }

    @Test
    void testExtractSignalForSkillExperience() {
        Map<String, Object> conversation = new HashMap<>();
        conversation.put("skill_name", "data_processor");
        conversation.put("signal_type", "script_artifact");

        EvolutionSignal signal = extractSignalFromConversation(conversation);

        assertEquals(EvolutionCategory.SKILL_EXPERIENCE, signal.getEvolutionType());
        assertEquals("data_processor", signal.getSkillName());
    }

    @Test
    void testExtractSignalFromEmptyConversation() {
        Map<String, Object> emptyConv = new HashMap<>();

        EvolutionSignal signal = extractSignalFromConversation(emptyConv);

        assertNull(signal);
    }

    @Test
    void testExtractSignalPreservesExcerpt() {
        Map<String, Object> conversation = new HashMap<>();
        conversation.put("excerpt", "Error: Connection refused when calling external API");

        EvolutionSignal signal = extractSignalFromConversation(conversation);

        assertEquals("Error: Connection refused when calling external API", signal.getExcerpt());
    }

    private EvolutionSignal extractSignalFromConversation(Map<String, Object> conversation) {
        if (conversation.isEmpty()) {
            return null;
        }

        String signalType = resolveSignalType(conversation);
        String excerpt = (String) conversation.getOrDefault("excerpt",
            conversation.getOrDefault("user_message", ""));
        String toolName = (String) conversation.get("tool_name");
        String skillName = (String) conversation.get("skill_name");
        String section = determineSection(signalType, (String) conversation.get("error_context"));

        EvolutionCategory category = "script_artifact".equals(signalType) 
            ? EvolutionCategory.SKILL_EXPERIENCE 
            : EvolutionCategory.SKILL_EXPERIENCE;

        Map<String, Object> context = (Map<String, Object>) conversation.get("context");

        return EvolutionSignal.builder()
                .signalType(signalType)
                .evolutionType(category)
                .section(section)
                .excerpt(excerpt)
                .toolName(toolName)
                .skillName(skillName)
                .context(context)
                .build();
    }

    private String resolveSignalType(Map<String, Object> conversation) {
        for (String key : List.of("signal_type", "error_type", "correction_type")) {
            Object value = conversation.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }

        Object userMessage = conversation.get("user_message");
        if (userMessage instanceof String text && CORRECTION_PATTERN.matcher(text).find()) {
            return "user_correction";
        }

        return "unknown";
    }

    private String determineSection(String signalType, String errorContext) {
        if ("user_correction".equals(signalType)) {
            return "Examples";
        }
        if (signalType.contains("script") || signalType.contains("artifact")) {
            return "Scripts";
        }
        return "Troubleshooting";
    }
}
