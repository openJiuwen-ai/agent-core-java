/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.store;

/**
 * 序列化异常。
 * 
 * <p>当序列化或反序列化操作失败时抛出。
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class SerializationException extends RuntimeException {
    
    /**
     * 使用指定的错误消息构造异常。
     *
     * @param message 错误消息
     */
    public SerializationException(String message) {
        super(message);
    }
    
    /**
     * 使用指定的错误消息和原因构造异常。
     *
     * @param message 错误消息
     * @param cause 原因
     */
    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

