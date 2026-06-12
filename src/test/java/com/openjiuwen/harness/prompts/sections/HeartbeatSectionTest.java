/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeartbeatSectionTest {

    @Test
    void defaultBuildUsesChineseHeartbeatPrompt() {
        PromptSection section = HeartbeatSection.build();

        assertThat(section.getName()).isEqualTo(SectionName.HEARTBEAT);
        assertThat(section.getPriority()).isEqualTo(80);
        assertThat(section.getContent()).containsOnlyKeys("cn");
        assertThat(section.render("cn")).contains("心跳检测", "HEARTBEAT_OK", "记录不等于执行");
    }

    @Test
    void englishBuildUsesEnglishPrompt() {
        PromptSection section = HeartbeatSection.build("en");

        assertThat(section.getContent()).containsOnlyKeys("en");
        assertThat(section.render("en")).contains("## Heartbeat", "HEARTBEAT_OK", "recording is not execution");
    }

    @Test
    void unknownLanguageFallsBackToChinese() {
        PromptSection section = HeartbeatSection.buildHeartbeatSection("fr");

        assertThat(section.getContent()).containsOnlyKeys("cn");
        assertThat(section.render("fr")).contains("心跳检测");
    }
}
