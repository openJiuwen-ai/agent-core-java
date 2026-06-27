/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillToolBindingTest {

    @Test
    void builderFiltersNullToolsAndKeepsToolOrder() {
        Tool first = tool("first");
        Tool second = tool("second");

        SkillToolBinding binding = SkillToolBinding.builder()
                .skillName("demo-clock")
                .tools(Arrays.asList(first, null, second))
                .build();

        assertThat(binding.getSkillName()).isEqualTo("demo-clock");
        assertThat(binding.getTools()).containsExactly(first, second);
    }

    @Test
    void blankSkillNameFails() {
        assertThatThrownBy(() -> SkillToolBinding.builder()
                .skillName(" ")
                .tools(List.of(tool("echo")))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skillName must not be blank");
    }

    @Test
    void blankToolNameFails() {
        assertThatThrownBy(() -> SkillToolBinding.builder()
                .skillName("demo-clock")
                .tools(List.of(tool(" ")))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Skill tool name must not be blank");
    }

    @Test
    void duplicateToolNameInOneBindingFails() {
        assertThatThrownBy(() -> SkillToolBinding.builder()
                .skillName("demo-clock")
                .tools(List.of(tool("echo"), tool("echo")))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate skill tool name")
                .hasMessageContaining("echo")
                .hasMessageContaining("demo-clock");
    }

    private static Tool tool(String name) {
        return new Tool(ToolCard.builder()
                .id(name == null || name.isBlank() ? "blank-id" : name)
                .name(name)
                .description("tool " + name)
                .inputParams(Map.of("type", "object"))
                .build()) {
        };
    }
}
