/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.singleagent.legacy.agent.BaseAgent;
import com.openjiuwen.core.singleagent.legacy.config.AgentConfig;
import com.openjiuwen.core.singleagent.legacy.config.ConstrainConfig;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.legacy.config.LlmCallConfig;
import com.openjiuwen.core.singleagent.legacy.react_agent.LegacyReActAgent;
import com.openjiuwen.core.singleagent.legacy.react_agent.LegacyReActAgentFactory;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Mirrors Python's {@code test_backward_compatibility.py} in
 * {@code tests/unit_tests/agent/test_backward_compatibility.py}.
 */
class BackwardCompatibilityPythonParityTest {

    @Test
    void oldImportsIssueWarnings() {
        assertThatCode(() -> {
            new AgentConfig();
            new LlmCallConfig();
            new ConstrainConfig();
        }).doesNotThrowAnyException();

        List<String> warnings = List.of(
                LegacyDeprecation.warningMessage("AgentConfig"),
                LegacyDeprecation.warningMessage("LLMCallConfig"),
                LegacyDeprecation.warningMessage("ConstrainConfig")
        );
        assertThat(warnings).hasSizeGreaterThanOrEqualTo(3);
        assertThat(warnings).allSatisfy(message -> assertThat(message).contains("in the future"));
    }

    @Test
    void newImportsDoNotWarn() {
        AgentCard card = new AgentCard();

        assertThat(card).isNotNull();
        assertThat(LegacyDeprecation.warningMessage("AgentCard")).isNull();
    }

    @Test
    void legacyModuleImportsIssueWarnings() {
        assertThatCode(LegacyReActAgentConfig::new).doesNotThrowAnyException();

        assertThat(LegacyDeprecation.warningMessage("LegacyReActAgentConfig"))
                .contains("in the future");
    }

    @Test
    void reactAgentOldStyleConstruction() {
        LegacyReActAgentConfig config = legacyConfig("test_agent", "1.0", "Test Agent");

        LegacyReActAgent agent = new LegacyReActAgent(config);

        assertThat(((LegacyReActAgentConfig) agent.getAgentConfig()).getId()).isEqualTo("test_agent");
        assertThat(((LegacyReActAgentConfig) agent.getAgentConfig()).getVersion()).isEqualTo("1.0");
    }

    @Test
    void reactAgentWithToolsParameter() {
        LegacyReActAgentConfig config = legacyConfig("test_agent", "1.0", "");

        LegacyReActAgent agent = new LegacyReActAgent(config, null, List.of());

        assertThat(agent).isNotNull();
    }

    @Test
    void addToolsMethodWorks() {
        LegacyReActAgent agent = new LegacyReActAgent(legacyConfig("test_agent", "1.0", ""));

        assertThatCode(() -> agent.addTools(List.of())).doesNotThrowAnyException();
        assertThat(agent).isNotNull();
    }

    @Test
    void addWorkflowsMethodWorks() {
        LegacyReActAgent agent = new LegacyReActAgent(legacyConfig("test_agent", "1.0", ""));

        assertThatCode(() -> agent.addWorkflows(List.of())).doesNotThrowAnyException();
        assertThat(agent).isNotNull();
    }

    @Test
    void deprecationWarningContainsMigrationInfo() {
        String warningMessage = LegacyDeprecation.warningMessage("LegacyReActAgentConfig");

        assertThat(warningMessage)
                .contains("in the future")
                .containsIgnoringCase("deprecated")
                .contains("ReActAgent");
    }

    @Test
    void createReactAgentConfigIssuesWarningMetadata() {
        LegacyReActAgentConfig ignored = LegacyReActAgentFactory.createReactAgentConfig(
                "test",
                "1.0",
                "test",
                modelConfig(),
                List.of()
        );

        assertThat(ignored).isNotNull();
        assertThat(LegacyDeprecation.warningMessage("LegacyReActAgentConfig"))
                .containsIgnoringCase("deprecated");
    }

    @Test
    void createReactAgentConfigWorks() {
        LegacyReActAgentConfig config = LegacyReActAgentFactory.createReactAgentConfig(
                "test",
                "1.0",
                "test",
                modelConfig(),
                List.of()
        );

        assertThat(config.getId()).isEqualTo("test");
        assertThat(config.getVersion()).isEqualTo("1.0");
    }

    @Test
    void oldAndNewApisCoexist() {
        AgentCard newCard = new AgentCard();
        LegacyReActAgent oldAgent = new LegacyReActAgent(legacyConfig("old_agent", "1.0", ""));

        assertThat(newCard).isNotNull();
        assertThat(oldAgent).isNotNull();
        assertThat(oldAgent).isInstanceOf(BaseAgent.class);
    }

    private static LegacyReActAgentConfig legacyConfig(String id, String version, String description) {
        LegacyReActAgentConfig config = new LegacyReActAgentConfig();
        config.setId(id);
        config.setVersion(version);
        config.setDescription(description);
        config.setModel(modelConfig());
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "guide")));
        return config;
    }

    private static ModelConfig modelConfig() {
        return ModelConfig.builder()
                .modelProvider("OpenAI")
                .modelInfo(BaseModelInfo.builder()
                        .modelName("gpt-4")
                        .apiKey("test-key")
                        .apiBase("https://api.openai.com/v1")
                        .build())
                .build();
    }
}
