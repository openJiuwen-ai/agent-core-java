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
 * 
 * @since 0.1.7
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
     * getMode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getMode() {
        return mode;
    }

    /**
     * getDesc.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDesc() {
        return desc;
    }

    /**
     * getOptions.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getOptions() {
        return options;
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "StreamMode(mode=" + mode + ", desc=" + desc + ", options=" + options + ")";
    }
}
