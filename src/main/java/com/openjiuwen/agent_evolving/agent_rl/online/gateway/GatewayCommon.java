/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shared lightweight gateway constants and helpers.
 * <p>
 * Mirrors Python's helpers in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.common}.
 */
public final class GatewayCommon {

    public static final Set<String> NON_STANDARD_BODY_KEYS = Set.of(
            "session_id",
            "session_done",
            "turn_type",
            "memory_scope",
            "user_id",
            "workspace_id"
    );

    private GatewayCommon() {
    }

    public static String utcNowIso() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    public static List<Double> fitList(List<Double> values, int expectedLen) {
        if (expectedLen <= 0) {
            return List.of();
        }
        List<Double> safeValues = values != null ? new ArrayList<>(values) : new ArrayList<>();
        if (safeValues.size() > expectedLen) {
            return new ArrayList<>(safeValues.subList(0, expectedLen));
        }
        while (safeValues.size() < expectedLen) {
            safeValues.add(0.0);
        }
        return safeValues;
    }
}
