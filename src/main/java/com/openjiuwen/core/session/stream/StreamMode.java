/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import java.util.Map;

/**
 * Enum representing stream output modes.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public enum StreamMode {
    
    /**
     * Standard stream data defined by the framework.
     */
    OUTPUT("output", "Standard stream data defined by the framework"),
    
    /**
     * Trace stream data produced by the graph.
     */
    TRACE("trace", "Trace stream data produced by the graph"),
    
    /**
     * Custom stream data defined by the runnable.
     */
    CUSTOM("custom", "Custom stream data defined by the runnable");
    
    private final String mode;
    private final String desc;
    private final Map<String, Object> options;
    
    StreamMode(String mode, String desc) {
        this(mode, desc, Map.of());
    }
    
    StreamMode(String mode, String desc, Map<String, Object> options) {
        this.mode = mode;
        this.desc = desc;
        this.options = options;
    }
    
    /**
     * Gets the mode string.
     * 
     * @return the mode
     */
    public String getMode() {
        return mode;
    }
    
    /**
     * Gets the description.
     * 
     * @return the description
     */
    public String getDesc() {
        return desc;
    }
    
    /**
     * Gets the options map.
     * 
     * @return the options
     */
    public Map<String, Object> getOptions() {
        return options;
    }
    
    @Override
    public String toString() {
        return "StreamMode(mode=" + mode + ", desc=" + desc + ", options=" + options + ")";
    }
}

/**
 * Base stream mode constants for compatibility with Python's BaseStreamMode.
 * Provides static references to the standard StreamMode values.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
class BaseStreamMode {
    
    /**
     * Standard stream data defined by the framework.
     */
    public static final StreamMode OUTPUT = StreamMode.OUTPUT;
    
    /**
     * Trace stream data produced by the graph.
     */
    public static final StreamMode TRACE = StreamMode.TRACE;
    
    /**
     * Custom stream data defined by the runnable.
     */
    public static final StreamMode CUSTOM = StreamMode.CUSTOM;
    
    private BaseStreamMode() {
        // Utility class, prevent instantiation
    }
}

