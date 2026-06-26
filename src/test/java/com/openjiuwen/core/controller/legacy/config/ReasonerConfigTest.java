/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.config;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests legacy reasoner configuration defaults.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/controller/legacy/config/reasoner_config.py}.</p>
 */
class ReasonerConfigTest {

    @Test
    void intentDetectionConfigPreservesPythonDefaults() {
        IntentDetectionConfig config = new IntentDetectionConfig();

        assertThat(config.getCategoryInfo()).isEmpty();
        assertThat(config.getCategoryList()).isEmpty();
        assertThat(config.getUserPrompt()).isEqualTo(IntentDetectionConfig.DEFAULT_USER_PROMPT);
        assertThat(config.getChatHistoryMaxTurn()).isEqualTo(100);
        assertThat(config.getDefaultClass()).isEqualTo("分类0");
        assertThat(config.isEnableHistory()).isFalse();
        assertThat(config.isEnableInput()).isTrue();
        assertThat(config.getExampleContent()).isEmpty();
    }

    @Test
    void defaultTemplateContainsSystemAndUserMessages() {
        List<BaseMessage> messages = new IntentDetectionConfig()
                .getIntentDetectionTemplate()
                .toMessages();

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(0).getContent()).isEqualTo(IntentDetectionConfig.DEFAULT_SYSTEM_PROMPT);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getContent()).isEqualTo(IntentDetectionConfig.DEFAULT_USER_PROMPT);
    }

    @Test
    void reasonerConfigComposesDefaultSubConfigs() {
        ReasonerConfig config = new ReasonerConfig();

        assertThat(config.getIntentDetection()).isNotNull();
        assertThat(config.getPlanner()).isNotNull();
        assertThat(config.getProactiveIdentifier()).isNotNull();
        assertThat(config.getReflector()).isNotNull();
        assertThat(config.isEnableMetrics()).isTrue();
        assertThat(config.isEnableLogging()).isTrue();
        assertThat(config.getMetadata()).isEmpty();
    }
}
