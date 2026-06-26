/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.subagent;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentFactory;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <p>Mirrors Python's {@code tests/unit_tests/harness/tools/test_task_tool.py}.</p>
 */
class TaskToolPythonParityTest {

    private static final Pattern RANDOM_CODE_SESSION =
            Pattern.compile("parent_session_sub_code_[0-9a-f]{8}");

    @Test
    @SuppressWarnings("unchecked")
    void taskToolInvokeSuccess() throws Exception {
        FakeSubagent subagent = new FakeSubagent("test_id", "test_agent");
        FakeParentAgent parent = new FakeParentAgent(subagent);
        TaskTool tool = new TaskTool(taskCard(), parent);

        ToolOutput output = (ToolOutput) tool.invoke(
                Map.of("subagent_type", "code", "task_description", "run task"),
                Map.of("session", new TestSession("parent_session"))
        );
        Map<String, Object> data = (Map<String, Object>) output.getData();

        assertThat(output.isSuccess()).isTrue();
        assertThat(data).containsEntry("output", "done").containsEntry("agent_id", "test_id");
        assertThat(output.getError()).isNull();
        assertThat(subagent.lastInputs()).containsEntry("query", "run task");
        assertThat(String.valueOf(subagent.lastInputs().get("conversation_id"))).matches(RANDOM_CODE_SESSION);
        assertThat(parent.lastSubagentType()).isEqualTo("code");
    }

    @Test
    void taskToolInvokeInvalidSession() {
        TaskTool tool = new TaskTool(taskCard(), new FakeParentAgent(new FakeSubagent("test_id", "test_agent")));

        assertThatThrownBy(() -> tool.invoke(
                Map.of("subagent_type", "code", "task_description", "run task"),
                Map.of("session", "not-session")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid session");
    }

    @Test
    void taskToolInvokeMissingRequiredFields() {
        TaskTool tool = new TaskTool(taskCard(), new FakeParentAgent(new FakeSubagent("test_id", "test_agent")));

        assertThatThrownBy(() -> tool.invoke(
                Map.of("subagent_type", "code"),
                Map.of("session", new TestSession("parent_session"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    @SuppressWarnings("unchecked")
    void taskToolReusesStickyBrowserSubsessionId() throws Exception {
        FakeSubagent subagent = new FakeSubagent("test_id", "browser_agent");
        TaskTool tool = new TaskTool(taskCard(), new FakeParentAgent(subagent));

        ToolOutput output = (ToolOutput) tool.invoke(
                Map.of("subagent_type", "browser_agent", "task_description", "continue browser task"),
                Map.of("session", new TestSession("parent_session"))
        );
        Map<String, Object> data = (Map<String, Object>) output.getData();

        assertThat(output.isSuccess()).isTrue();
        assertThat(data).containsEntry("output", "done");
        assertThat(subagent.lastInputs()).containsEntry("conversation_id", "parent_session_sub_browser_agent");
    }

    @Test
    void createTaskToolReturnsSingleTaskTool() {
        List<Tool> tools = TaskTool.createTaskTool(new DeepAgent(), "code,search", "cn");

        assertThat(tools).hasSize(1);
        assertThat(tools.getFirst()).isInstanceOf(TaskTool.class);
        assertThat(tools.getFirst().getCard().getName()).isEqualTo("task_tool");
    }

    @Test
    void generalPurposeSubagentInheritsParentMcps() {
        Tool parentTool = new DummyTool("read_file");
        McpServerConfig mcp = McpServerConfig.builder()
                .serverName("parent_mcp")
                .serverId("mcp_parent_001")
                .serverPath("http://127.0.0.1:8930/mcp")
                .build();
        DeepAgentConfig config = new DeepAgentConfig();
        config.setTools(List.of(parentTool));
        config.setMcps(List.of(mcp));
        config.setSkills(List.of("skill_a"));
        config.setSubagents(DeepAgentFactory.injectGeneralPurposeSubagent(Map.of()));
        DeepAgent parent = new DeepAgent(new AgentCard("parent", "parent", "test"));
        parent.configure(config);

        DeepAgent subagent = parent.createSubagent("general-purpose", "sub_session_id");

        assertThat(subagent.deepConfig().getTools()).containsExactly(parentTool);
        assertThat(subagent.deepConfig().getMcps()).containsExactly(mcp);
        assertThat(subagent.deepConfig().getSkills()).isEqualTo(List.of("skill_a"));
    }

    @Test
    void explicitGeneralPurposeSubagentOverridesDefault() {
        Tool parentTool = new DummyTool("read_file");
        Tool customTool = new DummyTool("custom_tool");
        McpServerConfig customMcp = McpServerConfig.builder()
                .serverName("custom_mcp")
                .serverId("custom_mcp_001")
                .serverPath("http://127.0.0.1:8931/mcp")
                .build();
        DeepAgentConfig.SubAgentConfig explicit = new DeepAgentConfig.SubAgentConfig(
                "general-purpose",
                "custom general subagent",
                "custom prompt"
        );
        explicit.setTools(List.of(customTool));
        explicit.setMcps(List.of(customMcp));
        explicit.setSkills(List.of("skill_b"));
        DeepAgentConfig config = new DeepAgentConfig();
        config.setTools(List.of(parentTool));
        config.setSkills(List.of("skill_a"));
        config.setSubagents(DeepAgentFactory.injectGeneralPurposeSubagent(Map.of("general-purpose", explicit)));
        DeepAgent parent = new DeepAgent(new AgentCard("parent", "parent", "test"));
        parent.configure(config);

        DeepAgent subagent = parent.createSubagent("general-purpose", "sub_session_id");

        assertThat(subagent.deepConfig().getTools()).containsExactly(customTool);
        assertThat(subagent.deepConfig().getMcps()).containsExactly(customMcp);
        assertThat(subagent.deepConfig().getSkills()).isEqualTo(List.of("skill_b"));
    }

    private static ToolCard taskCard() {
        return new ToolCard("task_tool_test", "task_tool", "test");
    }

    private static final class FakeParentAgent extends DeepAgent {
        private final DeepAgent subagent;
        private String lastSubagentType;

        private FakeParentAgent(DeepAgent subagent) {
            super(new AgentCard("parent", "parent", "test"));
            this.subagent = subagent;
        }

        @Override
        public DeepAgent createSubagent(String subagentType, String subsessionId) {
            lastSubagentType = subagentType;
            return subagent;
        }

        private String lastSubagentType() {
            return lastSubagentType;
        }
    }

    private static final class FakeSubagent extends DeepAgent {
        private Map<String, Object> lastInputs = Map.of();

        private FakeSubagent(String id, String name) {
            super(new AgentCard(id, name, "test"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> invoke(Map<String, Object> inputs) {
            lastInputs = new LinkedHashMap<>(inputs);
            return CompletableFuture.completedFuture(Map.of("output", "done"));
        }

        private Map<String, Object> lastInputs() {
            return lastInputs;
        }
    }

    private record TestSession(String sessionId) implements AgentSessionApi {
        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> data) {
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return List.of().iterator();
        }
    }

    private static final class DummyTool extends Tool {
        private DummyTool(String name) {
            super(new ToolCard(name, name, name));
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return Map.of("inputs", inputs);
        }
    }
}
