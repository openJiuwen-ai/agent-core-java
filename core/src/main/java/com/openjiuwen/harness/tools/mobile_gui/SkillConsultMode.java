/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

/**
 * Mirrors Python's {@code SkillConsultMode} in
 * {@code openjiuwen/harness/tools/mobile_gui/config.py}.
 */
public enum SkillConsultMode {
    BRANCH("branch"),
    INLINE("inline");

    private final String value;

    SkillConsultMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static SkillConsultMode fromRaw(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase();
        return "inline".equals(normalized) ? INLINE : BRANCH;
    }

    @Override
    public String toString() {
        return value;
    }
}
