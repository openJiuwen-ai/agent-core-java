/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.legacy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy event-type to agent-id router.
 *
 * <p>Mirrors Python's legacy default group controller in
 * {@code openjiuwen/core/multi_agent/legacy/group.py}.</p>
 */
public class DefaultGroupController {
    private final Map<String, List<String>> subscriptions = new LinkedHashMap<>();

    public void subscribe(String eventType, List<String> agentIds) {
        subscriptions.put(eventType, agentIds == null ? List.of() : List.copyOf(agentIds));
    }

    public List<String> route(GroupEvent event) {
        if (event == null || event.getCustomEventType() == null) {
            return List.of();
        }
        return subscriptions.getOrDefault(event.getCustomEventType(), List.of());
    }

    public Map<String, List<String>> getSubscriptions() {
        return Map.copyOf(subscriptions);
    }
}
