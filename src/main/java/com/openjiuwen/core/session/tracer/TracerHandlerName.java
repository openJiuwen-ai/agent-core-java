/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

/**
 * Trigger handler name enumeration.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public enum TracerHandlerName {
    
    TRACE_AGENT("tracer_agent"),
    TRACER_WORKFLOW("tracer_workflow");
    
    private final String value;
    
    TracerHandlerName(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}

