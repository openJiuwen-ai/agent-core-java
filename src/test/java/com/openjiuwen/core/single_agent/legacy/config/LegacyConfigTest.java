/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.legacy.config;

import com.openjiuwen.core.common.constants.ControllerType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for legacy single-agent config models.
 *
 * <p>Mirrors Python's Pydantic models in
 * {@code openjiuwen/core/single_agent/legacy/config.py}.</p>
 */
class LegacyConfigTest {

    @Test
    void agentConfigDefaultsMirrorPythonFields() {
        AgentConfig config = new AgentConfig();

        assertEquals("", config.getId());
        assertEquals("", config.getVersion());
        assertEquals("", config.getDescription());
        assertEquals(ControllerType.UNDEFINED, config.getControllerType());
        assertTrue(config.getWorkflows().isEmpty());
        assertTrue(config.getTools().isEmpty());
    }

    @Test
    void llmAndIntentDefaultsPreservePythonValues() {
        LlmCallConfig llm = new LlmCallConfig();
        IntentDetectionConfig intent = new IntentDetectionConfig();

        assertTrue(llm.isFreezeUserPrompt());
        assertEquals(false, llm.isFreezeSystemPrompt());
        assertTrue(llm.getSystemPrompt().isEmpty());
        assertEquals("\u5206\u7c7b1", intent.getDefaultClass());
        assertTrue(intent.isEnableInput());
        assertEquals(false, intent.isEnableHistory());
        assertEquals(5, intent.getChatHistoryMaxTurn());
    }

    @Test
    void constrainConfigRejectsNonPositiveValuesLikePydanticGt() {
        ConstrainConfig config = new ConstrainConfig();

        assertEquals(10, config.getReservedMaxChatRounds());
        assertEquals(5, config.getMaxIteration());
        assertThrows(IllegalArgumentException.class, () -> config.setReservedMaxChatRounds(0));
        assertThrows(IllegalArgumentException.class, () -> config.setMaxIteration(0));
    }

    @Test
    void workflowAndReactConfigsSetControllerTypesAndNestedDefaults() {
        WorkflowAgentConfig workflow = new WorkflowAgentConfig();
        LegacyReActAgentConfig react = new LegacyReActAgentConfig();
        ReActAgentConfig alias = new ReActAgentConfig();

        assertEquals(ControllerType.WORKFLOW_CONTROLLER, workflow.getControllerType());
        assertNotNull(workflow.getStartWorkflow());
        assertNotNull(workflow.getEndWorkflow());
        assertNotNull(workflow.getDefaultResponse());
        assertEquals(ControllerType.REACT_CONTROLLER, react.getControllerType());
        assertEquals("react_system_prompt", react.getPromptTemplateName());
        assertEquals(10, react.getContextWindowLimit());
        assertInstanceOf(LegacyReActAgentConfig.class, alias);
    }

    @Test
    void mutableCollectionsCopyInputsWithoutSharingOuterLists() {
        LlmCallConfig llm = new LlmCallConfig();
        IntentDetectionConfig intent = new IntentDetectionConfig();
        MemoryConfig memory = new MemoryConfig();

        llm.setSystemPrompt(List.of(Map.of("role", "system")));
        intent.setCategoryList(List.of("a", "b"));
        memory.setConfig(Map.of("k", "v"));

        assertEquals(List.of(Map.of("role", "system")), llm.getSystemPrompt());
        assertEquals(List.of("a", "b"), intent.getCategoryList());
        assertEquals(Map.of("k", "v"), memory.getConfig());
    }
}
