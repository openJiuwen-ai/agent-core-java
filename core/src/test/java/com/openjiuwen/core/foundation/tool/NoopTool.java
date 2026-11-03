/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Shared no-op tool for lifecycle and stress tests. Registers through the normal harness/tool paths and
 * answers every invocation with a fixed string, so tests observe
 * registration and resolution behavior without any side effects.
 *
 * @since 0.1.16
 */
public class NoopTool extends Tool {
    /**
     * Creates the tool with a caller-provided card.
     *
     * @param card tool card carrying the id used for registration
     */
    public NoopTool(ToolCard card) {
        super(card);
    }

    /**
     * Creates the tool with a fixed benchmark card.
     */
    public NoopTool() {
        this(ToolCard.builder().id("noop-tool").name("noop-tool").description("noop test tool").build());
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return "ok";
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return List.of((Object) "ok").iterator();
    }
}
