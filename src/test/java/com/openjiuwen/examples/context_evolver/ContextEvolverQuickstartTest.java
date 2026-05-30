/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.context_evolver;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.context_evolver.ContextEvolvingReActAgent;
import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.extensions.context_evolver.tool.WikipediaTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextEvolverQuickstartTest {
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
    void applyDefaultsMatchesPythonQuickstartDefaults() {
        ContextEvolverQuickstart.applyDefaults();

        assertThat(Config.get("API_BASE")).isEqualTo("https://api.openai.com/v1");
        assertThat(Config.get("MODEL_NAME")).isEqualTo("gpt-5.2");
        assertThat(Config.get("EMBEDDING_DIMENSIONS")).isEqualTo(2560);
        assertThat(Config.get("LLM_SSL_VERIFY")).isEqualTo(false);
    }

    @Test
    void apiKeyCheckRejectsPlaceholderAndAcceptsRealValue() {
        ContextEvolverQuickstart.applyDefaults();
        assertThat(ContextEvolverQuickstart.hasConfiguredApiKey()).isFalse();

        Config.setValue("API_KEY", "sk-real");
        assertThat(ContextEvolverQuickstart.hasConfiguredApiKey()).isTrue();
    }

    @Test
    void buildAgentConfigUsesCurrentConfigAndMaxIterationsFive() {
        ContextEvolverQuickstart.applyDefaults();
        Config.setValue("API_KEY", "sk-test");
        Config.setValue("MODEL_PROVIDER", "OpenAI");
        Config.setValue("MODEL_NAME", "gpt-test");

        ReActAgentConfig config = ContextEvolverQuickstart.buildAgentConfig();

        assertThat(config.getApiKey()).isEqualTo("sk-test");
        assertThat(config.getModelProvider()).isEqualTo("OpenAI");
        assertThat(config.getModelName()).isEqualTo("gpt-test");
        assertThat(config.getMaxIterations()).isEqualTo(5);
    }

    @Test
    void buildAndConfigureAgentRegistersWikipediaTool() {
        ContextEvolverQuickstart.applyDefaults();
        ContextEvolvingReActAgent agent = ContextEvolverQuickstart.buildAgent(
                new TaskMemoryService("gpt-5.2", "text-embedding-3-small", null, "ACE", "ACE"),
                tempDir.toString()
        );
        Tool wikipediaTool = WikipediaTool.createWikipediaTool(query -> "Title: Java\nSummary: " + query);

        ContextEvolverQuickstart.configureAgent(agent, wikipediaTool);

        AgentCard card = agent.getCard();
        assertThat(card.getId()).isEqualTo("demo-agent-refcon");
        assertThat(agent.getUserId()).isEqualTo("demo_user_hotpot_refcon");
        assertThat(agent.isInjectMemoriesInContext()).isTrue();
        assertThat(agent.getAbilityManager().listToolInfo())
                .anyMatch(toolInfo -> "wikipedia_search".equals(toolInfo.getName()));
    }

    @Test
    void bannersSectionsInvocationAndSummaryFormattingMatchScriptShape() {
        assertThat(ContextEvolverQuickstart.bannerLines("Title"))
                .containsExactly("", ContextEvolverQuickstart.DIVIDER, "Title", ContextEvolverQuickstart.DIVIDER);
        assertThat(ContextEvolverQuickstart.sectionLines("Step"))
                .containsExactly("", "  Step", "  " + ContextEvolverQuickstart.SUBDIV);
        assertThat(ContextEvolverQuickstart.invocationInput("Question")).containsEntry("query", "Question");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", "x".repeat(130));
        List<String> lines = ContextEvolverQuickstart.summarizeResult(result);

        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).startsWith("    answer");
        assertThat(lines.getFirst()).hasSize("    ".length() + 20 + ": ".length() + 120);
        assertThat(ContextEvolverQuickstart.summarizeResult(Map.of())).containsExactly("  No summary result returned.");
    }
}
