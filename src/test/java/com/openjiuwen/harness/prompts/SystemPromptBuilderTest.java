/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptBuilderTest {

    @Test
    void noneModeBuildsOnlyIdentitySection() {
        SystemPromptBuilder builder = new SystemPromptBuilder("en", PromptMode.NONE);
        builder.addSection(new PromptSection(SectionName.TOOLS, Map.of("en", "tools"), 20));
        builder.addSection(new PromptSection(SectionName.IDENTITY, Map.of("en", "identity"), 10));

        assertThat(builder.build()).isEqualTo("identity");
    }

    @Test
    void minimalModeFiltersToWhitelistedSections() {
        SystemPromptBuilder builder = new SystemPromptBuilder("en", PromptMode.MINIMAL);
        builder.addSection(new PromptSection(SectionName.IDENTITY, Map.of("en", "identity"), 10));
        builder.addSection(new PromptSection(SectionName.TOOLS, Map.of("en", "tools"), 20));
        builder.addSection(new PromptSection(SectionName.TODO, Map.of("en", "todo"), 30));

        assertThat(builder.build()).isEqualTo("identity\n\ntools");
    }

    @Test
    void buildReportCarriesHarnessModeIntoPromptReport() {
        SystemPromptBuilder builder = new SystemPromptBuilder("en", PromptMode.MINIMAL);
        builder.addSection(new PromptSection(SectionName.IDENTITY, Map.of("en", "identity"), 10));

        PromptReport report = builder.buildReport();

        assertThat(report.getMode()).isEqualTo("minimal");
        assertThat(report.getSectionCount()).isEqualTo(1);
        assertThat(report.getSections()).extracting(SectionInfo::getName).containsExactly(SectionName.IDENTITY);
    }
}
