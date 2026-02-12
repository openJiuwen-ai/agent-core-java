// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.session.tracer.TracerDecorator;
import org.junit.jupiter.api.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ModelMgr 测试类
 * 
 * 对应Python: test_model_manager.py
 */
@DisplayName("ModelMgr 测试")
class ModelMgrTest {
    
    static class MockModel {
        private final String modelId;
        
        MockModel(String modelId) {
            this.modelId = modelId;
        }
        
        String getModelId() {
            return modelId;
        }
    }
    
    @Nested
    @DisplayName("添加Model")
    class AddModelTest {
        
        @Test
        @DisplayName("成功添加Model")
        void testAddModelSuccess() {
            ModelMgr mgr = new ModelMgr();
            Supplier<MockModel> provider = () -> new MockModel("model_1");
            
            mgr.addModel("model_1", provider);
            
            assertTrue(mgr.containsProvider("model_1"));
        }
        
        @Test
        @DisplayName("添加重复ID抛出IllegalArgumentException")
        void testAddModelDuplicateIdRaisesError() {
            ModelMgr mgr = new ModelMgr();
            Supplier<MockModel> provider1 = () -> new MockModel("model_1");
            Supplier<MockModel> provider2 = () -> new MockModel("model_1");
            
            mgr.addModel("model_1", provider1);
            
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mgr.addModel("model_1", provider2)
            );
            
            assertTrue(exception.getMessage().toLowerCase().contains("already exist"));
        }
        
        @Test
        @DisplayName("添加多个不同ID的Model")
        void testAddMultipleModels() {
            ModelMgr mgr = new ModelMgr();
            
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                mgr.addModel("model_" + i, () -> new MockModel("m_" + idx));
            }
            
            assertEquals(5, mgr.getProviderCount());
        }
    }
    
    @Nested
    @DisplayName("获取Model")
    class GetModelTest {
        
        @Test
        @DisplayName("获取Model（无Session时包装在TracedModel中）")
        void testGetModelWithoutSession() throws ExecutionException, InterruptedException {
            ModelMgr<MockModel> mgr = new ModelMgr<>();
            MockModel model = new MockModel("model_1");
            Supplier<MockModel> provider = () -> model;
            
            mgr.addModel("model_1", provider);
            
            CompletableFuture<TracerDecorator.TracedModel<MockModel>> result = mgr.getModel("model_1", null);
            
            // 返回TracedModel，通过getModel()获取原始对象
            TracerDecorator.TracedModel<MockModel> tracedModel = result.get();
            assertNotNull(tracedModel);
            assertSame(model, tracedModel.getModel());
        }
        
        @Test
        @DisplayName("获取不存在的Model返回null")
        void testGetNonexistentModelReturnsNull() throws ExecutionException, InterruptedException {
            ModelMgr mgr = new ModelMgr();
            
            CompletableFuture<MockModel> result = mgr.getModel("nonexistent", null);
            
            assertNull(result.get());
        }
        
        @Test
        @DisplayName("使用异步Provider获取Model")
        void testGetModelWithAsyncProvider() throws ExecutionException, InterruptedException {
            ModelMgr<MockModel> mgr = new ModelMgr<>();
            MockModel model = new MockModel("async_model");
            Supplier<CompletableFuture<MockModel>> asyncProvider = 
                () -> CompletableFuture.completedFuture(model);
            
            mgr.addAsyncModel("async_model", asyncProvider);
            
            CompletableFuture<TracerDecorator.TracedModel<MockModel>> result = mgr.getModel("async_model", null);
            
            TracerDecorator.TracedModel<MockModel> tracedModel = result.get();
            assertNotNull(tracedModel);
            assertSame(model, tracedModel.getModel());
        }
        
        @Test
        @DisplayName("每次获取Model都调用Provider")
        void testGetModelProviderCalledEachTime() throws ExecutionException, InterruptedException {
            ModelMgr mgr = new ModelMgr();
            int[] callCount = {0};
            
            Supplier<MockModel> countingProvider = () -> {
                callCount[0]++;
                return new MockModel("model_" + callCount[0]);
            };
            
            mgr.addModel("counting_model", countingProvider);
            
            mgr.getModel("counting_model", null).get();
            mgr.getModel("counting_model", null).get();
            
            assertEquals(2, callCount[0]);
        }
    }
    
    @Nested
    @DisplayName("移除Model")
    class RemoveModelTest {
        
        @Test
        @DisplayName("成功移除Model")
        void testRemoveModelSuccess() {
            ModelMgr mgr = new ModelMgr();
            Supplier<MockModel> provider = () -> new MockModel("model_1");
            
            mgr.addModel("model_1", provider);
            assertTrue(mgr.containsProvider("model_1"));
            
            Supplier<?> result = mgr.removeModel("model_1");
            
            assertFalse(mgr.containsProvider("model_1"));
            assertSame(provider, result);
        }
        
        @Test
        @DisplayName("移除不存在的Model返回null")
        void testRemoveNonexistentModelReturnsNull() {
            ModelMgr mgr = new ModelMgr();
            
            Supplier<?> result = mgr.removeModel("nonexistent");
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("移除后可以重新添加相同ID的Model")
        void testRemoveThenReaddModel() {
            ModelMgr mgr = new ModelMgr();
            Supplier<MockModel> provider1 = () -> new MockModel("v1");
            Supplier<MockModel> provider2 = () -> new MockModel("v2");
            
            mgr.addModel("model_1", provider1);
            mgr.removeModel("model_1");
            
            assertDoesNotThrow(() -> mgr.addModel("model_1", provider2));
            assertTrue(mgr.containsProvider("model_1"));
        }
    }
    
    @Nested
    @DisplayName("集成场景")
    class IntegrationTest {
        
        @Test
        @DisplayName("Model完整生命周期")
        void testModelLifecycle() throws ExecutionException, InterruptedException {
            ModelMgr<MockModel> mgr = new ModelMgr<>();
            MockModel model = new MockModel("lifecycle_model");
            
            // 添加
            mgr.addModel("model_1", () -> model);
            assertTrue(mgr.containsProvider("model_1"));
            
            // 获取
            TracerDecorator.TracedModel<MockModel> tracedModel = mgr.getModel("model_1", null).get();
            assertNotNull(tracedModel);
            assertSame(model, tracedModel.getModel());
            
            // 移除
            Supplier<?> removed = mgr.removeModel("model_1");
            assertNotNull(removed);
            assertFalse(mgr.containsProvider("model_1"));
            
            // 再次获取返回null
            tracedModel = mgr.getModel("model_1", null).get();
            assertNull(tracedModel);
        }
    }
}

