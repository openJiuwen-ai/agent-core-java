/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

/**
 * Origin layer of a Skill in the three-level repository hierarchy.
 *
 * @since 0.1.17
 */
public enum SkillSource {
    CLASSPATH("classpath"),
    FILESYSTEM("filesystem"),
    WORKSPACE("workspace");

    private final String value;

    SkillSource(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
