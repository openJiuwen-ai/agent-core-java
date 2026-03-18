/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.Map;
import java.util.function.Function;

/**
 * Stream writer that validates and writes stream data to a StreamEmitter.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.stream.writer.StreamWriter}.
 *
 * @param <S> the schema type
 */
public class StreamWriter<S extends StreamSchema> {

    private final StreamEmitter streamEmitter;
    private final Class<S> schemaType;
    private final Function<Map<String, Object>, S> validator;

    /**
     * Create a new StreamWriter.
     *
     * @param streamEmitter the emitter to write to
     * @param schemaType    the schema class
     * @param validator     function to validate/convert a map to the schema type
     */
    public StreamWriter(StreamEmitter streamEmitter, Class<S> schemaType, Function<Map<String, Object>, S> validator) {
        if (streamEmitter == null) {
            throw new IllegalArgumentException("streamEmitter cannot be null");
        }
        this.streamEmitter = streamEmitter;
        this.schemaType = schemaType;
        this.validator = validator;
    }

    /**
     * Write stream data. Validates and emits the data.
     *
     * @param streamData the data to write (either a schema instance or a map)
     */
    @SuppressWarnings("unchecked")
    public void write(Object streamData) {
        if (streamData == null) {
            throw ErrorHelper.buildError(StatusCode.STREAM_WRITER_WRITE_STREAM_VALIDATION_ERROR,
                    "stream_type", schemaType.getSimpleName(),
                    "reason", "stream data is null");
        }

        S validatedData;
        try {
            if (schemaType.isInstance(streamData)) {
                validatedData = schemaType.cast(streamData);
            } else if (streamData instanceof Map) {
                validatedData = validator.apply((Map<String, Object>) streamData);
            } else {
                throw new IllegalArgumentException(
                        "stream data must be " + schemaType.getSimpleName() + " or Map, got " + streamData.getClass().getSimpleName());
            }
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.STREAM_WRITER_WRITE_STREAM_VALIDATION_ERROR,
                    "stream_type", schemaType.getSimpleName(),
                    "reason", e.getMessage());
        }

        try {
            doWrite(validatedData);
        } catch (Exception error) {
            throw ErrorHelper.buildError(StatusCode.STREAM_WRITER_WRITE_STREAM_ERROR,
                    "reason", error.getMessage());
        }
    }

    /**
     * Perform the actual write. Can be overridden by subclasses.
     *
     * @param validatedData the validated data
     */
    protected void doWrite(S validatedData) {
        if (streamEmitter != null && !streamEmitter.isClosed()) {
            streamEmitter.emit(validatedData);
        } else {
            Loggers.SESSION.warning("Stream message discarded, emitter already closed, dataType={}",
                    validatedData.getClass().getSimpleName());
        }
    }
}
