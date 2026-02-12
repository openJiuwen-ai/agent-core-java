/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Writes stream data to a StreamEmitter with schema validation.
 * 
 * @param <T> the input type
 * @param <S> the schema type
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class StreamWriter<T, S> {
    
    private static final LoggerProtocol logger = LogManager.getLogger("session");
    
    protected final StreamEmitter streamEmitter;
    protected final Function<Map<String, Object>, S> schemaValidator;
    protected final String schemaTypeName;
    
    /**
     * Creates a new StreamWriter.
     * 
     * @param streamEmitter the stream emitter
     * @param schemaValidator function to validate and convert data to schema
     * @param schemaTypeName the schema type name for error messages
     * @throws IllegalArgumentException if streamEmitter is null
     */
    public StreamWriter(StreamEmitter streamEmitter, 
                       Function<Map<String, Object>, S> schemaValidator, 
                       String schemaTypeName) {
        if (streamEmitter == null) {
            throw new IllegalArgumentException("stream_emitter can not be None");
        }
        this.streamEmitter = streamEmitter;
        this.schemaValidator = schemaValidator;
        this.schemaTypeName = schemaTypeName;
    }
    
    /**
     * Writes stream data.
     * 
     * @param streamData the data to write
     * @return a CompletableFuture that completes when the data is written
     * @throws JiuWenBaseException if validation fails or data is null
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> write(T streamData) {
        if (streamData == null) {
            return CompletableFuture.failedFuture(
                new JiuWenBaseException(
                    StatusCode.STREAM_WRITER_WRITE_FAILED.getCode(), 
                    StatusCode.STREAM_WRITER_WRITE_FAILED.formatMessage(
                        Map.of("reason", "can not write None"))));
        }
        
        try {
            S validatedData = schemaValidator.apply((Map<String, Object>) streamData);
            return doWrite(validatedData);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(
                new JiuWenBaseException(
                    StatusCode.STREAM_WRITER_WRITE_SCHEMA_FAILED.getCode(),
                    StatusCode.STREAM_WRITER_WRITE_SCHEMA_FAILED.formatMessage(
                        Map.of("detail", "Data validation failed for schema " + schemaTypeName))));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                new JiuWenBaseException(
                    StatusCode.STREAM_WRITER_WRITE_FAILED.getCode(),
                    StatusCode.STREAM_WRITER_WRITE_FAILED.formatMessage(
                        Map.of("reason", e.getMessage()))));
        }
    }
    
    /**
     * Performs the actual write operation.
     * 
     * @param validatedData the validated data
     * @return a CompletableFuture that completes when the data is written
     */
    protected CompletableFuture<Void> doWrite(S validatedData) {
        if (streamEmitter != null && !streamEmitter.isClosed()) {
            return streamEmitter.emit(validatedData);
        } else {
            logger.warning("discard message [{}], because stream emitter has already been closed",
                validatedData);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * Gets the stream emitter.
     * 
     * @return the stream emitter
     */
    public StreamEmitter getStreamEmitter() {
        return streamEmitter;
    }
    
    /**
     * Gets the schema type name.
     * 
     * @return the schema type name
     */
    public String getSchemaTypeName() {
        return schemaTypeName;
    }
}

