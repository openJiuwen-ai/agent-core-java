/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.spi.store.object;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ObjectStorageFactory SPI registration.
 * <p>
 * No built-in providers exist; all implementations are registered by Service adapters.
 */
class ObjectStorageFactorySpiTest {

    // ========== No built-in providers ==========

    @Test
    @DisplayName("No built-in object storage providers are registered")
    void noBuiltInProviders() {
        assertFalse(ObjectStorageFactory.hasProvider("obs"));
        assertFalse(ObjectStorageFactory.hasProvider("s3"));
        assertFalse(ObjectStorageFactory.hasProvider("minio"));
    }

    @Test
    @DisplayName("create() with unregistered type throws IllegalArgumentException")
    void createUnregisteredTypeThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ObjectStorageFactory.create("obs", Map.of()));
        assertTrue(ex.getMessage().contains("obs"));
    }

    // ========== Manual register() ==========

    @Test
    @DisplayName("register() allows adding a custom object storage provider")
    void registerCustomProvider() {
        ObjectStorageFactory.register("mock_obs", new ObjectStorageProvider() {
            @Override
            public String typeName() { return "mock_obs"; }
            @Override
            public BaseObjectStorageClient create(Map<String, Object> conf) { return new MockObjectStorageClient(); }
        });
        assertTrue(ObjectStorageFactory.hasProvider("mock_obs"));

        BaseObjectStorageClient client = ObjectStorageFactory.create("mock_obs", Map.of());
        assertNotNull(client);
        assertInstanceOf(MockObjectStorageClient.class, client);
    }

    @Test
    @DisplayName("register() can override an existing provider")
    void registerOverridesExisting() {
        ObjectStorageFactory.register("mock_s3", new ObjectStorageProvider() {
            @Override
            public String typeName() { return "mock_s3"; }
            @Override
            public BaseObjectStorageClient create(Map<String, Object> conf) { return new MockObjectStorageClient(); }
        });
        ObjectStorageFactory.register("mock_s3", new ObjectStorageProvider() {
            @Override
            public String typeName() { return "mock_s3"; }
            @Override
            public BaseObjectStorageClient create(Map<String, Object> conf) { return new AnotherMockObjectStorageClient(); }
        });

        BaseObjectStorageClient client = ObjectStorageFactory.create("mock_s3", Map.of());
        assertInstanceOf(AnotherMockObjectStorageClient.class, client);
    }

    // ========== hasProvider() ==========

    @Test
    @DisplayName("hasProvider() returns false for null")
    void hasProviderNull() {
        assertFalse(ObjectStorageFactory.hasProvider(null));
    }

    @Test
    @DisplayName("hasProvider() returns false for unknown type")
    void hasProviderUnknown() {
        assertFalse(ObjectStorageFactory.hasProvider("nonexistent"));
    }

    // ========== Additional test cases ==========

    @Test
    @DisplayName("create() with null conf defaults to empty map")
    void createWithNullConf() {
        ObjectStorageFactory.register("mock_null_conf", new ObjectStorageProvider() {
            @Override
            public String typeName() { return "mock_null_conf"; }
            @Override
            public BaseObjectStorageClient create(Map<String, Object> conf) {
                assertNotNull(conf);
                return new MockObjectStorageClient();
            }
        });

        BaseObjectStorageClient client = ObjectStorageFactory.create("mock_null_conf", null);
        assertNotNull(client);
    }

    @Test
    @DisplayName("create() with empty string type throws IllegalArgumentException")
    void createWithEmptyTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ObjectStorageFactory.create("", Map.of()));
    }

    @Test
    @DisplayName("Multiple create() calls return different instances")
    void createReturnsDifferentInstances() {
        ObjectStorageFactory.register("mock_multi", new ObjectStorageProvider() {
            @Override
            public String typeName() { return "mock_multi"; }
            @Override
            public BaseObjectStorageClient create(Map<String, Object> conf) { return new MockObjectStorageClient(); }
        });

        BaseObjectStorageClient client1 = ObjectStorageFactory.create("mock_multi", Map.of());
        BaseObjectStorageClient client2 = ObjectStorageFactory.create("mock_multi", Map.of());
        assertNotSame(client1, client2);
    }

    @Test
    @DisplayName("register() provider that reads conf")
    void registerProviderThatReadsConf() {
        ObjectStorageFactory.register("mock_conf_aware", new ObjectStorageProvider() {
            @Override
            public String typeName() { return "mock_conf_aware"; }
            @Override
            public BaseObjectStorageClient create(Map<String, Object> conf) {
                assertNotNull(conf);
                return new MockObjectStorageClient();
            }
        });

        BaseObjectStorageClient client = ObjectStorageFactory.create("mock_conf_aware",
                Map.of("endpoint", "https://obs.example.com", "bucket", "test-bucket"));
        assertNotNull(client);
    }

    @Test
    @DisplayName("hasProvider() returns true after register()")
    void hasProviderReturnsTrueAfterRegister() {
        String type = "mock_dynamic_obs";
        assertFalse(ObjectStorageFactory.hasProvider(type));

        ObjectStorageFactory.register(type, new ObjectStorageProvider() {
            @Override
            public String typeName() { return type; }
            @Override
            public BaseObjectStorageClient create(Map<String, Object> conf) { return new MockObjectStorageClient(); }
        });

        assertTrue(ObjectStorageFactory.hasProvider(type));
    }

    @Test
    @DisplayName("MockObjectStorageClient upload/download lifecycle")
    void mockClientLifecycle() throws Exception {
        MockObjectStorageClient client = new MockObjectStorageClient();
        assertTrue(client.createBucket("test-bucket", "us-east-1"));
        assertTrue(client.uploadFile("test-bucket", "test-key", java.nio.file.Path.of("test.txt")));
        assertTrue(client.downloadFile("test-bucket", "test-key", java.nio.file.Path.of("downloaded.txt")));
        assertTrue(client.deleteObject("test-bucket", "test-key"));
        assertTrue(client.deleteBucket("test-bucket"));
    }

    // ========== Mock implementations ==========

    static class MockObjectStorageClient extends BaseObjectStorageClient {
        @Override
        public boolean uploadFile(String bucketName, String objectName, java.nio.file.Path filePath) { return true; }
        @Override
        public boolean downloadFile(String bucketName, String objectName, java.nio.file.Path filePath) { return true; }
        @Override
        public boolean deleteObject(String bucketName, String objectName) { return true; }
        @Override
        public boolean createBucket(String bucketName, String location) { return true; }
        @Override
        public boolean deleteBucket(String bucketName) { return true; }
        @Override
        public java.util.List<java.util.Map<String, Object>> listObjects(String bucketName, String objectPrefix, int maxObjects) { return java.util.List.of(); }
    }

    static class AnotherMockObjectStorageClient extends BaseObjectStorageClient {
        @Override
        public boolean uploadFile(String bucketName, String objectName, java.nio.file.Path filePath) { return true; }
        @Override
        public boolean downloadFile(String bucketName, String objectName, java.nio.file.Path filePath) { return true; }
        @Override
        public boolean deleteObject(String bucketName, String objectName) { return true; }
        @Override
        public boolean createBucket(String bucketName, String location) { return true; }
        @Override
        public boolean deleteBucket(String bucketName) { return true; }
        @Override
        public java.util.List<java.util.Map<String, Object>> listObjects(String bucketName, String objectPrefix, int maxObjects) { return java.util.List.of(); }
    }
}
