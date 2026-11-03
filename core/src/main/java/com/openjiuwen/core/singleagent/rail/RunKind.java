/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Run kind enumeration for agent execution modes.
 *
 * <p>Mirrors Python's {@code RunKind} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
public enum RunKind {
    NORMAL("normal"),
    HEARTBEAT("heartbeat"),
    CRON("cron");

    private final String value;

    RunKind(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RunKind fromValue(String value) {
        for (RunKind item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }
}
