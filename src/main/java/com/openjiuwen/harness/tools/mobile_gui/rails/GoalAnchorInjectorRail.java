/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects a short-lived goal anchor into leading system messages.
 *
 * <p>Mirrors Python's {@code GoalAnchorInjectorRail} in
 * {@code openjiuwen/harness/tools/mobile_gui/rails/goal_anchor_injector_rail.py}.</p>
 */
public class GoalAnchorInjectorRail extends DeepAgentRail {

    public static final String GOAL_ANCHOR_KEY = "_ephemeral_goal_anchor";
    public static final String GOAL_ANCHOR_INJECTOR_STATE_KEY = "_goal_anchor_injector_state";

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (ctx == null) {
            return;
        }
        Object anchor = ctx.get(GOAL_ANCHOR_KEY);
        if (anchor == null || String.valueOf(anchor).isBlank()) {
            return;
        }
        List<String> messages = leadingMessages(ctx.get("messages"));
        if (messages.isEmpty()) {
            messages.add(String.valueOf(anchor));
        } else {
            messages.set(0, messages.get(0) + "\n\n" + anchor);
        }
        ctx.put("messages", messages);
        ctx.put(GOAL_ANCHOR_INJECTOR_STATE_KEY, true);
    }

    private static List<String> leadingMessages(Object rawMessages) {
        List<String> result = new ArrayList<>();
        if (rawMessages instanceof Iterable<?> iterable) {
            for (Object message : iterable) {
                result.add(String.valueOf(message));
            }
        }
        return result;
    }
}
