/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Map;

/**
 * Tests for the stable bean-style construction contract of {@link ToolInterruptEntry}.
 */
class ToolInterruptEntryConstructionTest {
    @Test
    void exposesOnlyNoArgConstructionAndBeanAccessors() {
        Constructor<?>[] constructors = ToolInterruptEntry.class.getConstructors();
        ToolCall toolCall = ToolCall.builder().id("call-1").name("tool").arguments("{}").build();
        InterruptRequest request = new InterruptRequest();
        ToolInterruptEntry entry = new ToolInterruptEntry();

        entry.setToolCall(toolCall);
        entry.setInterruptRequests(Map.of("request-1", request));
        entry.setSubAgent(true);

        assertThat(constructors).singleElement().satisfies(constructor ->
                assertThat(constructor.getParameterCount()).isZero());
        assertThat(entry.getToolCall()).isSameAs(toolCall);
        assertThat(entry.getInterruptRequests()).containsEntry("request-1", request);
        assertThat(entry.isSubAgent()).isTrue();
    }
}
