/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;

import org.junit.jupiter.api.Test;

/**
 * Tests for the stable construction contract of {@link ToolInterruptEntry}.
 */
class ToolInterruptEntryTest {
    @Test
    void noArgsConstructionSupportsBeanAssignment() {
        ToolCall toolCall = ToolCall.builder().id("call-1").name("search").build();
        InterruptRequest request = InterruptRequest.builder().build();
        ToolInterruptEntry entry = new ToolInterruptEntry();

        entry.setToolCall(toolCall);
        entry.setRequest(request);

        assertThat(entry.getToolCall()).isSameAs(toolCall);
        assertThat(entry.getRequest()).isSameAs(request);
        assertThat(entry.getSettledDecisions()).isEmpty();
    }

    @Test
    void builderKeepsSettledDecisionDefault() {
        ToolInterruptEntry entry = ToolInterruptEntry.builder()
                .toolCall(ToolCall.builder().id("call-2").name("search").build())
                .request(InterruptRequest.builder().build())
                .build();

        assertThat(entry.getSettledDecisions()).isNotNull().isEmpty();
    }

    @Test
    void onlyNoArgsConstructorIsPublic() {
        assertThat(ToolInterruptEntry.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterCount()).isZero());
    }
}
