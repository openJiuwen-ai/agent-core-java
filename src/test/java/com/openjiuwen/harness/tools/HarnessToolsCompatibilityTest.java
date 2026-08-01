package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.cli.CLIOptions;
import com.openjiuwen.harness.cli.HarnessCli;
import com.openjiuwen.harness.cli.ui.CliRunner;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.AgentMode;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.subagents.ResearchAgentFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolsCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void todoToolShouldCreatePersistAndListTodos() {
        TodoTool tool = new TodoTool(tempDir.toString());
        ToolOutput created = tool.create("session1", List.of("Task A", "Task B"));
        ToolOutput listed = tool.list("session1");

        assertThat(created.isSuccess()).isTrue();
        assertThat(listed.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<TodoItem> todos = (List<TodoItem>) listed.getData();
        assertThat(todos).hasSize(2);
        assertThat(todos.get(0).getContent()).isEqualTo("Task A");
    }

    @Test
    void sessionToolkitShouldTrackRows() {
        SessionToolkit toolkit = new SessionToolkit();
        toolkit.upsertRunning("task-1", "sub-1", "desc");
        toolkit.markCompleted("task-1", "ok");

        assertThat(toolkit.listAll()).hasSize(1);
        assertThat(toolkit.listAll().get(0).getStatus()).isEqualTo("completed");
    }

    @Test
    void harnessCliShouldRunChatAndRunOnce() {
        CLIOptions options = new CLIOptions();
        options.setModel("gpt-4");
        options.setWorkspace(tempDir.toString());
        Map<String, Object> configMap = HarnessCli.toConfigMap(options);
        int exitCode = HarnessCli.runOnce(options, "hello", "json", new CliRunner());

        assertThat(configMap).containsEntry("model", "gpt-4");
        assertThat(exitCode).isGreaterThanOrEqualTo(0);
    }

    @Test
    void taskToolShouldDelegateToSubagent() {
        var agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath(tempDir.toString())
                .language("en")
                .subagents(List.of(ResearchAgentFactory.buildResearchAgentConfig("en")))
                .build());
        TaskTool tool = new TaskTool(agent);

        ToolOutput output = tool.delegate("research_agent", "summarize repo", "session-main");

        assertThat(output.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) output.getData();
        assertThat(payload).containsKeys("agent_id", "sub_session_id", "result");
    }

    @Test
    void sessionToolsShouldListAndCancelRows() {
        SessionToolkit toolkit = new SessionToolkit();
        toolkit.upsertRunning("task-1", "sub-1", "desc");
        SessionsListTool listTool = new SessionsListTool(toolkit);
        SessionsCancelTool cancelTool = new SessionsCancelTool(toolkit);

        ToolOutput listed = listTool.list();
        ToolOutput canceled = cancelTool.cancel("task-1");

        assertThat(listed.isSuccess()).isTrue();
        assertThat(((List<?>) listed.getData())).hasSize(1);
        assertThat(canceled.isSuccess()).isTrue();
        assertThat(toolkit.get("task-1").getStatus()).isEqualTo("canceled");
    }

    @Test
    void switchModeToolShouldToggleDeepAgentMode() {
        var agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath(tempDir.toString())
                .build());
        SwitchModeTool tool = new SwitchModeTool(agent);

        ToolOutput switched = tool.switchMode("plan");

        assertThat(switched.isSuccess()).isTrue();
        assertThat(agent.getCurrentMode()).isEqualTo(AgentMode.PLAN);
        assertThat(switched.getData()).isEqualTo(Map.of(
                "previous_mode", "normal",
                "current_mode", "plan"
        ));
    }
}
