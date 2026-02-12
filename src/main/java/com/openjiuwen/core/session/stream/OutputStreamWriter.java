/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import java.util.Map;

/**
 * Stream writer for OutputSchema data.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class OutputStreamWriter extends StreamWriter<Map<String, Object>, OutputSchema> {
    
    /**
     * Creates a new OutputStreamWriter.
     * 
     * @param streamEmitter the stream emitter
     */
    public OutputStreamWriter(StreamEmitter streamEmitter) {
        super(streamEmitter, OutputSchema::fromMap, "OutputSchema");
    }
}

