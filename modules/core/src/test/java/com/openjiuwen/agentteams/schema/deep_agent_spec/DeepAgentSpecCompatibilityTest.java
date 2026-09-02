
package com.openjiuwen.agentteams.schema.deep_agent_spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class DeepAgentSpecCompatibilityTest {
    @Test
    void shouldCreateVisionModelSpecWithDefaults() {
        VisionModelSpec spec = VisionModelSpec.builder().build();
        assertThat(spec.getBaseUrl()).isNotBlank();
        assertThat(spec.getMaxRetries()).isEqualTo(3);
    }

    @Test
    void shouldCreateVisionModelSpecWithCustomValues() {
        VisionModelSpec spec = VisionModelSpec.builder().apiKey("sk-test").baseUrl("https://custom.api")
                .model("gpt-4-vision").maxRetries(5).build();
        assertThat(spec.getApiKey()).isEqualTo("sk-test");
        assertThat(spec.getModel()).isEqualTo("gpt-4-vision");
        assertThat(spec.getMaxRetries()).isEqualTo(5);
    }

    @Test
    void shouldCreateAudioModelSpec() {
        AudioModelSpec spec = AudioModelSpec.builder().build();
        assertThat(spec.getTranscriptionModel()).isEqualTo("whisper-1");
        assertThat(spec.getMaxAudioBytes()).isGreaterThan(0);
    }

    @Test
    void shouldCreateWorkspaceSpec() {
        WorkspaceSpec spec = WorkspaceSpec.builder().rootPath("/tmp/ws").language("en").isStableBase(true).build();
        assertThat(spec.getRootPath()).isEqualTo("/tmp/ws");
        assertThat(spec.getLanguage()).isEqualTo("en");
        assertThat(spec.isStableBase()).isTrue();
    }

    @Test
    void shouldCreateSysOperationSpec() {
        SysOperationSpec spec = SysOperationSpec.builder().id("test.sys").build();
        assertThat(spec.getId()).isEqualTo("test.sys");
    }

    @Test
    void shouldCreateRailSpec() {
        RailSpec spec = RailSpec.builder().type("task_planning").params(Map.of("enabled", true)).build();
        assertThat(spec.getType()).isEqualTo("task_planning");
        assertThat(spec.getParams()).containsKey("enabled");
    }

    @Test
    void shouldCreateBuiltinToolSpec() {
        BuiltinToolSpec spec = BuiltinToolSpec.builder().type("web_search").build();
        assertThat(spec.getType()).isEqualTo("web_search");
    }

    @Test
    void shouldCreateProgressiveToolSpec() {
        ProgressiveToolSpec spec = ProgressiveToolSpec.builder().isEnabled(true).maxLoadedTools(10)
                .alwaysVisibleTools(List.of("read_file")).build();
        assertThat(spec.isEnabled()).isTrue();
        assertThat(spec.getMaxLoadedTools()).isEqualTo(10);
        assertThat(spec.getAlwaysVisibleTools()).contains("read_file");
    }

    @Test
    void shouldCreateDeepAgentSpec() {
        DeepAgentSpec spec = DeepAgentSpec.builder().systemPrompt("You are a helpful assistant").maxIterations(20)
                .isTaskLoopEnabled(true).build();
        assertThat(spec.getSystemPrompt()).isEqualTo("You are a helpful assistant");
        assertThat(spec.getMaxIterations()).isEqualTo(20);
        assertThat(spec.isTaskLoopEnabled()).isTrue();
    }

    @Test
    void shouldDefaultMaxIterationsTo15() {
        DeepAgentSpec spec = DeepAgentSpec.builder().build();
        assertThat(spec.getMaxIterations()).isEqualTo(15);
        assertThat(spec.getCompletionTimeout()).isEqualTo(600.0);
    }
}
