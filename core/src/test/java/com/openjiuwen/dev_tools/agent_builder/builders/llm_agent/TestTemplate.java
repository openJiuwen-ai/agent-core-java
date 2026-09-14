/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test LlmAgentBuilder template functionality.
 * <p>
 * Mirrors Python's {@code test_template.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/llm_agent/test_template.py}.
 */
class TestTemplate {

    @Test
    void testTemplateIsDict() {
        assertThat(LlmAgentTemplate.create()).isInstanceOf(Map.class);
    }

    @Test
    void testTemplateHasRequiredKeys() {
        Map<String, Object> template = LlmAgentTemplate.create();

        assertThat(template)
                .containsKeys(
                        "agent_id",
                        "agent_type",
                        "name",
                        "description",
                        "configs",
                        "opening_remarks",
                        "plugins",
                        "knowledge",
                        "workflows",
                        "create_time",
                        "update_time"
                );
    }

    @SuppressWarnings("unchecked")
    @Test
    void testTemplateHasMemoryConfig() {
        Map<String, Object> template = LlmAgentTemplate.create();

        assertThat(template).containsKey("memory");
        assertThat((Map<String, Object>) template.get("memory")).containsKey("max_tokens");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testTemplateHasModelConfig() {
        Map<String, Object> template = LlmAgentTemplate.create();

        assertThat(template).containsKey("model");
        assertThat((Map<String, Object>) template.get("model")).containsKey("model_info");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testTemplateHasConstraints() {
        Map<String, Object> template = LlmAgentTemplate.create();

        assertThat(template).containsKey("constraints");
        assertThat((Map<String, Object>) template.get("constraints")).containsKey("max_iterations");
    }

    @Test
    void testTemplateCreateTimeIsNone() {
        Map<String, Object> template = LlmAgentTemplate.create();

        assertThat(template.get("create_time")).isNull();
        assertThat(template.get("update_time")).isNull();
    }

    @Test
    void testTemplateIsCopiedNotReferenced() {
        Map<String, Object> template = LlmAgentTemplate.create();
        Map<String, Object> copy = LlmAgentTemplate.deepCopy(template);

        copy.put("name", "Modified");

        assertThat(template.get("name")).isEqualTo("");
        assertThat(copy.get("name")).isEqualTo("Modified");
        assertThat((List<?>) template.get("plugins")).isEmpty();
    }
}
