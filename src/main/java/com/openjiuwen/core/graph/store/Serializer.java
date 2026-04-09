/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.graph.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Abstract serializer for graph state persistence.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.store.serde.Serializer}.
 */
public abstract class Serializer {

    /**
     * Serialize an object to a typed byte representation.
     *
     * @param obj the object to serialize
     * @return a pair of (type tag, byte data)
     */
    public abstract TypedBytes dumpsTyped(Object obj);

    /**
     * Deserialize a typed byte representation back to an object.
     *
     * @param data the typed bytes
     * @return the deserialized object
     */
    public abstract Object loadsTyped(TypedBytes data);

    /**
     * Container for typed serialized data.
     */
    public record TypedBytes(String type, byte[] data) {
    }

    /**
     * Create a serializer of the given type.
     *
     * @param typeName serializer type name ("json" or "java")
     * @return the serializer instance
     */
    public static Serializer create(String typeName) {
        if ("json".equals(typeName)) {
            return new JsonSerializer();
        }
        if ("java".equals(typeName)) {
            return new JavaNativeSerializer();
        }
        throw new IllegalArgumentException("Unknown serializer type: " + typeName);
    }

    /**
     * JSON-based serializer implementation using Jackson.
     */
    public static class JsonSerializer extends Serializer {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        @Override
        public TypedBytes dumpsTyped(Object obj) {
            try {
                byte[] bytes = MAPPER.writeValueAsBytes(obj);
                return new TypedBytes("json", bytes);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize object to JSON", e);
            }
        }

        @Override
        public Object loadsTyped(TypedBytes data) {
            if (data == null) {
                return null;
            }
            if (!"json".equals(data.type())) {
                return null;
            }
            try {
                return MAPPER.readValue(data.data(), Object.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to deserialize JSON", e);
            }
        }
    }

    /**
     * Java native serialization-based serializer.
     * <p>
     * Mirrors Python's {@code PickleSerializer} — uses Java's built-in ObjectOutputStream/ObjectInputStream
     * as the equivalent of Python's pickle module.
     * <p>
     * Note: objects must implement {@link java.io.Serializable} to be serialized.
     */
    public static class JavaNativeSerializer extends Serializer {

        @Override
        public TypedBytes dumpsTyped(Object obj) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(obj);
                oos.flush();
                return new TypedBytes("java", bos.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException("Failed to serialize object with Java native serialization", e);
            }
        }

        @Override
        public Object loadsTyped(TypedBytes data) {
            if (data == null) {
                return null;
            }
            if (!"java".equals(data.type())) {
                return null;
            }
            try (ByteArrayInputStream bis = new ByteArrayInputStream(data.data());
                 ObjectInputStream ois = new ObjectInputStream(bis)) {
                return ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException("Failed to deserialize object with Java native serialization", e);
            }
        }
    }
}
