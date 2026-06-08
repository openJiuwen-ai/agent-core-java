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
 * Mirrors Python's serializer module in
 * {@code openjiuwen/core/graph/store/serde.py}.
 */
public abstract class Serializer {

    public record TypedBytes(String type, byte[] data) {
    }

    public abstract TypedBytes dumpsTyped(Object obj);

    public abstract Object loadsTyped(TypedBytes data);

    public static Serializer create(String typeName) {
        return createSerializer(typeName);
    }

    public static Serializer createSerializer(String typeName) {
        if ("json".equals(typeName)) {
            throw new IllegalArgumentException("json is not yet supported");
        }
        if ("pickle".equals(typeName)) {
            return new PickleSerializer();
        }
        throw new IllegalArgumentException("Unknown serializer type: " + typeName);
    }

    /**
     * Mirrors Python's {@code JsonSerializer} in
     * {@code openjiuwen/core/graph/store/serde.py}.
     */
    public static final class JsonSerializer extends Serializer {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        @Override
        public TypedBytes dumpsTyped(Object obj) {
            try {
                return new TypedBytes("json", MAPPER.writeValueAsBytes(obj));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize JSON", e);
            }
        }

        @Override
        public Object loadsTyped(TypedBytes data) {
            if (data == null || !"json".equals(data.type())) {
                return null;
            }
            try {
                return MAPPER.readValue(data.data(), Object.class);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to deserialize JSON", e);
            }
        }
    }

    /**
     * Mirrors Python's {@code PickleSerializer} in
     * {@code openjiuwen/core/graph/store/serde.py}.
     */
    public static final class PickleSerializer extends Serializer {

        @Override
        public TypedBytes dumpsTyped(Object obj) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(obj);
                oos.flush();
                return new TypedBytes("pickle", bos.toByteArray());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to serialize pickle payload", e);
            }
        }

        @Override
        public Object loadsTyped(TypedBytes data) {
            if (data == null || !"pickle".equals(data.type())) {
                return null;
            }
            try (ByteArrayInputStream bis = new ByteArrayInputStream(data.data());
                 ObjectInputStream ois = new ObjectInputStream(bis)) {
                return ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new IllegalStateException("Failed to deserialize pickle payload", e);
            }
        }
    }
}
