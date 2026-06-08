/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Outcome of a single payload delivery.
 *
 * <p>Mirrors Python's {@code DeliverResult} in
 * {@code openjiuwen/agent_teams/interaction/payload.py}.</p>
 */
public record DeliverResult(
        boolean ok,
        @JsonProperty("message_id") String messageId,
        String reason
) {

    public static DeliverResult success() {
        return new DeliverResult(true, null, null);
    }

    public static DeliverResult success(String messageId) {
        return new DeliverResult(true, messageId, null);
    }

    public static DeliverResult failure(String reason) {
        return new DeliverResult(false, null, reason);
    }
}
