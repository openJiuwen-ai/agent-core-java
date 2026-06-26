/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.workflow.components.flow.StartComponent;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code Start} in
 * {@code openjiuwen/core/workflow/components/flow/start_comp.py}.
 */
class T01172StartComponentTest {

    @Test
    void invokeReturnsInputsAsIs() {
        Start start = new Start();
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", "hello");
        inputs.put("count", 1);

        Object output = start.invoke(inputs, null, null);

        assertSame(inputs, output);
    }

    @Test
    void aliasStartComponentUsesSamePassThroughBehavior() {
        StartComponent component = new StartComponent();
        Object inputs = "raw-input";

        Object output = component.invoke(inputs, null, null);

        assertSame(inputs, output);
    }
}
