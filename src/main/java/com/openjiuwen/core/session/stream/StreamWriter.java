/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.Map;
import java.util.function.Function;

/**
 * Stream writer that validates and writes stream data to a {@link StreamEmitter}.
 * <p>
 * Mirrors Python's {@code StreamWriter} in
 * {@code openjiuwen/core/session/stream/writer.py}.
 * </p>
 *
 * @param <S> stream schema type
 */
public class StreamWriter<S> {

    private final StreamEmitter streamEmitter;
    private final Class<S> schemaType;
    private final Function<Map<String, Object>, S> validator;

    public StreamWriter(StreamEmitter streamEmitter,
                        Class<S> schemaType,
                        Function<Map<String, Object>, S> validator) {
        if (streamEmitter == null) {
            throw new IllegalArgumentException("streamEmitter cannot be null");
        }
        this.streamEmitter = streamEmitter;
        this.schemaType = schemaType;
        this.validator = validator;
    }

    @SuppressWarnings("unchecked")
    public void write(Object streamData) {
        if (streamData == null) {
            throw ErrorHelper.buildError(
                    StatusCode.STREAM_WRITER_WRITE_STREAM_VALIDATION_ERROR,
                    "stream_type",
                    schemaType.getSimpleName(),
                    "reason",
                    "stream data is null"
            );
        }

        S validatedData;
        try {
            if (schemaType.isInstance(streamData)) {
                validatedData = schemaType.cast(streamData);
            } else if (streamData instanceof Map<?, ?> rawMap) {
                validatedData = validator.apply((Map<String, Object>) rawMap);
            } else {
                throw new IllegalArgumentException(
                        "stream data must be "
                                + schemaType.getSimpleName()
                                + " or Map, got "
                                + streamData.getClass().getSimpleName()
                );
            }
        } catch (Exception exception) {
            throw ErrorHelper.buildError(
                    StatusCode.STREAM_WRITER_WRITE_STREAM_VALIDATION_ERROR,
                    "stream_type",
                    schemaType.getSimpleName(),
                    "reason",
                    exception.getMessage()
            );
        }

        try {
            doWrite(validatedData);
        } catch (Exception error) {
            throw ErrorHelper.buildError(
                    StatusCode.STREAM_WRITER_WRITE_STREAM_ERROR,
                    "reason",
                    error.getMessage()
            );
        }
    }

    protected void doWrite(S validatedData) {
        if (streamEmitter != null && !streamEmitter.isClosed()) {
            streamEmitter.emit(validatedData);
        } else {
            Loggers.SESSION.warning(
                    "Stream message discarded, emitter already closed, dataType={}",
                    validatedData.getClass().getSimpleName()
            );
        }
    }
}
