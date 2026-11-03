/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TodoPromptToolProvidersTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void todoCreateMetadataProviderPreservesTaskSchema() {
        ToolMetadataProvider provider = new TodoPromptToolProviders.TodoCreateMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");
        Map<String, Object> tasks = castMap(castMap(schema.get("properties")).get("tasks"));
        Map<String, Object> items = castMap(tasks.get("items"));

        assertThat(provider.getName()).isEqualTo("todo_create");
        assertThat(provider.getDescription("cn")).contains("第一个任务自动设为 in_progress");
        assertThat(castList(schema.get("required"))).containsExactly("tasks");
        assertThat(castMap(items.get("properties")).keySet())
                .containsExactly("id", "content", "activeForm", "description", "selected_model_id");
        assertThat(castList(items.get("required")))
                .containsExactly("id", "content", "activeForm", "description");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void todoListMetadataProviderPreservesEmptySchema() {
        ToolMetadataProvider provider = new TodoPromptToolProviders.TodoListMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");

        assertThat(provider.getName()).isEqualTo("todo_list");
        assertThat(provider.getDescription("en")).contains("Retrieve and display all todo items");
        assertThat(castMap(schema.get("properties"))).isEmpty();
        assertThat(castList(schema.get("required"))).isEmpty();
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void todoModifyMetadataProviderPreservesNestedSchemas() {
        ToolMetadataProvider provider = new TodoPromptToolProviders.TodoModifyMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");
        Map<String, Object> properties = castMap(schema.get("properties"));
        Map<String, Object> todoData = castMap(properties.get("todo_data"));

        assertThat(provider.getName()).isEqualTo("todo_modify");
        assertThat(provider.getDescription("en")).contains("Supports batch operations");
        assertThat(castList(schema.get("required"))).containsExactly("action");
        assertThat(castMap(properties.get("action")).get("enum"))
                .isEqualTo(List.of("update", "delete", "cancel", "append", "insert_after", "insert_before"));
        assertThat(castList(todoData.get("required"))).containsExactly("target_id", "items");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void todoGetMetadataProviderPreservesIdRequirement() {
        ToolMetadataProvider provider = new TodoPromptToolProviders.TodoGetMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("todo_get");
        assertThat(provider.getDescription("cn")).contains("根据任务 ID 获取单个任务的完整详情");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactly("id");
        assertThat(castList(schema.get("required"))).containsExactly("id");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
