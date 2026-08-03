/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TaskToolPromptToolProvidersTest {

    @SuppressWarnings("unchecked")
    @Test
    void taskMetadataProviderPreservesDescriptionAndSchema() {
        ToolMetadataProvider provider = new TaskToolPromptToolProviders.TaskMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        assertThat(provider.getName()).isEqualTo("task_tool");
        assertThat(provider.getDescription("cn")).contains("{available_agents}");
        assertThat(provider.getDescription("en")).contains("Launch a new subagent");
        assertThat((List<String>) schema.get("required")).containsExactly("subagent_type", "task_description");
        assertThat(properties.keySet()).containsExactly("subagent_type", "task_description");
        assertThat((Map<String, Object>) properties.get("task_description"))
                .containsEntry("type", "string")
                .containsEntry("description", "Task description");
    }

    @Test
    void validatePassesForTaskMetadataProvider() {
        ToolMetadataProvider provider = new TaskToolPromptToolProviders.TaskMetadataProvider();
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
