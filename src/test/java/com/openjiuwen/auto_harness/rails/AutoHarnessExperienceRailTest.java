/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.auto_harness.tools.ExperienceSearchTool;
import com.openjiuwen.core.single_agent.AbilityManager;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.rails.CallbackContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code AutoHarnessExperienceRail} and module helper in
 * {@code openjiuwen/auto_harness/rails/experience_rail.py}.
 */
class AutoHarnessExperienceRailTest {

    @Test
    void buildExperienceSectionKeepsBothLanguagesAndPriority() {
        PromptSection section = AutoHarnessExperienceRail.buildExperienceSection("en", "/tmp/exp");

        assertThat(section.getName()).isEqualTo(SectionName.MEMORY);
        assertThat(section.getPriority()).isEqualTo(85);
        assertThat(section.render("cn")).contains("经验库位于 `/tmp/exp`");
        assertThat(section.render("en")).contains("The experience library lives at `/tmp/exp`.");
    }

    @Test
    void initRegistersExperienceToolAndUninitCleansIt() {
        TestAgent agent = new TestAgent();
        AutoHarnessExperienceRail rail = new AutoHarnessExperienceRail(".exp", "en");

        rail.init(agent);

        assertThat(agent.getAbilityManager().get(ExperienceSearchTool.TOOL_NAME)).isPresent();
        assertThat(rail.getOwnedToolNames()).containsExactly(ExperienceSearchTool.TOOL_NAME);

        rail.uninit(agent);

        assertThat(agent.getAbilityManager().get(ExperienceSearchTool.TOOL_NAME)).isEmpty();
        assertThat(rail.getOwnedToolNames()).isEmpty();
    }

    @Test
    void beforeModelCallRefreshesMemorySection() {
        TestAgent agent = new TestAgent();
        AutoHarnessExperienceRail rail = new AutoHarnessExperienceRail(".exp", "en");
        agent.getSystemPromptBuilder().addSection(new PromptSection(
                SectionName.MEMORY,
                Map.of("en", "old"),
                1
        ));
        rail.init(agent);

        rail.beforeModelCall(new CallbackContext(agent, Map.of()));

        PromptSection section = agent.getSystemPromptBuilder().getSection(SectionName.MEMORY).orElseThrow();
        assertThat(section.getPriority()).isEqualTo(85);
        assertThat(section.render("en")).contains("The experience library lives at `.exp`.");

        rail.uninit(agent);
        assertThat(agent.getSystemPromptBuilder().hasSection(SectionName.MEMORY)).isFalse();
    }

    /**
     * Minimal DeepAgent shape exposing Python-style system_prompt_builder and ability_manager.
     *
     * <p>Mirrors Python's dynamic agent object used by
     * {@code openjiuwen/auto_harness/rails/experience_rail.py}.</p>
     */
    private static final class TestAgent extends DeepAgent {
        private final SystemPromptBuilder systemPromptBuilder = new SystemPromptBuilder("en");
        private final AbilityManager abilityManager = new AbilityManager();

        public SystemPromptBuilder getSystemPromptBuilder() {
            return systemPromptBuilder;
        }

        public AbilityManager getAbilityManager() {
            return abilityManager;
        }
    }
}
