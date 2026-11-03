/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import java.util.Locale;

/**
 * Mirrors Python's {@code EvolutionEventKind} in
 * {@code openjiuwen/harness/rails/evolution/contracts.py}.
 */
public enum EvolutionEventKind {
    APPROVAL("approval"),
    PROGRESS("progress"),
    OUTCOME("outcome");

    private final String value;

    EvolutionEventKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static EvolutionEventKind fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Evolution event kind is required");
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (EvolutionEventKind kind : values()) {
            if (kind.value.equals(normalized)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unsupported evolution event kind: " + value);
    }
}
