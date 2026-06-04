/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for task tool.
 *
 * <p>Mirrors Python's {@code test_task_tool.py} in
 * {@code tests/unit_tests/harness/tools/test_task_tool.py}.
 */
class TestTaskTool {

    @Nested
    class TestTaskToolInvoke {

        @Test
        void testTaskToolInvokeSuccess() {
            Map<String, Object> calledInputs = new LinkedHashMap<>();
            DeepAgent parentAgent = parentWithSubagent("code", "test_id");
            TaskTool tool = new TaskTool(toolCard(), parentAgent) {
                @Override
                protected Object runSubagent(DeepAgent target, Map<String, Object> toolArgs,
                                             AgentSessionApi childSession) {
                    calledInputs.putAll(toolArgs);
                    return Map.of("output", "done");
                }
            };

            ToolOutput result = (ToolOutput) tool.invoke(
                    Map.of("subagent_type", "code", "task_description", "run task"),
                    Map.of("session", new FakeSession("parent_session"))
            );

            assertTrue(result.isSuccess(), result.getError());
            assertEquals(Map.of("output", "done", "agent_id", "test_id"), result.getData());
            assertNull(result.getError());
            assertEquals("run task", calledInputs.get("query"));
            assertTrue(Pattern.matches(
                    "parent_session_sub_code_[0-9a-f]{8}",
                    String.valueOf(calledInputs.get("conversation_id"))
            ));
        }

        @Test
        void testTaskToolInvokeInvalidSession() {
            TaskTool tool = new TaskTool(toolCard(), parentWithSubagent("code", "test_id"));

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> tool.invoke(
                            Map.of("subagent_type", "code", "task_description", "run task"),
                            Map.of("session", "not-session")
                    )
            );

            assertTrue(error.getMessage().contains("valid session"));
        }

        @Test
        void testTaskToolInvokeMissingRequiredFields() {
            TaskTool tool = new TaskTool(toolCard(), parentWithSubagent("code", "test_id"));

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> tool.invoke(
                            Map.of("subagent_type", "code"),
                            Map.of("session", new FakeSession("parent_session"))
                    )
            );

            assertTrue(error.getMessage().contains("required"));
        }

        @Test
        void testTaskToolReusesStickyBrowserSubsessionId() {
            Map<String, Object> calledInputs = new LinkedHashMap<>();
            DeepAgent parentAgent = parentWithSubagent("browser_agent", "test_id");
            TaskTool tool = new TaskTool(toolCard(), parentAgent) {
                @Override
                protected Object runSubagent(DeepAgent target, Map<String, Object> toolArgs,
                                             AgentSessionApi childSession) {
                    calledInputs.putAll(toolArgs);
                    return Map.of("output", "done");
                }
            };

            ToolOutput result = (ToolOutput) tool.invoke(
                    Map.of("subagent_type", "browser_agent", "task_description", "continue browser task"),
                    Map.of("session", new FakeSession("parent_session"))
            );

            assertTrue(result.isSuccess(), result.getError());
            assertEquals("parent_session_sub_browser_agent", calledInputs.get("conversation_id"));
        }
    }

    @Nested
    class TestTaskToolSync {

        @Test
        void testCreateTaskTool() {
            List<TaskTool> tools = TaskTool.createTaskTool(
                    parentWithSubagent("code", "test_id"),
                    "code,search",
                    "cn"
            );

            assertEquals(1, tools.size());
            assertInstanceOf(TaskTool.class, tools.get(0));
            assertEquals("task_tool", tools.get(0).getCard().getName());
        }

        @Test
        void testGeneralPurposeSubagentInheritsParentMcps() {
            List<ToolCard> tools = List.of(ToolCard.builder()
                    .id("parent_tool")
                    .name("read_file")
                    .description("read file")
                    .build());
            List<McpServerConfig> mcps = List.of(McpServerConfig.builder()
                    .serverName("parent_mcp")
                    .serverId("mcp_parent_001")
                    .serverPath("http://127.0.0.1:8930/mcp")
                    .build());
            DeepAgentConfig config = new DeepAgentConfig();
            config.setCard(agentCard("parent", "test", "parent-id"));
            config.setSystemPrompt("parent prompt");
            config.setTools(tools);
            config.setMcps(mcps);
            config.setSkills(List.of("skill_a"));
            config.setAddGeneralPurposeAgent(true);
            DeepAgent parentAgent = HarnessFactory.createDeepAgent(config);

            DeepAgent sub = parentAgent.createSubagent("general-purpose", "sub_session_id");

            DeepAgentConfig subConfig = (DeepAgentConfig) sub.getConfig();
            assertEquals(tools, subConfig.getTools());
            assertEquals(mcps, subConfig.getMcps());
            assertEquals(List.of("skill_a"), subConfig.getSkills());
        }

        @Test
        void testExplicitGeneralPurposeSubagentOverridesDefault() {
            List<ToolCard> explicitTools = List.of(ToolCard.builder()
                    .id("custom_tool")
                    .name("custom_tool")
                    .description("custom tool")
                    .build());
            List<McpServerConfig> explicitMcps = List.of(McpServerConfig.builder()
                    .serverName("custom_mcp")
                    .serverId("custom_mcp_001")
                    .serverPath("http://127.0.0.1:8931/mcp")
                    .build());
            DeepAgentConfig explicitConfig = new DeepAgentConfig();
            explicitConfig.setCard(agentCard("general-purpose", "custom general subagent", "explicit-id"));
            explicitConfig.setSystemPrompt("custom prompt");
            explicitConfig.setTools(explicitTools);
            explicitConfig.setMcps(explicitMcps);
            explicitConfig.setSkills(List.of("skill_b"));
            DeepAgent explicitSubagent = HarnessFactory.createDeepAgent(explicitConfig);

            DeepAgentConfig parentConfig = new DeepAgentConfig();
            parentConfig.setCard(agentCard("parent", "test", "parent-id"));
            parentConfig.setSystemPrompt("parent prompt");
            parentConfig.setTools(List.of(ToolCard.builder()
                    .id("parent_tool")
                    .name("read_file")
                    .description("read file")
                    .build()));
            parentConfig.setSubagents(List.of(explicitSubagent));
            parentConfig.setSkills(List.of("skill_a"));
            parentConfig.setAddGeneralPurposeAgent(true);
            DeepAgent parentAgent = HarnessFactory.createDeepAgent(parentConfig);

            DeepAgent sub = parentAgent.createSubagent("general-purpose", "sub_session_id");

            DeepAgentConfig subConfig = (DeepAgentConfig) sub.getConfig();
            assertEquals(explicitTools, subConfig.getTools());
            assertEquals(explicitMcps, subConfig.getMcps());
            assertEquals(List.of("skill_b"), subConfig.getSkills());
        }
    }

    private static DeepAgent parentWithSubagent(String name, String id) {
        DeepAgent child = HarnessFactory.createDeepAgent(configWithCard(agentCard(name, name + " subagent", id)));
        DeepAgentConfig config = configWithCard(agentCard("parent", "test", "parent-id"));
        config.setSubagents(List.of(child));
        return HarnessFactory.createDeepAgent(config);
    }

    private static DeepAgentConfig configWithCard(AgentCard card) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setSystemPrompt("prompt");
        return config;
    }

    private static AgentCard agentCard(String name, String description, String id) {
        return AgentCard.builder()
                .name(name)
                .description(description)
                .id(id)
                .build();
    }

    private static ToolCard toolCard() {
        return ToolCard.builder()
                .id("task_tool_test")
                .name("task_tool")
                .description("test")
                .build();
    }

    private static final class FakeSession implements Session {
        private final String sessionId;

        private FakeSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> state) {
        }
    }
}
