/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shared lightweight gateway constants and helpers.
 * <p>
 * Mirrors Python's module helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/common.py}.
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
        return OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .replace("Z", "+00:00");
    }

    public static List<Double> fitList(List<Double> values, int expectedLen) {
        if (expectedLen <= 0) {
            return List.of();
        }
        List<Double> safeValues = values == null ? new ArrayList<>() : new ArrayList<>(values);
        if (safeValues.size() > expectedLen) {
            return new ArrayList<>(safeValues.subList(0, expectedLen));
        }
        if (safeValues.size() < expectedLen) {
            List<Double> padded = new ArrayList<>(safeValues);
            while (padded.size() < expectedLen) {
                padded.add(0.0d);
            }
            return padded;
        }
        return safeValues;
    }
}
