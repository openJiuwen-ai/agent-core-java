/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Serialization utilities.
 * <p>
 * Mirrors Python's test_serialization.py from
 * <code>tests/unit_tests/core/common/test_serialization.py</code>.
 */
@DisplayName("Serialization Tests")
class TestSerialization {

    // Stub classes
    static class SerializableObject implements Serializable {
        private static final long serialVersionUID = 1L;
        
        String name;
        int value;
        Map<String, String> metadata = new HashMap<>();

        SerializableObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        void addMetadata(String key, String val) {
            metadata.put(key, val);
        }
    }

    @Nested
    @DisplayName("Object Serialization Tests")
    class TestObjectSerialization {

        @Test
        @DisplayName("serialize object to bytes")
        void testSerializeObjectToBytes() throws Exception {
            SerializableObject obj = new SerializableObject("test", 42);
            obj.addMetadata("key1", "value1");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
            oos.close();

            byte[] bytes = baos.toByteArray();
            assertNotNull(bytes);
            assertTrue(bytes.length > 0);
        }

        @Test
        @DisplayName("deserialize object from bytes")
        void testDeserializeObjectFromBytes() throws Exception {
            SerializableObject original = new SerializableObject("test", 42);
            original.addMetadata("key1", "value1");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(original);
            oos.close();

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            SerializableObject deserialized = (SerializableObject) ois.readObject();
            ois.close();

            assertEquals(original.name, deserialized.name);
            assertEquals(original.value, deserialized.value);
            assertEquals(original.metadata, deserialized.metadata);
        }

        @Test
        @DisplayName("serialize and deserialize preserves data")
        void testSerializeDeserializePreservesData() throws Exception {
            SerializableObject obj = new SerializableObject("complex", 100);
            obj.addMetadata("a", "b");
            obj.addMetadata("c", "d");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
            oos.close();

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            SerializableObject result = (SerializableObject) ois.readObject();
            ois.close();

            assertEquals("complex", result.name);
            assertEquals(100, result.value);
            assertEquals(2, result.metadata.size());
        }
    }

    @Nested
    @DisplayName("Serializable Object Tests")
    class TestSerializableObject {

        @Test
        @DisplayName("serializable object creation")
        void testSerializableObjectCreation() {
            SerializableObject obj = new SerializableObject("test", 42);

            assertEquals("test", obj.name);
            assertEquals(42, obj.value);
        }

        @Test
        @DisplayName("add metadata")
        void testAddMetadata() {
            SerializableObject obj = new SerializableObject("test", 42);
            obj.addMetadata("key1", "value1");
            obj.addMetadata("key2", "value2");

            assertEquals(2, obj.metadata.size());
            assertEquals("value1", obj.metadata.get("key1"));
            assertEquals("value2", obj.metadata.get("key2"));
        }
    }
}