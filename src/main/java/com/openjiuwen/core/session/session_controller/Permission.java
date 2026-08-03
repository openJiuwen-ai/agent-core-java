/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

/**
 * Data access permission level.
 *
 * <p>Mirrors Python's {@code Permission} in
 * {@code openjiuwen/core/session/session_controller/data_container.py}.</p>
 */
public enum Permission {
    READ(1);

    private final int value;

    Permission(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
