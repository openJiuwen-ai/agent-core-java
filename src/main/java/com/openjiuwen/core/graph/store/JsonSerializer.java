/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JSON 序列化器实现。
 * 
 * <p>使用 Jackson 进行 JSON 序列化和反序列化。
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/graph/store/serde.py - JsonSerializer
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class JsonSerializer implements Serializer {
    
    private static final String TYPE_NAME = "json";
    
    private final ObjectMapper objectMapper;
    
    /**
     * 使用默认的 ObjectMapper 构造 JsonSerializer。
     */
    public JsonSerializer() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 使用指定的 ObjectMapper 构造 JsonSerializer。
     *
     * @param objectMapper 要使用的 ObjectMapper 实例
     */
    public JsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public TypedData dumpsTyped(Object obj) {
        try {
            String jsonString = objectMapper.writeValueAsString(obj);
            byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);
            return new TypedData(TYPE_NAME, bytes);
        } catch (JsonProcessingException e) {
            throw new SerializationException("Failed to serialize object to JSON", e);
        }
    }
    
    @Override
    public Object loadsTyped(TypedData data) {
        if (data == null) {
            return null;
        }
        if (!TYPE_NAME.equals(data.type())) {
            return null;
        }
        try {
            String jsonString = new String(data.data(), StandardCharsets.UTF_8);
            return objectMapper.readValue(jsonString, Object.class);
        } catch (IOException e) {
            throw new SerializationException("Failed to deserialize object from JSON", e);
        }
    }
    
    /**
     * 从 JSON 反序列化为指定类型的对象。
     *
     * @param <T> 目标类型
     * @param data 带类型标识的数据
     * @param clazz 目标类
     * @return 反序列化后的对象，如果数据为 null 或类型不匹配则返回 null
     */
    public <T> T loadsTyped(TypedData data, Class<T> clazz) {
        if (data == null) {
            return null;
        }
        if (!TYPE_NAME.equals(data.type())) {
            return null;
        }
        try {
            String jsonString = new String(data.data(), StandardCharsets.UTF_8);
            return objectMapper.readValue(jsonString, clazz);
        } catch (IOException e) {
            throw new SerializationException("Failed to deserialize object from JSON", e);
        }
    }
}

