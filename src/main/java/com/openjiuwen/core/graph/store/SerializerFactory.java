/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.store;

/**
 * 序列化器工厂类。
 * 
 * <p>根据类型名称创建相应的序列化器实例。
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/graph/store/serde.py - create_serializer
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public final class SerializerFactory {
    
    /**
     * JSON 序列化器类型名称。
     */
    public static final String TYPE_JSON = "json";
    
    /**
     * Java 序列化器类型名称（对应 Python 的 pickle）。
     */
    public static final String TYPE_JAVA = "java";
    
    /**
     * Python pickle 类型名称（映射到 Java 序列化器）。
     */
    public static final String TYPE_PICKLE = "pickle";
    
    private SerializerFactory() {
        // 工具类，禁止实例化
    }
    
    /**
     * 根据类型名称创建序列化器。
     *
     * @param typeName 序列化器类型名称（"json", "java", "pickle"）
     * @return 对应的序列化器实例
     * @throws IllegalArgumentException 如果类型名称不支持
     */
    public static Serializer createSerializer(String typeName) {
        if (typeName == null) {
            throw new IllegalArgumentException("Serializer type name cannot be null");
        }
        
        switch (typeName.toLowerCase()) {
            case TYPE_JSON:
                // 注意：Python 代码中 json 类型暂不支持，但 Java 版本可以支持
                // 如果需要与 Python 保持完全一致，可以取消下面的注释
                // throw new IllegalArgumentException("json is not yet supported");
                return new JsonSerializer();
            case TYPE_JAVA:
            case TYPE_PICKLE:
                // pickle 和 java 都映射到 JavaSerializer
                return new JavaSerializer();
            default:
                throw new IllegalArgumentException("Unknown serializer type: " + typeName);
        }
    }
}

