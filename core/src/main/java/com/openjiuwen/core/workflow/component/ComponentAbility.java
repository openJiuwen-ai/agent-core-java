/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

/**
 * Mirrors Python's {@code ComponentAbility} in
 * {@code openjiuwen/core/workflow/components/base.py}.
 */
public enum ComponentAbility {
    INVOKE("invoke", "batch in, batch out"),
    STREAM("stream", "batch in, stream out"),
    COLLECT("collect", "stream in, batch out"),
    TRANSFORM("transform", "stream in, stream out");

    private final String name;
    private final String desc;

    ComponentAbility(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    public String getAbilityName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }
}
