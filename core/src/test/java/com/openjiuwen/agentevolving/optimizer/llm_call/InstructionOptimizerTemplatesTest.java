/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.llm_call;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for LLM optimizer prompt templates.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.llm_call.templates} in
 * {@code openjiuwen/agent_evolving/optimizer/llm_call/templates.py}.</p>
 */
class InstructionOptimizerTemplatesTest {

    @Test
    void singlePromptTemplateKeepsExpectedPlaceholdersAndTags() {
        String template = InstructionOptimizerTemplates.PROMPT_INSTRUCTION_OPTIMIZE_TEMPLATE;

        assertTrue(template.contains("{{prompt_instruction}}"));
        assertTrue(template.contains("{{tools_description}}"));
        assertTrue(template.contains("{{bad_cases}}"));
        assertTrue(template.contains("{{reflections_on_bad_cases}}"));
        assertTrue(template.contains("<思考>"));
        assertTrue(template.contains("</思考>"));
        assertTrue(template.contains("<PROMPT_OPTIMIZED>"));
    }

    @Test
    void bothPromptTemplateKeepsExpectedPlaceholdersAndTags() {
        String template = InstructionOptimizerTemplates.PROMPT_INSTRUCTION_OPTIMIZE_BOTH_TEMPLATE;

        assertTrue(template.contains("{{system_prompt}}"));
        assertTrue(template.contains("{{user_prompt}}"));
        assertTrue(template.contains("<SYSTEM_PROMPT_OPTIMIZED>"));
        assertTrue(template.contains("<USER_PROMPT_OPTIMIZED>"));
    }

    @Test
    void gradientAndBadCaseTemplatesKeepInputs() {
        String gradientTemplate = InstructionOptimizerTemplates.CREATE_PROMPT_TEXTUAL_GRADIENT_TEMPLATE;
        String badCaseTemplate = InstructionOptimizerTemplates.CREATE_BAD_CASE_TEMPLATE;

        assertTrue(gradientTemplate.contains("{{system_prompt}}"));
        assertTrue(gradientTemplate.contains("{{user_prompt}}"));
        assertTrue(gradientTemplate.contains("{{bad_cases}}"));
        assertTrue(badCaseTemplate.contains("{{question}}"));
        assertTrue(badCaseTemplate.contains("{{label}}"));
        assertTrue(badCaseTemplate.contains("{{answer}}"));
        assertTrue(badCaseTemplate.contains("{{reason}}"));
    }

    @Test
    void placeholderRestoreTemplateKeepsPlaceholderInputs() {
        String template = InstructionOptimizerTemplates.PLACEHOLDER_RESTORE_TEMPLATE;

        assertTrue(template.contains("{{original_prompt}}"));
        assertTrue(template.contains("{{revised_prompt}}"));
        assertTrue(template.contains("{{all_placeholders}}"));
        assertTrue(template.contains("{{missing_placeholders}}"));
    }
}
