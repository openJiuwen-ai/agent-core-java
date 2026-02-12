/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import java.util.Map;

/**
 * Stream writer for CustomSchema data.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class CustomStreamWriter extends StreamWriter<Map<String, Object>, CustomSchema> {
    
    /**
     * Creates a new CustomStreamWriter.
     * 
     * @param streamEmitter the stream emitter
     */
    public CustomStreamWriter(StreamEmitter streamEmitter) {
        super(streamEmitter, CustomSchema::fromMap, "CustomSchema");
    }
}

