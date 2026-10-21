/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.toolgroup;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Captures the full tool list in {@link ModelCallInputs} before an LLM request. */
public final class ModelToolsCaptureRail extends AgentRail {

    private final Queue<List<Object>> capturedToolLists = new ConcurrentLinkedQueue<>();

    @Override
    public void beforeModelCall(AgentCallbackContext context) {
        if (context != null && context.getInputs() instanceof ModelCallInputs modelCallInputs) {
            List<Object> tools =
                    modelCallInputs.getTools() == null ? List.of() : modelCallInputs.getTools();
            capturedToolLists.add(List.copyOf(tools));
        }
    }

    /**
     * Returns every captured tool list.
     *
     * @return immutable copy of captured tool lists
     */
    public List<List<Object>> capturedToolLists() {
        return List.copyOf(capturedToolLists);
    }

    /**
     * Returns the most recently captured tool list.
     *
     * @return latest tool list, or an empty list when no request was captured
     */
    public List<Object> lastCapturedToolList() {
        return capturedToolLists.stream().reduce((first, second) -> second).orElseGet(List::of);
    }

    /**
     * Removes all captured tool lists.
     *
     */
    public void clear() {
        capturedToolLists.clear();
    }
}
