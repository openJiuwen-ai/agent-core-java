
package com.openjiuwen.deepagents;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.deepagents.tools.DeepAgentToolKit;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class DeepAgentToolKitCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void toolkitShouldDelegateAndTrackCompletedTasks() {
        var parent = HarnessFactory
                .createDeepAgent(DeepAgentConfig.builder().workspacePath(tempDir.toString()).language("en").build());
        DeepAgentToolKit toolKit = new DeepAgentToolKit(parent);

        ToolOutput output = toolKit.delegate("task-1", "research_agent", "summarize repo", "session-main");

        assertThat(output.isSuccess()).isTrue();
        assertThat(toolKit.tasks()).hasSize(1);
        assertThat(toolKit.getTask("task-1").getStatus()).isEqualTo("completed");
        assertThat(toolKit.listSessions().isSuccess()).isTrue();
    }

    @Test
    void toolkitShouldCancelTrackedTask() {
        var parent = HarnessFactory
                .createDeepAgent(DeepAgentConfig.builder().workspacePath(tempDir.toString()).language("en").build());
        DeepAgentToolKit toolKit = new DeepAgentToolKit(parent);
        toolKit.delegate("task-2", "plan_agent", "plan changes", "session-main");

        ToolOutput canceled = toolKit.cancelSession("task-2");

        assertThat(canceled.isSuccess()).isTrue();
        assertThat(toolKit.getTask("task-2").getStatus()).isEqualTo("canceled");
        Map<?, ?> payload = (Map<?, ?>) canceled.getData();
        assertThat(payload.get("status")).isEqualTo("canceled");
    }

    @Test
    void toolkitShouldExposeSessionRowsThroughFacade() {
        var parent = HarnessFactory
                .createDeepAgent(DeepAgentConfig.builder().workspacePath(tempDir.toString()).language("en").build());
        DeepAgentToolKit toolKit = new DeepAgentToolKit(parent);
        toolKit.delegate("task-a", "code_agent", "modify code", "session-main");
        toolKit.delegate("task-b", "verification_agent", "run checks", "session-main");

        ToolOutput listed = toolKit.listSessions();

        assertThat(listed.isSuccess()).isTrue();
        assertThat((List<?>) listed.getData()).hasSize(2);
    }
}
