/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import java.util.Collections;
import java.util.Map;

/**
 * Mirrors Python's {@code StreamMode / BaseStreamMode} in
 * {@code openjiuwen/core/session/stream/base.py}.
 */
public enum StreamMode {
    OUTPUT("output", "Standard stream data defined by the framework"),
    TRACE("trace", "Trace stream data produced by the graph"),
    CUSTOM("custom", "Custom stream data defined by the runnable");

    private final String mode;
    private final String desc;
    private final Map<String, Object> options;

    StreamMode(String mode, String desc) {
        this(mode, desc, Collections.emptyMap());
    }

    StreamMode(String mode, String desc, Map<String, Object> options) {
        this.mode = mode;
        this.desc = desc;
        this.options = options == null ? Collections.emptyMap() : options;
    }

    public String getMode() {
        return mode;
    }

    public String getDesc() {
        return desc;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    @Override
    public String toString() {
        return "StreamMode(mode=" + mode + ", desc=" + desc + ", options=" + options + ")";
    }
}
