/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's package export surface in
 * {@code openjiuwen/core/session/stream/__init__.py}.
 */
class StreamPackageTest {

    @Test
    void exportsMatchPythonPackageSurface() {
        assertEquals("openjiuwen/core/session/stream/__init__.py", StreamPackage.PYTHON_MODULE);
        assertEquals(
                List.of(
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
                ),
                StreamPackage.EXPORTED_SYMBOLS
        );
        assertEquals(StreamMode.class, StreamPackage.STREAM_MODE);
        assertEquals(OutputSchema.class, StreamPackage.OUTPUT_SCHEMA);
        assertEquals(TraceSchema.class, StreamPackage.TRACE_SCHEMA);
        assertEquals(CustomSchema.class, StreamPackage.CUSTOM_SCHEMA);
        assertEquals(List.of(OutputSchema.class, CustomSchema.class, TraceSchema.class), StreamPackage.STREAM_SCHEMAS);
        assertEquals(StreamEmitter.class, StreamPackage.STREAM_EMITTER);
        assertEquals(AsyncStreamQueue.class, StreamPackage.ASYNC_STREAM_QUEUE);
        assertEquals(StreamMode.class, StreamPackage.BASE_STREAM_MODE);
        assertEquals(StreamWriterManager.class, StreamPackage.STREAM_WRITER_MANAGER);
        assertEquals(StreamWriter.class, StreamPackage.STREAM_WRITER);
        assertEquals(StreamMode.class, StreamPackage.EXPORTED_TYPES.get("BaseStreamMode"));
    }
}
