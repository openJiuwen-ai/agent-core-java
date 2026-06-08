/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
}
