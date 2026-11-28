package com.openjiuwen.deepagents;

import com.openjiuwen.core.foundation.llm.classification.ModelCallRetryPolicy;
import com.openjiuwen.core.foundation.llm.modelclients.ModelTimeoutResolver;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

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
    void factoryShouldReadModelTimeoutsFromYaml() throws Exception {
        Path configPath = tempDir.resolve("model-timeouts.yaml");
        Files.writeString(configPath, """
                schema_version: harness_config.v0.1
                name: Timeout Agent
                model:
                  model: test-model
                backend:
                  client_provider: openai
                  api_key: test-key
                  api_base: https://model.example.com/v1
                  connect_timeout_seconds: 12
                  stream_idle_seconds: 300
                  timeout: 120
                  max_retries: 4
                  retry_on_rate_limit: false
                  retry_on_temporary: false
                """);

        DeepAgent agent = new DeepAgentsFactory().createDeepAgent(configPath);

        ModelClientConfig client = (ModelClientConfig) agent.getConfig().getBackend();
        assertThat(client.getConnectTimeoutSeconds()).isEqualTo(12.0D);
        assertThat(client.getStreamIdleSeconds()).isEqualTo(300.0D);
        assertThat(client.getTimeout()).isEqualTo(120.0D);
        assertThat(client.isTimeoutExplicit()).isTrue();
        assertThat(client.getMaxRetries()).isEqualTo(4);
        assertThat(client.isRetryOnRateLimit()).isFalse();
        assertThat(client.isRetryOnTemporary()).isFalse();
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void factoryShouldUseJvmDefaultsForOmittedYamlSettings() throws Exception {
        String previousIdle = System.getProperty("openjiuwen.llm.http.stream-idle-seconds");
        String previousRetries = System.getProperty("openjiuwen.llm.retry.max-retries");
        try {
            System.setProperty("openjiuwen.llm.http.stream-idle-seconds", "450");
            System.setProperty("openjiuwen.llm.retry.max-retries", "4");
            Path configPath = tempDir.resolve("jvm-timeouts.yaml");
            Files.writeString(configPath, """
                    schema_version: harness_config.v0.1
                    backend:
                      client_provider: openai
                      api_key: test-key
                      api_base: https://model.example.com/v1
                    """);

            DeepAgent agent = new DeepAgentsFactory().createDeepAgent(configPath);

            ModelClientConfig client = (ModelClientConfig) agent.getConfig().getBackend();
            assertThat(ModelTimeoutResolver.resolveStreamIdleSeconds(client)).isEqualTo(450.0D);
            assertThat(ModelTimeoutResolver.resolveHeaderWaitSeconds(client)).isEqualTo(450.0D);
            assertThat(ModelCallRetryPolicy.from(client).maxRetries()).isEqualTo(4);
        } finally {
            restoreProperty("openjiuwen.llm.http.stream-idle-seconds", previousIdle);
            restoreProperty("openjiuwen.llm.retry.max-retries", previousRetries);
        }
    }

    @Test
    @Tag("system-test")
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

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
