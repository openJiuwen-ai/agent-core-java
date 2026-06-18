/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.ArrayList;
import java.util.List;

/**
 * Compacts multimodal context windows by replacing older screenshot blocks.
 *
 * <p>Mirrors Python's {@code MultimodalContextSummarizerRail} in
 * {@code openjiuwen/harness/tools/mobile_gui/rails/multimodal_context_summarizer_rail.py}.</p>
 */
public class MultimodalContextSummarizerRail extends DeepAgentRail {

    public static final String ARCHIVED_SCREEN_PLACEHOLDER =
            "[archived screenshot omitted; use current observation instead]";

    private final int maxMessages;

    public MultimodalContextSummarizerRail(int maxMessages) {
        this.maxMessages = Math.max(1, maxMessages);
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (ctx == null || !(ctx.get("messages") instanceof List<?> list) || list.size() <= maxMessages) {
            return;
        }
        List<Object> compacted = new ArrayList<>(list.subList(Math.max(0, list.size() - maxMessages), list.size()));
        compacted.add(0, ARCHIVED_SCREEN_PLACEHOLDER);
        ctx.put("messages", compacted);
    }
}
