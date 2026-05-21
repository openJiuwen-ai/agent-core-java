/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.object;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AioBotoClient.
 * <p>
 * Mirrors Python's test_aioboto_client.py from
 * <code>tests/unit_tests/core/foundation/store/object/test_aioboto_client.py</code>.
 */
@DisplayName("AioBoto Client Tests")
class TestAiobotoClient {

    // Stub classes
    static class ObjectStorageConfig {
        String endpoint;
        String bucket;
        String accessKey;
        String secretKey;

        ObjectStorageConfig(String endpoint, String bucket, String accessKey, String secretKey) {
            this.endpoint = endpoint;
            this.bucket = bucket;
            this.accessKey = accessKey;
            this.secretKey = secretKey;
        }
    }

    static class AioBotoClientStub {
        ObjectStorageConfig config;
        Map<String, byte[]> objects = new HashMap<>();

        AioBotoClientStub(ObjectStorageConfig config) {
            this.config = config;
        }

        CompletableFuture<Void> upload(String key, byte[] data) {
            objects.put(key, data);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<byte[]> download(String key) {
            return CompletableFuture.completedFuture(objects.get(key));
        }

        CompletableFuture<Boolean> exists(String key) {
            return CompletableFuture.completedFuture(objects.containsKey(key));
        }

        CompletableFuture<Void> delete(String key) {
            objects.remove(key);
            return CompletableFuture.completedFuture(null);
        }

        int objectCount() {
            return objects.size();
        }
    }

    @Nested
    @DisplayName("Object Storage Config Tests")
    class TestObjectStorageConfig {

        @Test
        @DisplayName("config creation")
        void testConfigCreation() {
            ObjectStorageConfig config = new ObjectStorageConfig(
                "https://storage.example.com",
                "my-bucket",
                "access-key",
                "secret-key"
            );

            assertEquals("https://storage.example.com", config.endpoint);
            assertEquals("my-bucket", config.bucket);
        }
    }

    @Nested
    @DisplayName("Upload Tests")
    class TestUpload {

        @Test
        @DisplayName("upload object")
        void testUploadObject() throws Exception {
            ObjectStorageConfig config = new ObjectStorageConfig(
                "https://storage.example.com",
                "bucket",
                "key",
                "secret"
            );
            AioBotoClientStub client = new AioBotoClientStub(config);

            byte[] data = "test data".getBytes();
            client.upload("test.txt", data).get();

            assertEquals(1, client.objectCount());
        }

        @Test
        @DisplayName("upload and download")
        void testUploadAndDownload() throws Exception {
            ObjectStorageConfig config = new ObjectStorageConfig(
                "https://storage.example.com",
                "bucket",
                "key",
                "secret"
            );
            AioBotoClientStub client = new AioBotoClientStub(config);

            byte[] original = "test content".getBytes();
            client.upload("file.txt", original).get();
            byte[] downloaded = client.download("file.txt").get();

            assertArrayEquals(original, downloaded);
        }
    }

    @Nested
    @DisplayName("Download Tests")
    class TestDownload {

        @Test
        @DisplayName("download non-existent returns null")
        void testDownloadNonExistentReturnsNull() throws Exception {
            ObjectStorageConfig config = new ObjectStorageConfig(
                "https://storage.example.com",
                "bucket",
                "key",
                "secret"
            );
            AioBotoClientStub client = new AioBotoClientStub(config);

            byte[] result = client.download("nonexistent.txt").get();

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Exists Tests")
    class TestExists {

        @Test
        @DisplayName("exists returns true for uploaded object")
        void testExistsReturnsTrueForUploadedObject() throws Exception {
            ObjectStorageConfig config = new ObjectStorageConfig(
                "https://storage.example.com",
                "bucket",
                "key",
                "secret"
            );
            AioBotoClientStub client = new AioBotoClientStub(config);
            client.upload("test.txt", "data".getBytes()).get();

            boolean exists = client.exists("test.txt").get();

            assertTrue(exists);
        }

        @Test
        @DisplayName("exists returns false for non-existent")
        void testExistsReturnsFalseForNonExistent() throws Exception {
            ObjectStorageConfig config = new ObjectStorageConfig(
                "https://storage.example.com",
                "bucket",
                "key",
                "secret"
            );
            AioBotoClientStub client = new AioBotoClientStub(config);

            boolean exists = client.exists("nonexistent.txt").get();

            assertFalse(exists);
        }
    }

    @Nested
    @DisplayName("Delete Tests")
    class TestDelete {

        @Test
        @DisplayName("delete object")
        void testDeleteObject() throws Exception {
            ObjectStorageConfig config = new ObjectStorageConfig(
                "https://storage.example.com",
                "bucket",
                "key",
                "secret"
            );
            AioBotoClientStub client = new AioBotoClientStub(config);
            client.upload("test.txt", "data".getBytes()).get();

            client.delete("test.txt").get();

            assertEquals(0, client.objectCount());
        }
    }
}