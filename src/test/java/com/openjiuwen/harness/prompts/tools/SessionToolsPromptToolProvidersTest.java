/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SessionToolsPromptToolProvidersTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void sessionsListMetadataProviderPreservesEmptySchema() {
        ToolMetadataProvider provider = new SessionToolsPromptToolProviders.SessionsListMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("sessions_list");
        assertThat(provider.getDescription("cn")).isEqualTo("查看当前所有后台异步子任务(包括运行中、已完成、失败、已取消)及其元数据");
        assertThat(castMap(schema.get("properties"))).isEmpty();
        assertThat(castList(schema.get("required"))).isEmpty();
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void sessionsSpawnMetadataProviderPreservesPromptContract() {
        ToolMetadataProvider provider = new SessionToolsPromptToolProviders.SessionsSpawnMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");
        Map<String, Object> properties = castMap(schema.get("properties"));

        assertThat(provider.getName()).isEqualTo("sessions_spawn");
        assertThat(provider.getDescription("cn")).contains("{available_agents}");
        assertThat(provider.getDescription("en")).contains("Create async background subagent task");
        assertThat(properties.keySet()).containsExactly("subagent_type", "task_description");
        assertThat(castList(schema.get("required"))).containsExactly("subagent_type", "task_description");
        assertThat(castMap(properties.get("task_description")))
                .containsEntry("type", "string")
                .containsEntry("description", "Task description");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void sessionsCancelMetadataProviderPreservesTaskIdSchema() {
        ToolMetadataProvider provider = new SessionToolsPromptToolProviders.SessionsCancelMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");
        Map<String, Object> properties = castMap(schema.get("properties"));

        assertThat(provider.getName()).isEqualTo("sessions_cancel");
        assertThat(provider.getDescription("en"))
                .isEqualTo("Cancel background async task. This operation blocks synchronously until cancellation completes.");
        assertThat(properties.keySet()).containsExactly("task_id");
        assertThat(castList(schema.get("required"))).containsExactly("task_id");
        assertThat(castMap(properties.get("task_id")))
                .containsEntry("type", "string")
                .containsEntry("description", "Task ID to cancel (obtained from sessions_list)");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
