package com.openjiuwen.deepagents;

import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeepAgentsFactoryCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void factoryShouldCreateDefaultAgent() {
        DeepAgentsFactory factory = new DeepAgentsFactory();
        DeepAgent agent = factory.createDeepAgent();

        assertThat(agent.getCard().getName()).isEqualTo("deep_agent");
    }

    @Test
    void factoryShouldAcceptDeepAgentConfig() {
        DeepAgentsFactory factory = new DeepAgentsFactory();
        DeepAgent agent = factory.createDeepAgent(DeepAgentConfig.builder().systemPrompt("Hi").workspacePath("./x").build());

        assertThat(agent.getConfig().getSystemPrompt()).isEqualTo("Hi");
    }

    @Test
    void factoryShouldAcceptHarnessConfigPathAndModel() throws Exception {
        Path configPath = tempDir.resolve("factory.yaml");
        Files.writeString(configPath, """
                schema_version: harness_config.v0.1
                name: Factory Agent
                prompts:
                  sections:
                    - name: identity
                      content: factory prompt
                """);

        DeepAgentsFactory factory = new DeepAgentsFactory();
        DeepAgent fromPath = factory.createDeepAgent(configPath);
        DeepAgent fromString = factory.createDeepAgent(configPath.toString());
        DeepAgent fromModel = factory.createDeepAgent(HarnessConfig.builder()
                .name("Model Factory Agent")
                .prompts(HarnessConfig.PromptsSchema.builder()
                        .sections(List.of(HarnessConfig.SectionSchema.builder()
                                .name("identity")
                                .content("inline prompt")
                                .build()))
                        .build())
                .build());

        assertThat(fromPath.getCard().getName()).isEqualTo("Factory Agent");
        assertThat(fromString.getConfig().getSystemPrompt()).isEqualTo("factory prompt");
        assertThat(fromModel.getCard().getName()).isEqualTo("Model Factory Agent");
    }
}
