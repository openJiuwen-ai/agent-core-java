package com.openjiuwen.core.controller.legacy.constants;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentDetectionConstantsTest {

    @Test
    void constantsAndRoleMapMatchPythonModule() {
        assertThat(IntentDetectionConstants.USER_PROMPT).isEqualTo("user_prompt");
        assertThat(IntentDetectionConstants.CATEGORY_LIST).isEqualTo("category_list");
        assertThat(IntentDetectionConstants.DEFAULT_CLASS).isEqualTo("default_class");
        assertThat(IntentDetectionConstants.ENABLE_HISTORY).isEqualTo("enable_history");
        assertThat(IntentDetectionConstants.ENABLE_INPUT).isEqualTo("enable_input");
        assertThat(IntentDetectionConstants.EXAMPLE_CONTENT).isEqualTo("example_content");
        assertThat(IntentDetectionConstants.CHAT_HISTORY_MAX_TURN).isEqualTo("chat_history_max_turn");
        assertThat(IntentDetectionConstants.CHAT_HISTORY).isEqualTo("chat_history");
        assertThat(IntentDetectionConstants.INPUT).isEqualTo("input");
        assertThat(IntentDetectionConstants.ROLE_MAP)
                .containsEntry("user", "用户")
                .containsEntry("assistant", "助手")
                .containsEntry("system", "系统");
    }
}
