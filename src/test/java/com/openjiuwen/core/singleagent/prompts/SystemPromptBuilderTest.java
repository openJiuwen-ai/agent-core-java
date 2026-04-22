package com.openjiuwen.core.singleagent.prompts;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptBuilderTest {

    @Test
    void buildSortsSectionsByPriorityAscending() {
        SystemPromptBuilder builder = new SystemPromptBuilder();
        builder.addSection(new PromptSection("high", Map.of("cn", "third"), 30));
        builder.addSection(new PromptSection("low", Map.of("cn", "first"), 10));
        builder.addSection(new PromptSection("mid", Map.of("cn", "second"), 20));

        assertThat(builder.build()).isEqualTo("first\n\nsecond\n\nthird");
    }

    @Test
    void addSectionWithSameNameOverwritesPreviousEntry() {
        SystemPromptBuilder builder = new SystemPromptBuilder();
        builder.addSection(new PromptSection("rules", Map.of("cn", "old"), 10));
        builder.addSection(new PromptSection("rules", Map.of("cn", "new"), 20));

        assertThat(builder.getAllSections()).hasSize(1);
        assertThat(builder.getSection("rules").render("cn")).isEqualTo("new");
        assertThat(builder.getSection("rules").getPriority()).isEqualTo(20);
    }

    @Test
    void renderFallsBackToDefaultLanguage() {
        PromptSection section = new PromptSection("rules", Map.of("cn", "中文"), 10);

        assertThat(section.render("en")).isEqualTo("中文");
        assertThat(section.charCount("en")).isEqualTo(2);
    }

    @Test
    void removeSectionDeletesItFromBuilder() {
        SystemPromptBuilder builder = new SystemPromptBuilder();
        builder.addSection(new PromptSection("rules", Map.of("cn", "text"), 10));

        builder.removeSection("rules");

        assertThat(builder.hasSection("rules")).isFalse();
        assertThat(builder.build()).isEmpty();
    }
}
