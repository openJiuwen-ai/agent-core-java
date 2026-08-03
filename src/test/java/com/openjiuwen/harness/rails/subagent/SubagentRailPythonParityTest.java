/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.subagent;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Mirrors Python's {@code test_subagent_rail} in
 * {@code tests/unit_tests/harness/test_subagent_rail.py}.
 */
class SubagentRailPythonParityTest {

    @TestFactory
    Collection<DynamicTest> subagentRailPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "TestSubagentRail::test_priority_attribute", this::priorityAttribute);
        add(tests, "TestSubagentRail::test_init_with_subagents", this::initWithSubagents);
        add(tests, "TestSubagentRail::test_init_without_subagents", this::initWithoutSubagents);
        add(tests, "TestSubagentRail::test_uninit_with_tools", this::uninitWithTools);
        add(tests, "TestSubagentRail::test_uninit_without_tools", this::uninitWithoutTools);
        add(tests, "TestSubagentRail::test_build_available_agents_description_with_subagents",
                this::buildAvailableAgentsDescriptionWithSubagents);
        add(tests, "TestSubagentRail::test_build_available_agents_description_with_general_purpose",
                this::buildAvailableAgentsDescriptionWithGeneralPurpose);
        add(tests, "TestSubagentRail::test_extract_agent_meta_with_subagentspec",
                this::extractAgentMetaWithSubagentSpec);
        add(tests, "TestSubagentRail::test_extract_agent_meta_with_deepagent", this::extractAgentMetaWithDeepAgent);
        add(tests, "TestSubagentRail::test_extract_agent_meta_with_deepagent_fallback",
                this::extractAgentMetaWithDeepAgentFallback);
        add(tests, "TestSubagentRail::test_before_model_call_sync_is_noop",
                this::beforeModelCallSyncInjectsTaskSection);
        add(tests, "TestSubagentRail::test_before_model_call_no_tools", this::beforeModelCallNoTools);
        add(tests, "TestSubagentRailAsyncMode::test_async_init_registers_session_tools",
                this::asyncInitRegistersSessionTools);
        add(tests, "TestSubagentRailAsyncMode::test_async_init_without_subagents_skips",
                this::asyncInitWithoutSubagentsSkips);
        add(tests, "TestSubagentRailAsyncMode::test_async_uninit_clears_toolkit",
                this::asyncUninitClearsToolkit);
        add(tests, "TestSubagentRailAsyncMode::test_async_before_model_call_injects_section",
                this::asyncBeforeModelCallInjectsSection);
        add(tests, "TestSubagentRailAsyncMode::test_async_before_model_call_removes_section_when_none",
                this::asyncBeforeModelCallSkipsWhenNoTools);
        add(tests, "TestSessionRailShim::test_session_rail_is_subagent_rail_subclass",
                this::sessionRailIsSubagentRailSubclass);
        add(tests, "TestSessionRailShim::test_session_rail_logs_deprecation",
                this::sessionRailLogsDeprecation);
        add(tests, "TestSessionRailShim::test_session_rail_inherits_async_semantics",
                this::sessionRailInheritsAsyncSemantics);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, Executable executable) {
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private void priorityAttribute() {
        assertEquals(95, new SubagentRail().getPriority());
    }

    private void initWithSubagents() {
        DeepAgent agent = agentWithSubagents(subagent("test_agent", "Test agent"));
        SubagentRail rail = new SubagentRail();

        rail.init(agent);

        assertEquals(List.of("task_tool"), new ArrayList<>(rail.getRegisteredToolNames()));
        assertTrue(rail.getAvailableAgentsDescription().contains("- test_agent: Test agent (Tools: All tools)"));
    }

    private void initWithoutSubagents() {
        SubagentRail rail = new SubagentRail();

        rail.init(agentWithSubagents());

        assertTrue(rail.getRegisteredToolNames().isEmpty());
        assertEquals("", rail.getAvailableAgentsDescription());
    }

    private void uninitWithTools() {
        DeepAgent agent = agentWithSubagents(subagent("test_agent", "Test agent"));
        SubagentRail rail = new SubagentRail();
        rail.init(agent);
        assertFalse(rail.getRegisteredToolNames().isEmpty());

        rail.uninit(agent);

        assertTrue(rail.getRegisteredToolNames().isEmpty());
        assertEquals("", rail.getAvailableAgentsDescription());
    }

    private void uninitWithoutTools() {
        SubagentRail rail = new SubagentRail();

        assertDoesNotThrow(() -> rail.uninit(agentWithSubagents()));
        assertTrue(rail.getRegisteredToolNames().isEmpty());
    }

    private void buildAvailableAgentsDescriptionWithSubagents() {
        SubagentRail rail = new SubagentRail();
        String description = rail.buildAvailableAgentsDescription(List.of(
                subagent("research_agent", "Research specialist"),
                subagent("code_agent", "Code specialist")
        ));

        assertTrue(description.contains("- research_agent: Research specialist (Tools: All tools)"));
        assertTrue(description.contains("- code_agent: Code specialist (Tools: All tools)"));
    }

    private void buildAvailableAgentsDescriptionWithGeneralPurpose() {
        SubagentRail rail = new SubagentRail();
        String description = rail.buildAvailableAgentsDescription(List.of(
                subagent("general-purpose", "Custom general purpose agent")
        ));

        assertTrue(description.contains("general-purpose"));
        assertEquals(1, countOccurrences(description, "general-purpose"));
    }

    private void extractAgentMetaWithSubagentSpec() {
        SubagentRail rail = new SubagentRail();
        String description = rail.buildAvailableAgentsDescription(List.of(
                subagent("test_agent", "Test description")
        ));

        assertEquals("- test_agent: Test description (Tools: All tools)", description);
    }

    private void extractAgentMetaWithDeepAgent() {
        DeepAgent subagent = new DeepAgent(new AgentCard("agent-id", "agent_name", "agent description"));
        SubagentRail rail = new SubagentRail();

        String description = rail.buildAvailableAgentsDescription(List.of(subagent));

        assertEquals("- agent_name: agent description (Tools: All tools)", description);
    }

    private void extractAgentMetaWithDeepAgentFallback() {
        List<Object> specs = new ArrayList<>();
        specs.add(null);
        SubagentRail rail = new SubagentRail();

        String description = rail.buildAvailableAgentsDescription(specs);

        assertEquals("- general-purpose: DeepAgent instance (Tools: All tools)", description);
    }

    private void beforeModelCallSyncInjectsTaskSection() {
        SubagentRail rail = new SubagentRail();
        rail.init(agentWithSubagents(subagent("test_agent", "Test agent")));
        CallbackContext ctx = ctx("language", "en");

        rail.beforeModelCall(ctx);

        PromptSection section = assertInstanceOf(PromptSection.class, ctx.get("task_tool_section"));
        assertEquals(SectionName.TASK_TOOL, section.getName());
        assertEquals(List.of("task_tool"), ctx.get("subagent_tool_names"));
        assertEquals("- test_agent: Test agent (Tools: All tools)", ctx.get("available_agents"));
        assertNull(ctx.get("session_tools_section"));
    }

    private void beforeModelCallNoTools() {
        SubagentRail rail = new SubagentRail();
        CallbackContext ctx = ctx();

        rail.beforeModelCall(ctx);

        assertFalse(ctx.getValues().containsKey("task_tool_section"));
        assertFalse(ctx.getValues().containsKey("session_tools_section"));
    }

    private void asyncInitRegistersSessionTools() {
        DeepAgent agent = agentWithSubagents(subagent("test_agent", "Test agent"));
        SubagentRail rail = new SubagentRail(true);

        rail.init(agent);

        assertNotNull(agent.getSessionToolkit());
        assertEquals(List.of("sessions_list", "sessions_spawn", "sessions_cancel"),
                new ArrayList<>(rail.getRegisteredToolNames()));
    }

    private void asyncInitWithoutSubagentsSkips() {
        DeepAgent agent = agentWithSubagents();
        SubagentRail rail = new SubagentRail(true);

        rail.init(agent);

        assertNull(agent.getSessionToolkit());
        assertTrue(rail.getRegisteredToolNames().isEmpty());
    }

    private void asyncUninitClearsToolkit() {
        DeepAgent agent = agentWithSubagents(subagent("test_agent", "Test agent"));
        SubagentRail rail = new SubagentRail(true);
        rail.init(agent);
        assertNotNull(agent.getSessionToolkit());

        rail.uninit(agent);

        assertNull(agent.getSessionToolkit());
        assertTrue(rail.getRegisteredToolNames().isEmpty());
    }

    private void asyncBeforeModelCallInjectsSection() {
        SubagentRail rail = new SubagentRail(true);
        rail.init(agentWithSubagents(subagent("test_agent", "Test agent")));
        CallbackContext ctx = ctx("language", "en");

        rail.beforeModelCall(ctx);

        PromptSection section = assertInstanceOf(PromptSection.class, ctx.get("session_tools_section"));
        assertEquals(SectionName.SESSION_TOOLS, section.getName());
        assertEquals(List.of("sessions_list", "sessions_spawn", "sessions_cancel"), ctx.get("subagent_tool_names"));
        assertNull(ctx.get("task_tool_section"));
    }

    private void asyncBeforeModelCallSkipsWhenNoTools() {
        SubagentRail rail = new SubagentRail(true);
        CallbackContext ctx = ctx();

        rail.beforeModelCall(ctx);

        assertFalse(ctx.getValues().containsKey("session_tools_section"));
    }

    private void sessionRailIsSubagentRailSubclass() {
        assertTrue(SubagentRail.class.isAssignableFrom(SessionRail.class));
    }

    private void sessionRailLogsDeprecation() {
        Logger log = mock(Logger.class);
        SessionRail rail = new SessionRail(log);

        assertTrue(rail.isEnableAsyncSubagent());
        verify(log).warn("SessionRail is deprecated; use SubagentRail(enable_async_subagent=True).");
    }

    private void sessionRailInheritsAsyncSemantics() {
        SessionRail rail = new SessionRail();

        assertTrue(rail instanceof SubagentRail);
        assertTrue(rail.isEnableAsyncSubagent());
    }

    private static DeepAgent agentWithSubagents(DeepAgentConfig.SubAgentConfig... subagents) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setLanguage("cn");
        Map<String, DeepAgentConfig.SubAgentConfig> specs = new LinkedHashMap<>();
        for (DeepAgentConfig.SubAgentConfig spec : subagents) {
            specs.put(spec.getName(), spec);
        }
        config.setSubagents(specs);
        DeepAgent agent = new DeepAgent(new AgentCard("deep-agent", "deep-agent", "test"));
        agent.configure(config);
        return agent;
    }

    private static DeepAgentConfig.SubAgentConfig subagent(String name, String description) {
        DeepAgentConfig.SubAgentConfig spec = new DeepAgentConfig.SubAgentConfig();
        spec.setAgentCard(new AgentCard(name, name, description));
        spec.setSystemPrompt("Stub prompt");
        return spec;
    }

    private static CallbackContext ctx(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return new CallbackContext(new DeepAgent(new AgentCard("deep", "deep", "test")), map);
    }

    private static int countOccurrences(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
