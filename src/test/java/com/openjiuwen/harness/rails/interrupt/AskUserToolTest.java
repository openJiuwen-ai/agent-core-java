
package com.openjiuwen.harness.rails.interrupt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Map;

class AskUserToolTest {
    @Test
    void invokeReturnsResumedResponseFields() throws Exception {
        AskUserTool tool = new AskUserTool();

        assertThat(tool.getCard().getId()).isEqualTo("ask_user");
        assertThat(tool.invoke(Map.of("response", "1000"), Map.of())).isEqualTo("1000");
        assertThat(tool.invoke(Map.of("feedback", "确认"), Map.of())).isEqualTo("确认");
        assertThat(tool.invoke(Map.of("answer", "ok"), Map.of())).isEqualTo("ok");
    }
}
