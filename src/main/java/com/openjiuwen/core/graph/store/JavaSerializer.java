/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Java 原生序列化器实现。
 * 
 * <p>使用 Java 的 ObjectOutputStream/ObjectInputStream 进行序列化和反序列化。
 * 这是 Python PickleSerializer 的 Java 等价实现。
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/graph/store/serde.py - PickleSerializer
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class JavaSerializer implements Serializer {
    
    private static final String TYPE_NAME = "java";
    
    @Override
    public TypedData dumpsTyped(Object obj) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return new TypedData(TYPE_NAME, baos.toByteArray());
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize object using Java serialization", e);
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
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data.data());
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new SerializationException("Failed to deserialize object using Java serialization", e);
        }
    }
}

