
package com.openjiuwen.core.singleagent.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

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

    @Test
    void promptModeNoneKeepsIdentityOnly() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn", "none");
        builder.addSection(new PromptSection("identity", Map.of("cn", "identity"), 10));
        builder.addSection(new PromptSection("tools", Map.of("cn", "tools"), 20));

        assertThat(builder.getLanguage()).isEqualTo("cn");
        assertThat(builder.getMode()).isEqualTo("none");
        assertThat(builder.build()).isEqualTo("identity");
    }

    @Test
    void promptModeMinimalKeepsCoreSectionsOnly() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn", "minimal");
        builder.addSection(new PromptSection("identity", Map.of("cn", "identity"), 10));
        builder.addSection(new PromptSection("tools", Map.of("cn", "tools"), 20));
        builder.addSection(new PromptSection("project_memory", Map.of("cn", "memory file"), 30));

        assertThat(builder.getMode()).isEqualTo("minimal");
        assertThat(builder.build()).isEqualTo("identity\n\ntools");
    }

    @Test
    void invalidPromptModeFallsBackToFull() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn", "compact");
        builder.addSection(new PromptSection("identity", Map.of("cn", "identity"), 10));
        builder.addSection(new PromptSection("project_memory", Map.of("cn", "memory file"), 20));

        assertThat(builder.getMode()).isEqualTo("full");
        assertThat(builder.build()).isEqualTo("identity\n\nmemory file");
    }

    /**
     * 模拟 sections map 中混入 null 值（可能由并发修改或反射注入导致），
     * 验证 build() 不会抛出 NPE，而是跳过 null section 继续构建。
     */
    @Test
    @SuppressWarnings("unchecked")
    void buildSkipsNullSectionWithoutNpe() throws Exception {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn", "full");
        builder.addSection(new PromptSection("identity", Map.of("cn", "identity"), 10));
        builder.addSection(new PromptSection("tools", Map.of("cn", "tools"), 20));

        // 通过反射注入 null 值到 sections map
        Field sectionsField = SystemPromptBuilder.class.getDeclaredField("sections");
        sectionsField.setAccessible(true);
        Map<String, PromptSection> sections =
            (Map<String, PromptSection>) sectionsField.get(builder);
        // 使用新的 LinkedHashMap 模拟并发场景下可能出现的 null 值
        Map<String, PromptSection> tampered = new LinkedHashMap<String, PromptSection>();
        tampered.put("identity", new PromptSection("identity", Map.of("cn", "identity"), 10));
        tampered.put("broken", null);  // 注入 null
        tampered.put("tools", new PromptSection("tools", Map.of("cn", "tools"), 20));
        sectionsField.set(builder, tampered);

        // build() 应跳过 null section，不抛出 NPE
        String result = builder.build();
        assertThat(result).isEqualTo("identity\n\ntools");
    }

    /**
     * 模拟 minimal 模式下 sections map 中混入 null 值，
     * 验证 getSectionsForBuild() 不会抛出 NPE。
     */
    @Test
    @SuppressWarnings("unchecked")
    void minimalModeSkipsNullSectionWithoutNpe() throws Exception {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn", "minimal");
        builder.addSection(new PromptSection("identity", Map.of("cn", "identity"), 10));

        // 通过反射注入 null 值
        Field sectionsField = SystemPromptBuilder.class.getDeclaredField("sections");
        sectionsField.setAccessible(true);
        Map<String, PromptSection> tampered = new LinkedHashMap<String, PromptSection>();
        tampered.put("identity", new PromptSection("identity", Map.of("cn", "identity"), 10));
        tampered.put("broken", null);
        sectionsField.set(builder, tampered);

        // minimal 模式下应跳过 null，仅输出 identity
        String result = builder.build();
        assertThat(result).isEqualTo("identity");
    }
}
