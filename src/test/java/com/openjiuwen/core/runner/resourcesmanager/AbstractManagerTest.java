// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 AbstractManager 抽象基类：
 * - _register_resource_provider: 注册Provider、重复ID抛出ValueError
 * - _get_resource: 同步/异步Provider调用、不存在返回null
 * - _unregister_resource_provider: 注销逻辑
 * 
 * 对应Python: test_abstract_manager.py
 */
class AbstractManagerTest {

    /**
     * 用于测试的模拟资源类
     */
    static class MockResource {
        private final String resourceId;
        private final String data;

        public MockResource(String resourceId) {
            this(resourceId, "default_data");
        }

        public MockResource(String resourceId, String data) {
            this.resourceId = resourceId;
            this.data = data;
        }

        public String getResourceId() {
            return resourceId;
        }

        public String getData() {
            return data;
        }
    }

    /**
     * 用于测试的具体管理器实现
     */
    static class ConcreteManager extends AbstractManager<MockResource> {
        // 暴露受保护的方法用于测试
        public void registerProvider(String resourceId, Supplier<MockResource> provider) {
            registerResourceProvider(resourceId, provider);
        }

        public void registerAsyncProvider(String resourceId, Supplier<CompletableFuture<MockResource>> provider) {
            registerAsyncResourceProvider(resourceId, provider);
        }

        public CompletableFuture<MockResource> getResource(String resourceId) {
            return getResourceAsync(resourceId);
        }

        public Supplier<?> unregisterProvider(String resourceId) {
            return unregisterResourceProvider(resourceId);
        }

        public boolean hasProvider(String resourceId) {
            return containsProvider(resourceId);
        }

        public int providerCount() {
            return getProviderCount();
        }
    }

    private ConcreteManager manager;

    @BeforeEach
    void setUp() {
        manager = new ConcreteManager();
    }

    @Nested
    @DisplayName("资源Provider注册测试")
    class RegistrationTest {

        @Test
        @DisplayName("成功注册资源Provider")
        void testRegisterResourceProviderSuccess() {
            Supplier<MockResource> provider = () -> new MockResource("res1", "data1");
            manager.registerProvider("resource_1", provider);

            assertTrue(manager.hasProvider("resource_1"));
            assertEquals(1, manager.providerCount());
        }

        @Test
        @DisplayName("重复注册相同ID的资源抛出IllegalArgumentException")
        void testRegisterDuplicateResourceIdRaisesException() {
            Supplier<MockResource> provider1 = () -> new MockResource("res1");
            Supplier<MockResource> provider2 = () -> new MockResource("res2");

            manager.registerProvider("duplicate_id", provider1);

            assertThrows(IllegalArgumentException.class, () -> 
                manager.registerProvider("duplicate_id", provider2));
        }

        @Test
        @DisplayName("注册多个不同ID的资源")
        void testRegisterMultipleResources() {
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                manager.registerProvider("resource_" + i, () -> new MockResource("res_" + idx));
            }

            assertEquals(5, manager.providerCount());
            for (int i = 0; i < 5; i++) {
                assertTrue(manager.hasProvider("resource_" + i));
            }
        }
    }

    @Nested
    @DisplayName("资源获取测试")
    class GetResourceTest {

        @Test
        @DisplayName("使用同步Provider获取资源")
        void testGetResourceWithSyncProvider() throws ExecutionException, InterruptedException {
            MockResource expected = new MockResource("res1", "sync_data");
            manager.registerProvider("sync_resource", () -> expected);

            CompletableFuture<MockResource> future = manager.getResource("sync_resource");
            MockResource result = future.get();

            assertSame(expected, result);
            assertEquals("sync_data", result.getData());
        }

        @Test
        @DisplayName("使用异步Provider获取资源")
        void testGetResourceWithAsyncProvider() throws ExecutionException, InterruptedException {
            MockResource expected = new MockResource("res2", "async_data");
            manager.registerAsyncProvider("async_resource", 
                () -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(10); // 模拟异步操作
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return expected;
                }));

            CompletableFuture<MockResource> future = manager.getResource("async_resource");
            MockResource result = future.get();

            assertSame(expected, result);
            assertEquals("async_data", result.getData());
        }

        @Test
        @DisplayName("获取不存在的资源返回null")
        void testGetNonexistentResourceReturnsNull() throws ExecutionException, InterruptedException {
            CompletableFuture<MockResource> future = manager.getResource("nonexistent");
            MockResource result = future.get();

            assertNull(result);
        }

        @Test
        @DisplayName("每次获取资源都会调用Provider")
        void testGetResourceProviderCalledEachTime() throws ExecutionException, InterruptedException {
            int[] callCount = {0};
            manager.registerProvider("counting", () -> {
                callCount[0]++;
                return new MockResource("res_" + callCount[0]);
            });

            MockResource result1 = manager.getResource("counting").get();
            MockResource result2 = manager.getResource("counting").get();

            assertEquals(2, callCount[0]);
            assertEquals("res_1", result1.getResourceId());
            assertEquals("res_2", result2.getResourceId());
        }

        @Test
        @DisplayName("Provider返回null的情况")
        void testProviderReturningNull() throws ExecutionException, InterruptedException {
            manager.registerProvider("null_resource", () -> null);

            CompletableFuture<MockResource> future = manager.getResource("null_resource");
            MockResource result = future.get();

            assertNull(result);
        }

        @Test
        @DisplayName("Provider抛出异常的情况")
        void testProviderWithException() {
            manager.registerProvider("failing", () -> {
                throw new RuntimeException("Provider failed");
            });

            CompletableFuture<MockResource> future = manager.getResource("failing");
            
            ExecutionException exception = assertThrows(ExecutionException.class, future::get);
            assertTrue(exception.getCause().getMessage().contains("Provider failed"));
        }
    }

    @Nested
    @DisplayName("资源Provider注销测试")
    class UnregisterTest {

        @Test
        @DisplayName("注销已存在的Provider")
        void testUnregisterExistingProvider() {
            Supplier<MockResource> provider = () -> new MockResource("res1");
            manager.registerProvider("to_remove", provider);
            assertTrue(manager.hasProvider("to_remove"));

            Supplier<?> removed = manager.unregisterProvider("to_remove");

            assertNotNull(removed);
            assertFalse(manager.hasProvider("to_remove"));
        }

        @Test
        @DisplayName("注销不存在的Provider返回null")
        void testUnregisterNonexistentProviderReturnsNull() {
            Supplier<?> removed = manager.unregisterProvider("nonexistent");

            assertNull(removed);
        }

        @Test
        @DisplayName("注销后可以重新注册相同ID")
        void testUnregisterThenReregister() throws ExecutionException, InterruptedException {
            manager.registerProvider("reusable_id", () -> new MockResource("res1", "first"));
            manager.unregisterProvider("reusable_id");

            // 应该可以重新注册
            manager.registerProvider("reusable_id", () -> new MockResource("res1", "second"));

            MockResource result = manager.getResource("reusable_id").get();
            assertEquals("second", result.getData());
        }

        @Test
        @DisplayName("注销后从providers中完全移除")
        void testUnregisterClearsFromProviders() {
            manager.registerProvider("res1", () -> new MockResource("1"));
            manager.registerProvider("res2", () -> new MockResource("2"));
            manager.registerProvider("res3", () -> new MockResource("3"));
            assertEquals(3, manager.providerCount());

            manager.unregisterProvider("res2");

            assertEquals(2, manager.providerCount());
            assertFalse(manager.hasProvider("res2"));
            assertTrue(manager.hasProvider("res1"));
            assertTrue(manager.hasProvider("res3"));
        }
    }

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCasesTest {

        @Test
        @DisplayName("使用工厂模式创建资源")
        void testProviderFactoryPattern() throws ExecutionException, InterruptedException {
            int[] createdCount = {0};
            manager.registerProvider("factory_resource", () -> {
                createdCount[0]++;
                return new MockResource("factory_" + createdCount[0], "type_a");
            });

            MockResource result1 = manager.getResource("factory_resource").get();
            MockResource result2 = manager.getResource("factory_resource").get();

            assertEquals(2, createdCount[0]);
            assertEquals("factory_1", result1.getResourceId());
            assertEquals("factory_2", result2.getResourceId());
        }
    }
}

