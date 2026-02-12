/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

/**
 * Workflow node status enumeration.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public enum NodeStatus {
    
    START("start"),
    FINISH("finish"),
    RUNNING("running"),
    ERROR("error");
    
    private final String value;
    
    NodeStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}

