/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for stream exports.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.stream} in
 * {@code openjiuwen/core/session/stream/__init__.py}.
 * </p>
 */
public final class StreamPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/session/stream/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "StreamMode",
            "OutputSchema",
            "TraceSchema",
            "CustomSchema",
            "StreamSchemas",
            "StreamEmitter",
            "AsyncStreamQueue",
            "BaseStreamMode",
            "StreamWriterManager",
            "StreamWriter"
    );

    public static final Class<StreamMode> STREAM_MODE = StreamMode.class;
    public static final Class<OutputSchema> OUTPUT_SCHEMA = OutputSchema.class;
    public static final Class<TraceSchema> TRACE_SCHEMA = TraceSchema.class;
    public static final Class<CustomSchema> CUSTOM_SCHEMA = CustomSchema.class;
    public static final List<Class<?>> STREAM_SCHEMAS = List.of(OutputSchema.class, CustomSchema.class, TraceSchema.class);
    public static final Class<StreamEmitter> STREAM_EMITTER = StreamEmitter.class;
    public static final Class<AsyncStreamQueue> ASYNC_STREAM_QUEUE = AsyncStreamQueue.class;
    public static final Class<StreamMode> BASE_STREAM_MODE = StreamMode.class;
    public static final Class<StreamWriterManager> STREAM_WRITER_MANAGER = StreamWriterManager.class;
    public static final Class<StreamWriter> STREAM_WRITER = StreamWriter.class;

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private StreamPackage() {
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("StreamMode", StreamMode.class);
        exports.put("OutputSchema", OutputSchema.class);
        exports.put("TraceSchema", TraceSchema.class);
        exports.put("CustomSchema", CustomSchema.class);
        exports.put("StreamEmitter", StreamEmitter.class);
        exports.put("AsyncStreamQueue", AsyncStreamQueue.class);
        exports.put("BaseStreamMode", StreamMode.class);
        exports.put("StreamWriterManager", StreamWriterManager.class);
        exports.put("StreamWriter", StreamWriter.class);
        return Map.copyOf(exports);
    }
}
