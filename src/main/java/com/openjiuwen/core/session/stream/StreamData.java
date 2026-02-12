/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.stream;

/**
 * Represents stream data with code, message, data, execution ID, and index.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class StreamData {
    
    private final StreamCode code;
    private final String msg;
    private final Object data;
    private final String executionId;
    private final int index;
    
    /**
     * Creates a new StreamData.
     * 
     * @param code the stream code
     * @param msg the message
     * @param data the data payload
     * @param executionId the execution identifier
     */
    public StreamData(StreamCode code, String msg, Object data, String executionId) {
        this(code, msg, data, executionId, 0);
    }
    
    /**
     * Creates a new StreamData with index.
     * 
     * @param code the stream code
     * @param msg the message
     * @param data the data payload
     * @param executionId the execution identifier
     * @param index the index
     */
    public StreamData(StreamCode code, String msg, Object data, String executionId, int index) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.executionId = executionId;
        this.index = index;
    }
    
    /**
     * Gets the stream code.
     * 
     * @return the code
     */
    public StreamCode getCode() {
        return code;
    }
    
    /**
     * Gets the message.
     * 
     * @return the message
     */
    public String getMsg() {
        return msg;
    }
    
    /**
     * Gets the data payload.
     * 
     * @return the data
     */
    public Object getData() {
        return data;
    }
    
    /**
     * Gets the execution identifier.
     * 
     * @return the execution ID
     */
    public String getExecutionId() {
        return executionId;
    }
    
    /**
     * Gets the index.
     * 
     * @return the index
     */
    public int getIndex() {
        return index;
    }
    
    @Override
    public String toString() {
        return "StreamData(code=" + code + ", msg=" + msg + ", data=" + data +
               ", executionId=" + executionId + ", index=" + index + ")";
    }
}

