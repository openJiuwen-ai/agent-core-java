/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ListSkillMetadataProviderTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @Test
    void listSkillMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new ListSkillMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("list_skill");
        assertThat(provider.getDescription("cn")).isEqualTo("列出可用技能或为当前任务选择相关技能。");
        assertThat(provider.getDescription("en")).isEqualTo("List available skills or select relevant skills for the current task.");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactly("query");
        assertThat((java.util.List<?>) schema.get("required")).isEmpty();
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
