/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for LLM Agent template module.
 * <p>
 * Mirrors Python's {@code test_template_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.llm_agent}.
 */
class TestTemplateIntegration {

    @Test
    void templateStructure() {
        Map<String, Object> template = LlmAgentTemplate.create();
        assertThat(template).containsKey("agent_id");
        assertThat(template).containsKey("agent_type");
        assertThat(template).containsKey("name");
        assertThat(template).containsKey("description");
        assertThat(template).containsKey("configs");
        assertThat(template).containsKey("model");
        assertThat(template).containsKey("plugins");
    }

    @Test
    void templateDefaultValues() {
        Map<String, Object> template = LlmAgentTemplate.create();
        assertThat(template.get("agent_id")).isEqualTo("");
        assertThat(template.get("agent_type")).isEqualTo("react");
        assertThat(template.get("edit_mode")).isEqualTo("manual");
    }

    @SuppressWarnings("unchecked")
    @Test
    void templateConfigsStructure() {
        Map<String, Object> template = LlmAgentTemplate.create();
        Map<String, Object> configs = (Map<String, Object>) template.get("configs");
        assertThat(configs).containsKey("system_prompt");
    }

    @SuppressWarnings("unchecked")
    @Test
    void templateConstraintsStructure() {
        Map<String, Object> template = LlmAgentTemplate.create();
        Map<String, Object> constraints = (Map<String, Object>) template.get("constraints");
        assertThat(constraints).containsKey("max_iterations");
        assertThat(constraints).containsKey("reserved_max_chat_rounds");
        assertThat(constraints.get("max_iterations")).isEqualTo(5);
    }

    @SuppressWarnings("unchecked")
    @Test
    void templateMemoryStructure() {
        Map<String, Object> template = LlmAgentTemplate.create();
        Map<String, Object> memory = (Map<String, Object>) template.get("memory");
        assertThat(memory).containsKey("max_tokens");
        assertThat(memory.get("max_tokens")).isEqualTo(1000);
    }

    @SuppressWarnings("unchecked")
    @Test
    void templateModelStructure() {
        Map<String, Object> template = LlmAgentTemplate.create();
        Map<String, Object> model = (Map<String, Object>) template.get("model");
        assertThat(model).containsKey("model_info");
        assertThat(model).containsKey("model_provider");

        Map<String, Object> modelInfo = (Map<String, Object>) model.get("model_info");
        assertThat(modelInfo).containsKey("api_base");
        assertThat(modelInfo).containsKey("api_key");
        assertThat(modelInfo).containsKey("model_name");
        assertThat(modelInfo).containsKey("temperature");
        assertThat(modelInfo).containsKey("top_p");
        assertThat(modelInfo).containsKey("max_tokens");
        assertThat(modelInfo).containsKey("streaming");
    }

    @SuppressWarnings("unchecked")
    @Test
    void templateModelDefaults() {
        Map<String, Object> template = LlmAgentTemplate.create();
        Map<String, Object> modelInfo = (Map<String, Object>) ((Map<String, Object>) template.get("model")).get("model_info");
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
        Map<String, Object> original = LlmAgentTemplate.create();
        Map<String, Object> copy = LlmAgentTemplate.deepCopy(original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy).isEqualTo(original);
    }

    @SuppressWarnings("unchecked")
    @Test
    void templateModificationDoesNotAffectOriginal() {
        Map<String, Object> original = LlmAgentTemplate.create();
        Map<String, Object> copy = LlmAgentTemplate.deepCopy(original);

        copy.put("name", "Test Agent");
        ((Map<String, Object>) ((Map<String, Object>) copy.get("model")).get("model_info")).put("temperature", 0.5);

        assertThat(original.get("name")).isEqualTo("");
        assertThat(((Map<String, Object>) ((Map<String, Object>) original.get("model")).get("model_info")).get("temperature")).isEqualTo(2);
    }
}
