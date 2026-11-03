/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_template_integration.py} in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/llm_agent/test_template_integration.py}.
 */
class LlmAgentTemplateIntegrationPythonParityTest {

    @Test
    void templateStructure() {
        Map<String, Object> template = LlmAgentTemplate.create();

        assertThat(template)
                .containsKeys(
                        "agent_id",
                        "agent_type",
                        "name",
                        "description",
                        "configs",
                        "model",
                        "plugins"
                );
    }

    @Test
    void templateDefaultValues() {
        Map<String, Object> template = LlmAgentTemplate.create();

        assertThat(template.get("agent_id")).isEqualTo("");
        assertThat(template.get("agent_type")).isEqualTo("react");
        assertThat(template.get("edit_mode")).isEqualTo("manual");
    }

    @Test
    @SuppressWarnings("unchecked")
    void templateConfigsStructure() {
        Map<String, Object> template = LlmAgentTemplate.create();
        Map<String, Object> configs = (Map<String, Object>) template.get("configs");

        assertThat(configs).containsKey("system_prompt");
    }

    @Test
    @SuppressWarnings("unchecked")
    void templateConstraintsStructure() {
        Map<String, Object> template = LlmAgentTemplate.create();
        Map<String, Object> constraints = (Map<String, Object>) template.get("constraints");

        assertThat(constraints).containsKey("max_iterations");
        assertThat(constraints).containsKey("reserved_max_chat_rounds");
        assertThat(constraints.get("max_iterations")).isEqualTo(5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void templateMemoryStructure() {
        Map<String, Object> template = LlmAgentTemplate.create();
        Map<String, Object> memory = (Map<String, Object>) template.get("memory");

        assertThat(memory).containsKey("max_tokens");
        assertThat(memory.get("max_tokens")).isEqualTo(1000);
    }

    @Test
    @SuppressWarnings("unchecked")
    void templateModelStructure() {
        Map<String, Object> template = LlmAgentTemplate.create();
        Map<String, Object> model = (Map<String, Object>) template.get("model");
        Map<String, Object> modelInfo = (Map<String, Object>) model.get("model_info");

        assertThat(model).containsKeys("model_info", "model_provider");
        assertThat(modelInfo)
                .containsKeys(
                        "api_base",
                        "api_key",
                        "model_name",
                        "temperature",
                        "top_p",
                        "max_tokens",
                        "streaming"
                );
    }

    @Test
    @SuppressWarnings("unchecked")
    void templateModelDefaults() {
        Map<String, Object> template = LlmAgentTemplate.create();
        Map<String, Object> model = (Map<String, Object>) template.get("model");
        Map<String, Object> modelInfo = (Map<String, Object>) model.get("model_info");

        assertThat(modelInfo.get("streaming")).isEqualTo(true);
        assertThat(modelInfo.get("max_tokens")).isEqualTo(2048);
        assertThat(modelInfo.get("timeout")).isEqualTo(1000);
    }

    @Test
    void templateEmptyCollections() {
        Map<String, Object> template = LlmAgentTemplate.create();

        assertThat((List<?>) template.get("plugins")).isEmpty();
        assertThat((List<?>) template.get("knowledge")).isEmpty();
        assertThat((List<?>) template.get("workflows")).isEmpty();
        assertThat((List<?>) template.get("triggers")).isEmpty();
        assertThat((List<?>) template.get("prompt_template")).isEmpty();
    }

    @Test
    void templateNullableFields() {
        Map<String, Object> template = LlmAgentTemplate.create();

        assertThat(template.get("create_time")).isNull();
        assertThat(template.get("update_time")).isNull();
        assertThat(template.get("latest_publish_time")).isNull();
        assertThat(template.get("latest_publish_version")).isNull();
    }

    @Test
    void templateCanBeCopied() {
        Map<String, Object> template = LlmAgentTemplate.create();
        Map<String, Object> templateCopy = LlmAgentTemplate.deepCopy(template);

        assertThat(templateCopy).isNotSameAs(template);
        assertThat(templateCopy).isEqualTo(template);
    }

    @Test
    @SuppressWarnings("unchecked")
    void templateModificationDoesNotAffectOriginal() {
        Map<String, Object> template = LlmAgentTemplate.create();
        Map<String, Object> templateCopy = LlmAgentTemplate.deepCopy(template);
        Map<String, Object> model = (Map<String, Object>) templateCopy.get("model");
        Map<String, Object> modelInfo = (Map<String, Object>) model.get("model_info");

        templateCopy.put("name", "Test Agent");
        modelInfo.put("temperature", 0.5);

        assertThat(template.get("name")).isEqualTo("");
        Map<String, Object> originalModel = (Map<String, Object>) template.get("model");
        Map<String, Object> originalModelInfo = (Map<String, Object>) originalModel.get("model_info");
        assertThat(originalModelInfo.get("temperature")).isEqualTo(2);
    }
}
