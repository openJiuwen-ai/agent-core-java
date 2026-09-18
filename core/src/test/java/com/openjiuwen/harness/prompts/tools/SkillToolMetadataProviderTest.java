/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SkillToolMetadataProviderTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void skillToolMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new SkillToolMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("skill_tool");
        assertThat(provider.getDescription("cn")).isEqualTo("使用此工具查看特定技能的内容");
        assertThat(provider.getDescription("en")).isEqualTo("Use this tool to view the skill contents of a certain skill");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactly("skill_name", "relative_file_path");
        assertThat(castList(schema.get("required"))).containsExactly("skill_name");
        assertThat(castMap(castMap(schema.get("properties")).get("relative_file_path")))
                .containsEntry("type", "string")
                .containsEntry("description", "可选。查看技能目录中指定路径（relative_file_path）下的特定文件。留空则查看主 SKILL.md 文件。");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
