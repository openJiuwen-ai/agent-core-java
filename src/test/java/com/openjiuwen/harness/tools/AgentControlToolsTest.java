package com.openjiuwen.harness.tools;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's task/session agent-control test intent for P1-02.
 */
class AgentControlToolsTest {

    private AgentCard agentCard(String name, String description, String id) {
        return AgentCard.builder()
                .name(name)
                .description(description)
                .id(id)
                .build();
    }

    @Test
    void taskToolUsesSubagentTypeAndReturnsAgentId() throws Exception {
        DeepAgent parent = new DeepAgent(agentCard("parent", "parent", "parent-id"));
        DeepAgent child = new DeepAgent(agentCard("code", "code", "test_id"));
        DeepAgentConfig config = new DeepAgentConfig();
        config.setSubagents(List.of(child));
        config.setSessionToolkit(new DeepAgentConfig.SessionToolkit());
        parent.configure(config);

        TaskTool tool = new TaskTool(parent) {
            @Override
            protected Object runSubagent(DeepAgent target, Map<String, Object> toolArgs, com.openjiuwen.core.session.AgentSessionApi childSession) {
                return Map.of("output", "done");
            }
        };
        FakeSession session = new FakeSession("parent_session");

        ToolOutput output = (ToolOutput) tool.invoke(Map.of(
                "subagent_type", "code",
                "task_description", "run task"
        ), Map.of("session", session));

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertEquals("test_id", data.get("agent_id"));
    }

    @Test
    void taskToolStickyBrowserSubsessionId() throws Exception {
        DeepAgent parent = new DeepAgent(agentCard("parent", "parent", "parent-id"));
        DeepAgent child = new DeepAgent(agentCard("browser_agent", "browser", "browser-id"));
        DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
        DeepAgentConfig config = new DeepAgentConfig();
        config.setSubagents(List.of(child));
        config.setSessionToolkit(toolkit);
        parent.configure(config);

        TaskTool tool = new TaskTool(parent) {
            @Override
            protected Object runSubagent(DeepAgent target, Map<String, Object> toolArgs, com.openjiuwen.core.session.AgentSessionApi childSession) {
                return Map.of("output", "done");
            }
        };
        ToolOutput output = (ToolOutput) tool.invoke(Map.of(
                "subagent_type", "browser_agent",
                "task_description", "open browser"
        ), Map.of("session", new FakeSession("parent_session")));

        assertTrue(output.isSuccess());
        List<Map<String, Object>> tasks = toolkit.listTasks();
        assertEquals(1, tasks.size());
        assertEquals("parent_session_sub_browser_agent", tasks.get(0).get("task_id"));
    }

    @Test
    void sessionsSpawnRegistersTaskRowAndReturnsTaskId() {
        DeepAgent parent = new DeepAgent(agentCard("parent", "parent", "parent-id"));
        DeepAgent child = new DeepAgent(agentCard("code", "code", "code-id"));
        DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
        DeepAgentConfig config = new DeepAgentConfig();
        config.setSubagents(List.of(child));
        config.setSessionToolkit(toolkit);
        parent.configure(config);

        SessionsSpawnTool tool = new SessionsSpawnTool(parent, toolkit);
        ToolOutput output = (ToolOutput) tool.invoke(Map.of(
                "agent_name", "code",
                "description", "hello",
                "prompt", "hello"
        ), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertNotNull(data.get("task_id"));
        assertFalse(String.valueOf(data.get("task_id")).isBlank());
        assertEquals(1, toolkit.listTasks().size());
    }

    @Test
    void sessionsListRendersEmptyAndOneRow() {
        DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
        SessionsListTool cnTool = new SessionsListTool(toolkit, "cn");
        ToolOutput empty = (ToolOutput) cnTool.invoke(Map.of(), Map.of());
        assertTrue(String.valueOf(empty.getData()).contains("没有后台"));

        toolkit.upsertTask("tid", "sid", "hello", "running");
        SessionsListTool enTool = new SessionsListTool(toolkit, "en");
        ToolOutput one = (ToolOutput) enTool.invoke(Map.of(), Map.of());
        String text = String.valueOf(one.getData());
        assertTrue(text.contains("tid"));
        assertTrue(text.contains("hello"));
        assertTrue(text.contains("running"));
    }

    private static final class FakeSession implements com.openjiuwen.core.session.Session {
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
