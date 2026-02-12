// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentGroupMgr 测试类
 * 
 * 对应Python: test_agent_group_manager.py
 */
@DisplayName("AgentGroupMgr 测试")
class AgentGroupMgrTest {
    
    // Mock类
    static class MockBaseGroup {
        private final String groupId;
        
        MockBaseGroup(String groupId) {
            this.groupId = groupId;
        }
        
        String getGroupId() {
            return groupId;
        }
    }
    
    @Nested
    @DisplayName("添加AgentGroup")
    class AddAgentGroupTest {
        
        @Test
        @DisplayName("成功添加AgentGroup")
        void testAddAgentGroupSuccess() {
            AgentGroupMgr mgr = new AgentGroupMgr();
            Supplier<MockBaseGroup> provider = () -> new MockBaseGroup("group_1");
            
            mgr.addAgentGroup("group_1", provider);
            
            assertTrue(mgr.containsProvider("group_1"));
        }
        
        @Test
        @DisplayName("添加重复ID抛出IllegalArgumentException")
        void testAddAgentGroupDuplicateIdRaisesError() {
            AgentGroupMgr mgr = new AgentGroupMgr();
            Supplier<MockBaseGroup> provider1 = () -> new MockBaseGroup("group_1");
            Supplier<MockBaseGroup> provider2 = () -> new MockBaseGroup("group_1");
            
            mgr.addAgentGroup("group_1", provider1);
            
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mgr.addAgentGroup("group_1", provider2)
            );
            
            assertTrue(exception.getMessage().toLowerCase().contains("already exist"));
        }
        
        @Test
        @DisplayName("添加多个不同ID的AgentGroup")
        void testAddMultipleAgentGroups() {
            AgentGroupMgr mgr = new AgentGroupMgr();
            
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                mgr.addAgentGroup("group_" + i, () -> new MockBaseGroup("g_" + idx));
            }
            
            assertEquals(5, mgr.getProviderCount());
        }
    }
    
    @Nested
    @DisplayName("获取AgentGroup")
    class GetAgentGroupTest {
        
        @Test
        @DisplayName("使用同步Provider获取AgentGroup")
        void testGetAgentGroupWithSyncProvider() throws ExecutionException, InterruptedException {
            AgentGroupMgr mgr = new AgentGroupMgr();
            MockBaseGroup expectedGroup = new MockBaseGroup("group_1");
            Supplier<MockBaseGroup> provider = () -> expectedGroup;
            
            mgr.addAgentGroup("group_1", provider);
            
            CompletableFuture<MockBaseGroup> result = mgr.getAgentGroup("group_1");
            
            assertSame(expectedGroup, result.get());
        }
        
        @Test
        @DisplayName("使用异步Provider获取AgentGroup")
        void testGetAgentGroupWithAsyncProvider() throws ExecutionException, InterruptedException {
            AgentGroupMgr mgr = new AgentGroupMgr();
            MockBaseGroup expectedGroup = new MockBaseGroup("group_1");
            Supplier<CompletableFuture<MockBaseGroup>> asyncProvider = 
                () -> CompletableFuture.completedFuture(expectedGroup);
            
            mgr.addAsyncAgentGroup("group_1", asyncProvider);
            
            CompletableFuture<MockBaseGroup> result = mgr.getAgentGroup("group_1");
            
            assertSame(expectedGroup, result.get());
        }
        
        @Test
        @DisplayName("获取不存在的AgentGroup返回null")
        void testGetNonexistentAgentGroupReturnsNull() throws ExecutionException, InterruptedException {
            AgentGroupMgr mgr = new AgentGroupMgr();
            
            CompletableFuture<MockBaseGroup> result = mgr.getAgentGroup("nonexistent");
            
            assertNull(result.get());
        }
        
        @Test
        @DisplayName("每次获取AgentGroup都调用Provider")
        void testGetAgentGroupProviderCalledEachTime() throws ExecutionException, InterruptedException {
            AgentGroupMgr mgr = new AgentGroupMgr();
            int[] callCount = {0};
            
            Supplier<MockBaseGroup> countingProvider = () -> {
                callCount[0]++;
                return new MockBaseGroup("group_" + callCount[0]);
            };
            
            mgr.addAgentGroup("counting_group", countingProvider);
            
            mgr.getAgentGroup("counting_group").get();
            mgr.getAgentGroup("counting_group").get();
            
            assertEquals(2, callCount[0]);
        }
    }
    
    @Nested
    @DisplayName("移除AgentGroup")
    class RemoveAgentGroupTest {
        
        @Test
        @DisplayName("成功移除AgentGroup")
        void testRemoveAgentGroupSuccess() {
            AgentGroupMgr mgr = new AgentGroupMgr();
            Supplier<MockBaseGroup> provider = () -> new MockBaseGroup("group_1");
            
            mgr.addAgentGroup("group_1", provider);
            assertTrue(mgr.containsProvider("group_1"));
            
            Supplier<?> result = mgr.removeAgentGroup("group_1");
            
            assertFalse(mgr.containsProvider("group_1"));
            assertSame(provider, result);
        }
        
        @Test
        @DisplayName("移除不存在的AgentGroup返回null")
        void testRemoveNonexistentAgentGroupReturnsNull() {
            AgentGroupMgr mgr = new AgentGroupMgr();
            
            Supplier<?> result = mgr.removeAgentGroup("nonexistent");
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("移除后可以重新添加相同ID的AgentGroup")
        void testRemoveThenReaddAgentGroup() {
            AgentGroupMgr mgr = new AgentGroupMgr();
            Supplier<MockBaseGroup> provider1 = () -> new MockBaseGroup("v1");
            Supplier<MockBaseGroup> provider2 = () -> new MockBaseGroup("v2");
            
            mgr.addAgentGroup("group_1", provider1);
            mgr.removeAgentGroup("group_1");
            
            // 应该可以重新添加
            assertDoesNotThrow(() -> mgr.addAgentGroup("group_1", provider2));
            assertTrue(mgr.containsProvider("group_1"));
        }
    }
    
    @Nested
    @DisplayName("集成场景")
    class IntegrationTest {
        
        @Test
        @DisplayName("AgentGroup完整生命周期")
        void testAgentGroupLifecycle() throws ExecutionException, InterruptedException {
            AgentGroupMgr<MockBaseGroup> mgr = new AgentGroupMgr<>();
            MockBaseGroup group = new MockBaseGroup("lifecycle_group");
            
            // 添加
            mgr.addAgentGroup("group_1", () -> group);
            assertTrue(mgr.containsProvider("group_1"));
            
            // 获取
            MockBaseGroup result = mgr.getAgentGroup("group_1").get();
            assertSame(group, result);
            
            // 移除
            Supplier<?> removed = mgr.removeAgentGroup("group_1");
            assertNotNull(removed);
            assertFalse(mgr.containsProvider("group_1"));
            
            // 再次获取返回null
            result = mgr.getAgentGroup("group_1").get();
            assertNull(result);
        }
        
        @Test
        @DisplayName("Provider抛出异常时异常被传播")
        void testProviderExceptionPropagates() {
            AgentGroupMgr mgr = new AgentGroupMgr();
            
            Supplier<MockBaseGroup> failingProvider = () -> {
                throw new RuntimeException("Provider initialization failed");
            };
            
            mgr.addAgentGroup("failing_group", failingProvider);
            
            ExecutionException exception = assertThrows(
                ExecutionException.class,
                () -> mgr.getAgentGroup("failing_group").get()
            );
            
            assertTrue(exception.getCause().getMessage().contains("Provider initialization failed"));
        }
    }
}

