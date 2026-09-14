/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AgentModePromptToolProvidersTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void switchModeProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new AgentModePromptToolProviders.SwitchModeMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");
        Map<String, Object> properties = castMap(schema.get("properties"));

        assertThat(provider.getName()).isEqualTo("switch_mode");
        assertThat(provider.getDescription("en")).contains("planning only");
        assertThat(provider.getDescription("cn")).contains("ask_user");
        assertThat(properties.keySet()).containsExactly("mode");
        assertThat(castList(schema.get("required"))).containsExactly("mode");
        assertThat(castMap(properties.get("mode")))
                .containsEntry("type", "string")
                .containsEntry("description", "Target mode: normal or plan");
        assertThat(castList(castMap(properties.get("mode")).get("enum"))).containsExactly("normal", "plan");
    }

    @Test
    void enterPlanModeProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new AgentModePromptToolProviders.EnterPlanModeMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");

        assertThat(provider.getName()).isEqualTo("enter_plan_mode");
        assertThat(provider.getDescription("en")).contains("Initialize the plan file");
        assertThat(castMap(schema.get("properties"))).isEmpty();
        assertThat(castList(schema.get("required"))).isEmpty();
    }

    @Test
    void exitPlanModeProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new AgentModePromptToolProviders.ExitPlanModeMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");

        assertThat(provider.getName()).isEqualTo("exit_plan_mode");
        assertThat(provider.getDescription("en")).contains("ending the planning phase");
        assertThat(castMap(schema.get("properties"))).isEmpty();
        assertThat(castList(schema.get("required"))).isEmpty();
    }

    @Test
    void validatePassesForAllAgentModeProviders() {
        assertThatCode(new AgentModePromptToolProviders.SwitchModeMetadataProvider()::validate).doesNotThrowAnyException();
        assertThatCode(new AgentModePromptToolProviders.EnterPlanModeMetadataProvider()::validate).doesNotThrowAnyException();
        assertThatCode(new AgentModePromptToolProviders.ExitPlanModeMetadataProvider()::validate).doesNotThrowAnyException();
    }
}
