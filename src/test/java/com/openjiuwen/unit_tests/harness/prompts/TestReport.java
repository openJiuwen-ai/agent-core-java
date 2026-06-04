/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.prompts;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.DeepAgentPromptBuilder;
import com.openjiuwen.harness.prompts.PromptReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_report.py}.
 */
class TestReport {

    @Test
    void testFromBuilderBasic() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder("cn", DeepAgentPromptBuilder.PromptMode.FULL);
        builder.addSection(new PromptSection("identity", Map.of("cn", "你好世界", "en", "Hello"), 10));

        PromptReport report = PromptReport.fromBuilder(builder);

        assertThat(report.getSectionCount()).isEqualTo(1);
        assertThat(report.getTotalChars()).isEqualTo(4);
        assertThat(report.getMode()).isEqualTo("full");
        assertThat(report.getLanguage()).isEqualTo("cn");
        assertThat(report.getEstimatedTokens()).isPositive();
    }

    @Test
    void testFromBuilderEmpty() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder("en", DeepAgentPromptBuilder.PromptMode.FULL);

        PromptReport report = PromptReport.fromBuilder(builder);

        assertThat(report.getSectionCount()).isZero();
        assertThat(report.getTotalChars()).isZero();
        assertThat(report.getEstimatedTokens()).isZero();
    }

    @Test
    void testFromBuilderMultipleSections() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder("en", DeepAgentPromptBuilder.PromptMode.FULL);
        builder.addSection(new PromptSection("a", Map.of("cn", "中文A", "en", "EnglishA"), 10));
        builder.addSection(new PromptSection("b", Map.of("cn", "中文B", "en", "EnglishBB"), 20));

        PromptReport report = PromptReport.fromBuilder(builder);

        assertThat(report.getSectionCount()).isEqualTo(2);
        assertThat(report.getTotalChars()).isEqualTo("EnglishA".length() + "EnglishBB".length());
    }

    @Test
    void testToDict() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder("cn", DeepAgentPromptBuilder.PromptMode.FULL);
        builder.addSection(new PromptSection("id", Map.of("cn", "身份", "en", "identity"), 10));

        Map<String, Object> data = PromptReport.fromBuilder(builder).toDict();

        assertThat(data.get("section_count")).isEqualTo(1);
        assertThat(data.get("mode")).isEqualTo("full");
        assertThat(data.get("language")).isEqualTo("cn");
        assertThat((List<?>) data.get("sections")).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) data.get("sections")).getFirst()).get("name")).isEqualTo("id");
    }

    @Test
    void testSummary() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder("cn", DeepAgentPromptBuilder.PromptMode.MINIMAL);
        builder.addSection(new PromptSection("identity", Map.of("cn", "身份", "en", "id"), 10));

        String summary = PromptReport.fromBuilder(builder).summary();

        assertThat(summary).contains("mode=minimal");
        assertThat(summary).contains("lang=cn");
        assertThat(summary).contains("sections=1");
    }

    @Test
    void testSectionsSortedByPriority() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder("cn", DeepAgentPromptBuilder.PromptMode.FULL);
        builder.addSection(new PromptSection("b", Map.of("cn", "B", "en", "B"), 20));
        builder.addSection(new PromptSection("a", Map.of("cn", "A", "en", "A"), 10));

        PromptReport report = PromptReport.fromBuilder(builder);

        assertThat(report.getSections().get(0).getName()).isEqualTo("a");
        assertThat(report.getSections().get(1).getName()).isEqualTo("b");
    }

    @Test
    void testMinimalModeKeepsToolsSection() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder("cn", DeepAgentPromptBuilder.PromptMode.MINIMAL);
        builder.addSection(new PromptSection("identity", Map.of("cn", "身份", "en", "id"), 10));
        builder.addSection(new PromptSection("tools", Map.of("cn", "工具\n## task_tool 使用原则", "en", "tools"), 20));

        String rendered = builder.build();

        assertThat(rendered).contains("身份");
        assertThat(rendered).contains("## task_tool 使用原则");
    }

    @Test
    void testBuildReportReturnsPromptReport() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder("cn", DeepAgentPromptBuilder.PromptMode.FULL);
        builder.addSection(new PromptSection("identity", Map.of("cn", "身份", "en", "id"), 10));

        PromptReport report = builder.buildReport();

        assertThat(report).isInstanceOf(PromptReport.class);
        assertThat(report.getSectionCount()).isEqualTo(1);
    }

    @Test
    void testBuildReportReflectsDynamicChanges() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder("cn", DeepAgentPromptBuilder.PromptMode.FULL);

        PromptReport first = builder.buildReport();
        builder.addSection(new PromptSection("x", Map.of("cn", "X", "en", "X"), 10));
        PromptReport second = builder.buildReport();

        assertThat(first.getSectionCount()).isZero();
        assertThat(second.getSectionCount()).isEqualTo(1);
    }
}
