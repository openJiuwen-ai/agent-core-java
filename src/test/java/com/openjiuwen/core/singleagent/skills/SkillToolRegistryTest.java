/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillToolRegistryTest {

    @Test
    void repeatedRegistrationForSameSkillAppendsNewToolNames() {
        Tool first = tool("first");
        Tool second = tool("second");
        SkillToolRegistry registry = new SkillToolRegistry();

        registry.register(binding("demo-clock", first));
        registry.register(binding("demo-clock", second));

        assertThat(registry.hasSkill("demo-clock")).isTrue();
        assertThat(registry.listToolsForActiveSkills(List.of("demo-clock"))).containsExactly(first, second);
        assertThat(registry.findToolForActiveSkills("second", List.of("demo-clock"))).containsSame(second);
    }

    @Test
    void repeatedRegistrationForSameSkillRejectsDuplicateToolName() {
        SkillToolRegistry registry = new SkillToolRegistry();
        registry.register(binding("demo-clock", tool("echo")));

        assertThatThrownBy(() -> registry.register(binding("demo-clock", tool("echo"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate skill tool name")
                .hasMessageContaining("echo")
                .hasMessageContaining("demo-clock");
    }

    @Test
    void registerAllDoesNotApplyPartialBindingsWhenOneBindingFails() {
        Tool existing = tool("existing");
        SkillToolRegistry registry = new SkillToolRegistry();
        registry.register(binding("demo-clock", existing));

        assertThatThrownBy(() -> registry.registerAll(List.of(
                binding("new-skill", tool("new")),
                binding("demo-clock", tool("existing"))
        ))).isInstanceOf(IllegalArgumentException.class);

        assertThat(registry.hasSkill("new-skill")).isFalse();
        assertThat(registry.listToolsForActiveSkills(List.of("demo-clock"))).containsExactly(existing);
    }

    @Test
    void duplicateToolNamesInDifferentSkillsAreKeptUntilEffectiveMerge() {
        Tool first = tool("echo");
        Tool second = tool("echo");
        SkillToolRegistry registry = new SkillToolRegistry();

        registry.register(binding("skill-a", first));
        registry.register(binding("skill-b", second));

        assertThat(registry.listToolsForActiveSkills(List.of("skill-a", "skill-b")))
                .containsExactly(first, second);
        assertThat(registry.findToolForActiveSkills("echo", List.of("skill-a", "skill-b")))
                .containsSame(first);
    }

    @Test
    void inactiveSkillsDoNotExposeTools() {
        SkillToolRegistry registry = new SkillToolRegistry();
        registry.register(binding("demo-clock", tool("echo")));

        assertThat(registry.listToolsForActiveSkills(List.of("other"))).isEmpty();
        assertThat(registry.findToolForActiveSkills("echo", List.of("other"))).isEmpty();
    }

    private static SkillToolBinding binding(String skillName, Tool... tools) {
        return SkillToolBinding.builder()
                .skillName(skillName)
                .tools(List.of(tools))
                .build();
    }

    private static Tool tool(String name) {
        return new Tool(ToolCard.builder()
                .id(name)
                .name(name)
                .description("tool " + name)
                .inputParams(Map.of("type", "object"))
                .build()) {
        };
    }
}
