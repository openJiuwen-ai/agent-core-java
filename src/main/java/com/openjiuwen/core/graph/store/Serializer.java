/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Abstract serializer for graph state persistence.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.store.serde.Serializer}.
 * 
 * @since 0.1.7
 */
public abstract class Serializer {
    /**
     * dumpsTyped.
     * 
     * @param obj obj
     * @return the result
     * @since 0.1.7
     */
    public abstract TypedBytes dumpsTyped(Object obj);

    /**
     * Deserialize a typed byte representation back to an object.
     * 
     * @param data the typed bytes
     * @return the deserialized object
     * @since 0.1.7
     */
    public abstract Object loadsTyped(TypedBytes data);

    /**
     * Container for typed serialized data.
     * 
     * @since 0.1.7
     */
    public record TypedBytes(String type, byte[] data) {
    }

    /**
     * Signals that an object could not be serialized or deserialized.
     *
     * @since 0.1.14
     */
    public static class SerializationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * Creates a serialization failure with its original cause.
         *
         * @param message failure description
         * @param cause original serialization failure
         * @since 0.1.14
         */
        public SerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Create a serializer of the given type.
     * 
     * @param typeName serializer type name ("json" or "java")
     * @return the serializer instance
     * @since 0.1.7
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
     * 
     * @since 0.1.7
     */
    public static class JsonSerializer extends Serializer {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        /**
         * dumpsTyped.
         * 
         * @param obj obj
         * @return the result
         * @since 0.1.7
         */
        @Override
        public TypedBytes dumpsTyped(Object obj) {
            try {
                byte[] bytes = MAPPER.writeValueAsBytes(obj);
                return new TypedBytes("json", bytes);
            } catch (JsonProcessingException e) {
                throw new SerializationException("Failed to serialize object to JSON", e);
            }
        }

        /**
         * loadsTyped.
         * 
         * @param data data
         * @return the result
         * @since 0.1.7
         */
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
                throw new SerializationException("Failed to deserialize JSON", e);
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
     * 
     * @since 0.1.7
     */
    public static class JavaNativeSerializer extends Serializer {
        /**
         * dumpsTyped.
         * 
         * @param obj obj
         * @return the result
         * @since 0.1.7
         */
        @Override
        public TypedBytes dumpsTyped(Object obj) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(obj);
                oos.flush();
                return new TypedBytes("java", bos.toByteArray());
            } catch (IOException e) {
                throw new SerializationException("Failed to serialize object with Java native serialization", e);
            }
        }

        /**
         * loadsTyped.
         * 
         * @param data data
         * @return the result
         * @since 0.1.7
         */
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
                throw new SerializationException("Failed to deserialize object with Java native serialization", e);
            }
        }
    }
}
