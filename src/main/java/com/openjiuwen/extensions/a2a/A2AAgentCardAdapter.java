/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import java.util.*;

/**
 * A2A agent card adapter — bridges openjiuwen agent cards to A2A format.
 * <p>
 * Mirrors Python's {@code A2AAgentCardAdapter} in
 * {@code openjiuwen.extensions.a2a.a2a_agentcard_adapter}.
 */
public class A2AAgentCardAdapter {

    /** Convert an openjiuwen agent card to A2A agent card. */
    public static Map<String, Object> toA2aCard(Map<String, Object> agentCard) {
        Map<String, Object> a2aCard = new LinkedHashMap<>();
        a2aCard.put("name", agentCard.getOrDefault("name", "unknown"));
        a2aCard.put("description", agentCard.getOrDefault("description", ""));
        a2aCard.put("url", agentCard.getOrDefault("endpoint", ""));
        return a2aCard;
    }

    /** Convert an A2A agent card to openjiuwen format. */
    public static Map<String, Object> fromA2aCard(Map<String, Object> a2aCard) {
        Map<String, Object> agentCard = new LinkedHashMap<>();
        agentCard.put("name", a2aCard.getOrDefault("name", "unknown"));
        agentCard.put("description", a2aCard.getOrDefault("description", ""));
        return agentCard;
    }
}
