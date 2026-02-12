/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.security.UserConfig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Manages stream writers for different stream modes.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class StreamWriterManager {
    
    private static final LoggerProtocol logger = LogManager.getLogger("session");
    private static final double DEFAULT_FRAME_TIMEOUT = -1;
    
    private final StreamEmitter streamEmitter;
    private final List<StreamMode> defaultModes;
    private final Map<StreamMode, StreamWriter<?, ?>> writers;
    
    /**
     * Creates a new StreamWriterManager with default modes.
     * 
     * @param streamEmitter the stream emitter
     */
    public StreamWriterManager(StreamEmitter streamEmitter) {
        this(streamEmitter, List.of(StreamMode.OUTPUT, StreamMode.TRACE, StreamMode.CUSTOM));
    }
    
    /**
     * Creates a new StreamWriterManager with specified modes.
     * 
     * @param streamEmitter the stream emitter
     * @param modes the stream modes to enable
     * @throws IllegalArgumentException if streamEmitter is null
     */
    public StreamWriterManager(StreamEmitter streamEmitter, List<StreamMode> modes) {
        if (streamEmitter == null) {
            throw new IllegalArgumentException("stream_emitter is None");
        }
        this.streamEmitter = streamEmitter;
        this.defaultModes = modes != null ? modes : List.of(StreamMode.OUTPUT, StreamMode.TRACE, StreamMode.CUSTOM);
        this.writers = new HashMap<>();
        addDefaultWriters();
    }
    
    /**
     * Gets the stream emitter.
     * 
     * @return the stream emitter
     */
    public StreamEmitter streamEmitter() {
        return streamEmitter;
    }
    
    /**
     * Adds a writer for a stream mode.
     * 
     * @param mode the stream mode
     * @param writer the writer
     */
    public void addWriter(StreamMode mode, StreamWriter<?, ?> writer) {
        writers.put(mode, writer);
    }
    
    /**
     * Gets a writer for a stream mode.
     * 
     * @param mode the stream mode
     * @return the writer, or null if not found
     */
    public StreamWriter<?, ?> getWriter(StreamMode mode) {
        return writers.get(mode);
    }
    
    /**
     * Gets the output writer.
     * 
     * @return the output writer
     */
    public OutputStreamWriter getOutputWriter() {
        return (OutputStreamWriter) getWriter(StreamMode.OUTPUT);
    }
    
    /**
     * Gets the trace writer.
     * 
     * @return the trace writer
     */
    public TraceStreamWriter getTraceWriter() {
        return (TraceStreamWriter) getWriter(StreamMode.TRACE);
    }
    
    /**
     * Gets the custom writer.
     * 
     * @return the custom writer
     */
    public CustomStreamWriter getCustomWriter() {
        return (CustomStreamWriter) getWriter(StreamMode.CUSTOM);
    }
    
    /**
     * Removes a writer for a stream mode.
     * 
     * @param mode the stream mode
     * @return the removed writer, or null if not found
     * @throws IllegalArgumentException if trying to remove a default mode writer
     */
    public StreamWriter<?, ?> removeWriter(StreamMode mode) {
        if (defaultModes.contains(mode)) {
            throw new IllegalArgumentException("Can not remove default writer for mode " + mode);
        }
        return writers.remove(mode);
    }
    
    /**
     * Streams output data as an iterable.
     * 
     * @return an iterable of stream data
     */
    public Iterable<Object> streamOutput() {
        return streamOutput(DEFAULT_FRAME_TIMEOUT, DEFAULT_FRAME_TIMEOUT, true);
    }
    
    /**
     * Streams output data as an iterable with timeout configuration.
     * 
     * @param firstFrameTimeout timeout in seconds for the first frame (-1 for no timeout)
     * @param timeout timeout in seconds for subsequent frames (-1 for no timeout)
     * @param needClose whether to close the stream queue after END_FRAME
     * @return an iterable of stream data
     */
    public Iterable<Object> streamOutput(double firstFrameTimeout, double timeout, boolean needClose) {
        return () -> new StreamOutputIterator(firstFrameTimeout, timeout, needClose);
    }
    
    /**
     * Iterator for streaming output data.
     */
    private class StreamOutputIterator implements Iterator<Object> {
        private final double firstFrameTimeout;
        private final double timeout;
        private final boolean needClose;
        private boolean isFirstFrame = true;
        private boolean hasNext = true;
        private Object nextData = null;
        private boolean dataFetched = false;
        
        StreamOutputIterator(double firstFrameTimeout, double timeout, boolean needClose) {
            this.firstFrameTimeout = firstFrameTimeout;
            this.timeout = timeout;
            this.needClose = needClose;
        }
        
        @Override
        public boolean hasNext() {
            if (!hasNext) {
                return false;
            }
            
            if (!dataFetched) {
                fetchNext();
            }
            
            return hasNext;
        }
        
        @Override
        public Object next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more elements in stream");
            }
            
            Object result = nextData;
            dataFetched = false;
            nextData = null;
            return result;
        }
        
        private void fetchNext() {
            try {
                double currentTimeout = isFirstFrame ? firstFrameTimeout : timeout;
                CompletableFuture<Object> future = streamEmitter.getStreamQueue().receive(currentTimeout);
                Object data = future.get();
                
                if (isFirstFrame) {
                    isFirstFrame = false;
                }
                
                if (data != null) {
                    if (StreamEmitter.END_FRAME.equals(data)) {
                        logger.info("Received END_FRAME, stopping stream output.");
                        if (needClose) {
                            streamEmitter.getStreamQueue().close(timeout).get();
                        }
                        hasNext = false;
                        dataFetched = true;
                    } else {
                        if (UserConfig.isSensitive()) {
                            logger.debug("Received stream data");
                        } else {
                            logger.debug("Received stream data: {}", data);
                        }
                        nextData = data;
                        dataFetched = true;
                    }
                } else {
                    logger.debug("No data received, waiting for data.");
                    // Continue fetching
                    fetchNext();
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException && cause.getCause() instanceof TimeoutException) {
                    // Handle timeout
                    if (isFirstFrame) {
                        throw new JiuWenBaseException(
                            StatusCode.STREAM_FIRST_FRAME_TIMEOUT_FAILED.getCode(),
                            StatusCode.STREAM_FIRST_FRAME_TIMEOUT_FAILED.formatMessage(
                                Map.of("timeout", String.valueOf(firstFrameTimeout))));
                    } else {
                        throw new JiuWenBaseException(
                            StatusCode.STREAM_FRAME_TIMEOUT_FAILED.getCode(),
                            StatusCode.STREAM_FRAME_TIMEOUT_FAILED.formatMessage(
                                Map.of("timeout", String.valueOf(timeout))));
                    }
                } else {
                    throw new RuntimeException("Error fetching stream data", e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while fetching stream data", e);
            }
        }
    }
    
    /**
     * Adds default writers for the enabled modes.
     */
    private void addDefaultWriters() {
        for (StreamMode mode : defaultModes) {
            switch (mode) {
                case OUTPUT -> addWriter(mode, new OutputStreamWriter(streamEmitter));
                case TRACE -> addWriter(mode, new TraceStreamWriter(streamEmitter));
                case CUSTOM -> addWriter(mode, new CustomStreamWriter(streamEmitter));
                default -> throw new IllegalArgumentException(
                    "default modes must be OUTPUT, TRACE, CUSTOM, " + mode + " is not supported.");
            }
        }
    }
    
    /**
     * Factory method to create a new manager with default modes.
     * 
     * @param streamEmitter the stream emitter
     * @return the new manager
     */
    public static StreamWriterManager createManager(StreamEmitter streamEmitter) {
        return new StreamWriterManager(streamEmitter);
    }
    
    /**
     * Factory method to create a new manager.
     * 
     * @param streamEmitter the stream emitter
     * @param modes the stream modes to enable (null for default modes)
     * @return the new manager
     */
    public static StreamWriterManager createManager(StreamEmitter streamEmitter, List<StreamMode> modes) {
        return new StreamWriterManager(streamEmitter, modes);
    }
}

