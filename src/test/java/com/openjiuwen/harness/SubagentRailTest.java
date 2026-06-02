/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.SubagentRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for SubagentRail.
 * <p>
 * Mirrors Python's {@code test_subagent_rail} in
 * {@code tests.unit_tests.harness.test_subagent_rail}.
 */
@Tag("unit-test")
class SubagentRailTest {

    @Test
    @DisplayName("Test that priority is correctly set")
    void testPriorityAttribute() {
        SubagentRail rail = new SubagentRail();
        assertEquals(95, rail.getPriority());
    }

    @Test
    @DisplayName("Test init method when subagents are configured")
    void testInitWithSubagents() {
        Runner.start();
        try {
            DeepAgent parent = buildParentAgent(List.of(buildSubagent("test_agent", "Test agent")));
            SubagentRail rail = new SubagentRail();

            rail.init(parent);

            assertEquals(1, rail.getRegisteredTools().size());
            assertTrue(parent.getDelegate().getAbilityManager().list().stream()
                    .anyMatch(card -> "task".equals(cardValue(card, "getName"))
                            || "task_tool".equals(cardValue(card, "getName"))
                            || "task".equals(cardValue(card, "getId"))));
            assertTrue(rail.getAvailableAgentsDescription().contains("\"test_agent\": Test agent"));
        } finally {
            Runner.stop();
        }
    }

    @Test
    @DisplayName("Test init method when no subagents are configured")
    void testInitWithoutSubagents() {
        DeepAgent parent = buildParentAgent(List.of());
        SubagentRail rail = new SubagentRail();

        rail.init(parent);

        assertTrue(rail.getRegisteredTools().isEmpty());
        assertEquals("", rail.getAvailableAgentsDescription());
    }

    @Test
    @DisplayName("Test uninit method when tools are registered")
    void testUninitWithTools() {
        Runner.start();
        try {
            DeepAgent parent = buildParentAgent(List.of(buildSubagent("test_tool_agent", "Test tool agent")));
            SubagentRail rail = new SubagentRail();

            rail.init(parent);
            rail.uninit(parent);

            assertTrue(rail.getRegisteredTools().isEmpty());
            assertEquals("", rail.getAvailableAgentsDescription());
        } finally {
            Runner.stop();
        }
    }

    @Test
    @DisplayName("Test uninit method when no tools are registered")
    void testUninitWithoutTools() {
        DeepAgent parent = buildParentAgent(List.of());
        SubagentRail rail = new SubagentRail();

        rail.uninit(parent);

        assertTrue(rail.getRegisteredTools().isEmpty());
    }

    @Test
    @DisplayName("Test build available agents description with subagents")
    void testBuildAvailableAgentsDescriptionWithSubagents() {
        String description = SubagentRail.buildAvailableAgentsDescription(List.of(
                buildSubagent("research_agent", "Research specialist"),
                buildSubagent("code_agent", "Code specialist")));

        assertTrue(description.contains("\"research_agent\": Research specialist"));
        assertTrue(description.contains("\"code_agent\": Code specialist"));
    }

    @Test
    @DisplayName("Test build available agents description keeps general-purpose once")
    void testBuildAvailableAgentsDescriptionWithGeneralPurpose() {
        String description = SubagentRail.buildAvailableAgentsDescription(List.of(
                buildSubagent("general-purpose", "Custom general purpose agent"),
                buildSubagent("general-purpose", "Duplicate")));

        assertTrue(description.contains("general-purpose"));
        assertEquals(1, description.split("general-purpose", -1).length - 1);
    }

    @Test
    @DisplayName("Test extract agent meta with subagentspec")
    void testExtractAgentMetaWithSubagentSpec() {
        SubagentRail.AgentMeta meta = SubagentRail.extractAgentMeta(buildSubagent("test_agent", "Test description"));
        assertEquals("test_agent", meta.name());
        assertEquals("Test description", meta.description());
    }

    @Test
    @DisplayName("Test extract agent meta with deepagent")
    void testExtractAgentMetaWithDeepAgent() {
        DeepAgent subagent = buildSubagent("agent_name", "agent description");
        SubagentRail.AgentMeta meta = SubagentRail.extractAgentMeta(subagent);
        assertEquals("agent_name", meta.name());
        assertEquals("agent description", meta.description());
    }

    @Test
    @DisplayName("Test extract agent meta with deepagent fallback")
    void testExtractAgentMetaWithDeepAgentFallback() {
        DeepAgent subagent = buildSubagent("", "");
        SubagentRail.AgentMeta meta = SubagentRail.extractAgentMeta(subagent);
        assertEquals("general-purpose", meta.name());
        assertEquals("DeepAgent instance", meta.description());
    }

    @Test
    @DisplayName("before_model_call is a no-op after task_tool moved into tools section")
    void testBeforeModelCallWithBuilder() {
        SubagentRail rail = new SubagentRail();
        rail.beforeModelCall(com.openjiuwen.core.singleagent.rail.AgentCallbackContext.builder().build());
        assertTrue(rail.getRegisteredTools().isEmpty());
    }

    @Test
    @DisplayName("before_model_call returns immediately when tools are absent")
    void testBeforeModelCallNoTools() {
        SubagentRail rail = new SubagentRail();

        rail.beforeModelCall(com.openjiuwen.core.singleagent.rail.AgentCallbackContext.builder().build());

        assertTrue(rail.getRegisteredTools().isEmpty());
        assertEquals("", rail.getAvailableAgentsDescription());
    }

    @Test
    @DisplayName("Java export equivalent exposes the SubagentRail class")
    void testAllMethodEquivalent() {
        assertEquals("SubagentRail", SubagentRail.class.getSimpleName());
    }

    private static DeepAgent buildParentAgent(List<DeepAgent> subagents) {
        DeepAgent parent = buildSubagent("parent", "Parent");
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(AgentCard.builder().name("parent").description("Parent").build());
        config.setSubagents(subagents);
        parent.configure(config);
        return parent;
    }

    private static DeepAgent buildSubagent(String name, String description) {
        return new DeepAgent(AgentCard.builder().name(name).description(description).build());
    }

    private static String cardValue(Object card, String getterName) {
        try {
            Object value = card.getClass().getMethod(getterName).invoke(card);
            return value != null ? String.valueOf(value) : "";
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }
}
