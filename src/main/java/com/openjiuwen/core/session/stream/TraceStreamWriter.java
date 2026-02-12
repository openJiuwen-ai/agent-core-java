/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import java.util.Map;

/**
 * Stream writer for TraceSchema data.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TraceStreamWriter extends StreamWriter<Map<String, Object>, TraceSchema> {
    
    /**
     * Creates a new TraceStreamWriter.
     * 
     * @param streamEmitter the stream emitter
     */
    public TraceStreamWriter(StreamEmitter streamEmitter) {
        super(streamEmitter, TraceSchema::fromMap, "TraceSchema");
    }
}

