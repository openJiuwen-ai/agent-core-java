/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestPromptReport} and {@code TestBuildReport} in
 * {@code tests/unit_tests/harness/prompts/test_report.py}.
 */
class PromptReportTest {

    @Test
    void reportCapturesSortedSectionsAndTokenEstimate() {
        SystemPromptBuilder builder = new SystemPromptBuilder("en");
        builder.addSection(new PromptSection("later", Map.of("en", "second"), 20));
        builder.addSection(new PromptSection("first", Map.of("en", "first"), 10));

        PromptReport report = PromptReport.fromBuilder(builder);

        assertThat(report.getMode()).isEqualTo("full");
        assertThat(report.getLanguage()).isEqualTo("en");
        assertThat(report.getSectionCount()).isEqualTo(2);
        assertThat(report.getSections())
                .extracting(SectionInfo::getName)
                .containsExactly("first", "later");
        assertThat(report.getEstimatedTokens()).isEqualTo(PromptReport.estimateTokens(report.getTotalChars(), "en"));
    }

    @Test
    void toDictMatchesPythonStyleKeys() {
        PromptReport report = new PromptReport();
        report.setTotalChars(10);
        report.setEstimatedTokens(4);
        report.setSectionCount(1);
        report.setSections(List.of(new SectionInfo("identity", 1, 10)));

        assertThat(report.toDict()).containsEntry("total_chars", 10);
        assertThat(report.toDict()).containsEntry("estimated_tokens", 4);
        assertThat(report.toDict()).containsEntry("section_count", 1);
        assertThat(report.summary()).contains("mode=full").contains("sections=1");
    }

    @Test
    void fromBuilderBasicCountsChineseCharacters() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        builder.addSection(new PromptSection("identity", Map.of("cn", "你好世界", "en", "Hello"), 10));

        PromptReport report = PromptReport.fromBuilder(builder);

        assertThat(report.getSectionCount()).isEqualTo(1);
        assertThat(report.getTotalChars()).isEqualTo(4);
        assertThat(report.getMode()).isEqualTo("full");
        assertThat(report.getLanguage()).isEqualTo("cn");
        assertThat(report.getEstimatedTokens()).isPositive();
    }

    @Test
    void fromBuilderEmptyHasZeroCounts() {
        SystemPromptBuilder builder = new SystemPromptBuilder("en");

        PromptReport report = PromptReport.fromBuilder(builder);

        assertThat(report.getSectionCount()).isZero();
        assertThat(report.getTotalChars()).isZero();
        assertThat(report.getEstimatedTokens()).isZero();
    }

    @Test
    void fromBuilderMultipleSectionsSumsSelectedLanguageCharacters() {
        SystemPromptBuilder builder = new SystemPromptBuilder("en");
        builder.addSection(new PromptSection("a", Map.of("cn", "中文A", "en", "EnglishA"), 10));
        builder.addSection(new PromptSection("b", Map.of("cn", "中文B", "en", "EnglishBB"), 20));

        PromptReport report = PromptReport.fromBuilder(builder);

        assertThat(report.getSectionCount()).isEqualTo(2);
        assertThat(report.getTotalChars()).isEqualTo("EnglishA".length() + "EnglishBB".length());
    }

    @Test
    void toDictSerializesPythonStyleShape() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        builder.addSection(new PromptSection("id", Map.of("cn", "身份", "en", "identity"), 10));

        Map<String, Object> serialized = PromptReport.fromBuilder(builder).toDict();

        assertThat(serialized).containsEntry("section_count", 1);
        assertThat(serialized).containsEntry("mode", "full");
        assertThat(serialized).containsEntry("language", "cn");
        assertThat(sections(serialized)).hasSize(1);
        assertThat(sections(serialized).getFirst()).containsEntry("name", "id");
    }

    @Test
    void summaryIncludesModeLanguageAndSectionCount() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn", PromptMode.MINIMAL);
        builder.addSection(new PromptSection("identity", Map.of("cn", "身份", "en", "id"), 10));

        String summary = PromptReport.fromBuilder(builder).summary();

        assertThat(summary).contains("mode=minimal");
        assertThat(summary).contains("lang=cn");
        assertThat(summary).contains("sections=1");
    }

    @Test
    void sectionsAreSortedByPriority() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        builder.addSection(new PromptSection("b", Map.of("cn", "B", "en", "B"), 20));
        builder.addSection(new PromptSection("a", Map.of("cn", "A", "en", "A"), 10));

        PromptReport report = PromptReport.fromBuilder(builder);

        assertThat(report.getSections())
                .extracting(SectionInfo::getName)
                .containsExactly("a", "b");
    }

    @Test
    void minimalModeKeepsToolsSection() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn", PromptMode.MINIMAL);
        builder.addSection(new PromptSection("identity", Map.of("cn", "身份", "en", "id"), 10));
        builder.addSection(new PromptSection("tools", Map.of("cn", "工具\n## task_tool 使用原则", "en", "tools"), 20));

        String rendered = builder.build();

        assertThat(rendered).contains("身份");
        assertThat(rendered).contains("## task_tool 使用原则");
    }

    @Test
    void buildReportReturnsPromptReport() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        builder.addSection(new PromptSection("identity", Map.of("cn", "身份", "en", "id"), 10));

        PromptReport report = builder.buildReport();

        assertThat(report).isInstanceOf(PromptReport.class);
        assertThat(report.getSectionCount()).isEqualTo(1);
    }

    @Test
    void buildReportReflectsDynamicChanges() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");

        PromptReport first = builder.buildReport();
        builder.addSection(new PromptSection("x", Map.of("cn", "X", "en", "X"), 10));
        PromptReport second = builder.buildReport();

        assertThat(first.getSectionCount()).isZero();
        assertThat(second.getSectionCount()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sections(Map<String, Object> report) {
        return (List<Map<String, Object>>) report.get("sections");
    }
}
