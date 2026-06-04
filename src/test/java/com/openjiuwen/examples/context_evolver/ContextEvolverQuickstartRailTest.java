/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.context_evolver;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.service.AddMemoryRequest;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.rails.evolution.ContextEvolutionRail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextEvolverQuickstartRailTest {

    private Map<String, Object> configSnapshot;

    @TempDir
    Path tempDir;

    @BeforeEach
    void captureConfig() {
        configSnapshot = Config.snapshot();
    }

    @AfterEach
    void restoreConfig() {
        Config.restore(configSnapshot);
    }

    @Test
    void applyDefaultsMatchesPythonQuickstartRailDefaults() {
        ContextEvolverQuickstartRail.applyDefaults();

        assertThat(Config.get("API_KEY")).isEqualTo("your_api_key_here");
        assertThat(Config.get("API_BASE")).isEqualTo("https://api.openai.com/v1");
        assertThat(Config.get("MODEL_NAME")).isEqualTo("gpt-5.2");
        assertThat(Config.get("MODEL_PROVIDER")).isEqualTo("OpenAI");
        assertThat(Config.get("EMBEDDING_MODEL")).isEqualTo("text-embedding-3-small");
        assertThat(Config.get("EMBEDDING_DIMENSIONS")).isEqualTo(2560);
        assertThat(Config.get("LLM_TEMPERATURE")).isEqualTo(0.7d);
        assertThat(Config.get("LLM_SEED")).isEqualTo(42);
        assertThat(Config.get("LLM_SSL_VERIFY")).isEqualTo(false);
    }

    @Test
    void apiKeyCheckRejectsPlaceholderAndAcceptsRealValue() {
        ContextEvolverQuickstartRail.applyDefaults();
        assertThat(ContextEvolverQuickstartRail.hasConfiguredApiKey()).isFalse();

        Config.setValue("API_KEY", "sk-real");
        assertThat(ContextEvolverQuickstartRail.hasConfiguredApiKey()).isTrue();
    }

    @Test
    void modelConfigsAreBuiltFromCurrentConfig() {
        ContextEvolverQuickstartRail.applyDefaults();
        Config.setValue("API_KEY", "sk-test");
        Config.setValue("API_BASE", "https://example.test/v1");
        Config.setValue("MODEL_NAME", "gpt-test");
        Config.setValue("MODEL_PROVIDER", "OpenAI");
        Config.setValue("LLM_TEMPERATURE", 0.25d);
        Config.setValue("LLM_SEED", 7);
        Config.setValue("LLM_SSL_VERIFY", false);

        ModelClientConfig clientConfig = ContextEvolverQuickstartRail.buildModelClientConfig();
        ModelRequestConfig requestConfig = ContextEvolverQuickstartRail.buildModelRequestConfig();

        assertThat(clientConfig.getClientProvider()).isEqualTo("OpenAI");
        assertThat(clientConfig.getApiKey()).isEqualTo("sk-test");
        assertThat(clientConfig.getApiBase()).isEqualTo("https://example.test/v1");
        assertThat(clientConfig.isVerifySsl()).isFalse();
        assertThat(requestConfig.getModelName()).isEqualTo("gpt-test");
        assertThat(requestConfig.getTemperature()).isEqualTo(0.25d);
        assertThat(requestConfig.getSeed()).isEqualTo(7);
    }

    @Test
    void memoryServiceUsesRefconJsonPersistenceAndSeedsBothMemories() {
        ContextEvolverQuickstartRail.applyDefaults();

        TaskMemoryService memoryService = ContextEvolverQuickstartRail.buildMemoryService(tempDir);
        List<Map<String, Object>> results =
                ContextEvolverQuickstartRail.seedDemoMemories(memoryService, ContextEvolverQuickstartRail.DEFAULT_USER_ID);

        assertThat(memoryService.getRetrievalAlgorithm()).isEqualTo("RefCon");
        assertThat(memoryService.getSummaryAlgorithm()).isEqualTo("RefCon");
        assertThat(memoryService.getPersistType()).isEqualTo("json");
        assertThat(memoryService.getPersistPath()).isEqualTo(
                tempDir.resolve("memory_files").resolve("{algo_name}").resolve("{user_id}.json").toString());
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(result -> assertThat(result).containsEntry("status", "success"));
        assertThat(memoryService.getVectorStore().getAll()).hasSize(2);
        assertThat(Files.exists(tempDir.resolve("memory_files").resolve("reme").resolve("demo_user.json"))).isTrue();
    }

    @Test
    void memoryRequestsMatchSeededPythonDemoTopics() {
        AddMemoryRequest debugging = ContextEvolverQuickstartRail.pythonDebuggingMemoryRequest();
        AddMemoryRequest testing = ContextEvolverQuickstartRail.pythonUnitTestingMemoryRequest();

        assertThat(debugging.getWhenToUse()).isEqualTo("When asked how to debug Python code or find bugs");
        assertThat(debugging.getContent()).contains("pdb.set_trace()", "PYTHONASYNCIODEBUG=1");
        assertThat(testing.getWhenToUse()).isEqualTo("When asked how to write or structure Python unit tests");
        assertThat(testing.getContent()).contains("pytest", "unittest.mock.patch", "pytest --tb=short");
    }

    @Test
    void railAndAgentConfigMirrorCreateDeepAgentArguments() {
        ContextEvolverQuickstartRail.applyDefaults();
        Config.setValue("API_KEY", "sk-test");
        TaskMemoryService memoryService = ContextEvolverQuickstartRail.buildMemoryService(tempDir);
        ContextEvolutionRail rail = ContextEvolverQuickstartRail.buildRail("demo_user", memoryService);

        DeepAgentConfig config = ContextEvolverQuickstartRail.buildAgentConfig(rail);
        DeepAgent agent = ContextEvolverQuickstartRail.buildAgent(rail);

        AgentCard card = config.getCard();
        assertThat(card.getId()).isEqualTo("mem_agent");
        assertThat(card.getName()).isEqualTo("Memory-augmented DeepAgent");
        assertThat(card.getDescription()).isEqualTo("Memory-augmented DeepAgent");
        assertThat(config.getSystemPrompt()).isEqualTo(ContextEvolverQuickstartRail.SYSTEM_PROMPT);
        assertThat(config.getMaxIterations()).isEqualTo(5);
        assertThat(config.getRails()).containsExactly(rail);
        assertThat(config.getModelRequestConfig().getModelName()).isEqualTo("gpt-5.2");
        assertThat(config.getModelClientConfig().getClientProvider()).isEqualTo("OpenAI");
        assertThat(rail.getUserId()).isEqualTo("demo_user");
        assertThat(rail.getMemoryService()).isSameAs(memoryService);
        assertThat(rail.isInjectMemoriesInContext()).isTrue();
        assertThat(rail.isAutoSummarize()).isTrue();
        assertThat(rail.getAgent()).isSameAs(agent);
    }

    @Test
    void formattingHelpersAndInvocationShapeMatchScript() {
        assertThat(ContextEvolverQuickstartRail.bannerLines("Title"))
                .containsExactly("", ContextEvolverQuickstartRail.DIVIDER, "Title", ContextEvolverQuickstartRail.DIVIDER);
        assertThat(ContextEvolverQuickstartRail.sectionLines("Step"))
                .containsExactly("", "  Step", "  " + ContextEvolverQuickstartRail.SUBDIV);
        assertThat(ContextEvolverQuickstartRail.invocationInput("Question")).containsEntry("query", "Question");
        assertThat(ContextEvolverQuickstartRail.sessionId(3)).isEqualTo("demo_session_3");

        Map<String, Object> result = Map.of(
                "output", String.join("\n", "one", "two", "three", "four", "five", "six", "seven"),
                "memories_used", 2
        );
        assertThat(ContextEvolverQuickstartRail.logResultLines(result)).containsExactly(
                "  memories_used : 2",
                "  Response      :",
                "    one",
                "    two",
                "    three",
                "    four",
                "    five",
                "    six",
                "    ... (truncated)"
        );
        assertThat(ContextEvolverQuickstartRail.summaryLines(
                Map.of("memories_used", 1),
                Map.of("memories_used", 2),
                Map.of("memories_used", 3),
                4
        )).containsExactly(
                "  Invoke 1 memories_used : 1",
                "  Invoke 2 memories_used : 2",
                "  Invoke 3 memories_used : 3",
                "  Total nodes in store   : 4"
        );
    }
}
