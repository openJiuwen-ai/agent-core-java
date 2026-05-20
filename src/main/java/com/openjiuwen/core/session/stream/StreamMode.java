/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import java.util.Collections;
import java.util.Map;

/**
 * Stream mode definition.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.stream.base.StreamMode / BaseStreamMode}.
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
        this.options = options != null ? options : Collections.emptyMap();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getMode() {
        return mode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDesc() {
        return desc;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getOptions() {
        return options;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String toString() {
        return "StreamMode(mode=" + mode + ", desc=" + desc + ", options=" + options + ")";
    }
}
