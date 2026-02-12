// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteAgent;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentMgr 测试类
 * 
 * 对应Python: test_agent_manager.py
 * 
 * 注意：默认RunnerConfig为非分布式模式，测试不需要mock静态方法
 */
@DisplayName("AgentMgr 测试")
class AgentMgrTest {
    
    static class MockBaseAgent {
        private final String agentId;
        
        MockBaseAgent(String agentId) {
            this.agentId = agentId;
        }
        
        String getAgentId() {
            return agentId;
        }
    }
    
    static class MockRemoteAgent extends RemoteAgent {
        MockRemoteAgent(String agentId) {
            super(agentId);
        }
    }
    
    @BeforeEach
    void setUp() {
        // 确保使用默认的非分布式配置
        RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG);
    }
    
    @Nested
    @DisplayName("添加Agent - 非分布式模式")
    class AddAgentNonDistributedTest {
        
        @Test
        @DisplayName("添加本地Agent Provider")
        void testAddLocalAgentWithProvider() {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            Supplier<MockBaseAgent> provider = () -> new MockBaseAgent("agent_1");
            
            mgr.addAgent("agent_1", provider);
            
            assertTrue(mgr.containsProvider("agent_1"));
        }
        
        @Test
        @DisplayName("添加RemoteAgent")
        void testAddRemoteAgent() {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            MockRemoteAgent remoteAgent = new MockRemoteAgent("agent_1");
            
            mgr.addRemoteAgent("agent_1", remoteAgent);
            
            assertTrue(mgr.hasRemoteAgent("agent_1"));
        }
        
        @Test
        @DisplayName("添加重复Agent ID抛出异常")
        void testAddDuplicateAgentIdRaisesError() {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            Supplier<MockBaseAgent> provider1 = () -> new MockBaseAgent("agent_1");
            Supplier<MockBaseAgent> provider2 = () -> new MockBaseAgent("agent_1");
            
            mgr.addAgent("agent_1", provider1);
            
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mgr.addAgent("agent_1", provider2)
            );
            
            assertTrue(exception.getMessage().toLowerCase().contains("already"));
        }
        
        @Test
        @DisplayName("添加RemoteAgent后再添加相同ID的Agent抛出异常")
        void testAddAgentAfterRemoteAgentRaisesError() {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            MockRemoteAgent remoteAgent = new MockRemoteAgent("agent_1");
            Supplier<MockBaseAgent> provider = () -> new MockBaseAgent("agent_1");
            
            mgr.addRemoteAgent("agent_1", remoteAgent);
            
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mgr.addAgent("agent_1", provider)
            );
            
            assertTrue(exception.getMessage().toLowerCase().contains("already"));
        }
    }
    
    @Nested
    @DisplayName("获取Agent")
    class GetAgentTest {
        
        @Test
        @DisplayName("获取本地Agent")
        void testGetLocalAgent() throws ExecutionException, InterruptedException {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            MockBaseAgent expectedAgent = new MockBaseAgent("agent_1");
            Supplier<MockBaseAgent> provider = () -> expectedAgent;
            
            mgr.addAgent("agent_1", provider);
            
            CompletableFuture<Object> result = mgr.getAgent("agent_1");
            
            assertSame(expectedAgent, result.get());
        }
        
        @Test
        @DisplayName("获取RemoteAgent直接返回缓存实例")
        void testGetRemoteAgent() throws ExecutionException, InterruptedException {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            MockRemoteAgent remoteAgent = new MockRemoteAgent("remote_1");
            mgr.addRemoteAgent("remote_1", remoteAgent);
            
            CompletableFuture<Object> result = mgr.getAgent("remote_1");
            
            assertSame(remoteAgent, result.get());
        }
        
        @Test
        @DisplayName("获取不存在的Agent返回null")
        void testGetNonexistentAgentReturnsNull() throws ExecutionException, InterruptedException {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            
            CompletableFuture<Object> result = mgr.getAgent("nonexistent");
            
            assertNull(result.get());
        }
        
        @Test
        @DisplayName("获取Agent时优先返回RemoteAgent")
        void testGetAgentPrefersRemoteOverProvider() throws ExecutionException, InterruptedException {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            MockBaseAgent localAgent = new MockBaseAgent("agent_1");
            MockRemoteAgent remoteAgent = new MockRemoteAgent("agent_1_remote");
            
            // 添加Provider和RemoteAgent（不同ID来避免冲突）
            mgr.addAgent("agent_1_local", () -> localAgent);
            mgr.addRemoteAgent("agent_1", remoteAgent);
            
            CompletableFuture<Object> result = mgr.getAgent("agent_1");
            
            // 应该返回RemoteAgent
            assertSame(remoteAgent, result.get());
        }
        
        @Test
        @DisplayName("使用异步Provider获取Agent")
        void testGetAgentWithAsyncProvider() throws ExecutionException, InterruptedException {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            MockBaseAgent expectedAgent = new MockBaseAgent("async_agent");
            Supplier<CompletableFuture<MockBaseAgent>> asyncProvider = 
                () -> CompletableFuture.completedFuture(expectedAgent);
            
            mgr.addAsyncAgent("async_agent", asyncProvider);
            
            CompletableFuture<Object> result = mgr.getAgent("async_agent");
            
            assertSame(expectedAgent, result.get());
        }
    }
    
    @Nested
    @DisplayName("移除Agent")
    class RemoveAgentTest {
        
        @Test
        @DisplayName("移除本地Agent")
        void testRemoveLocalAgent() {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            Supplier<MockBaseAgent> provider = () -> new MockBaseAgent("agent_1");
            
            mgr.addAgent("agent_1", provider);
            assertTrue(mgr.containsProvider("agent_1"));
            
            Supplier<?> result = mgr.removeAgent("agent_1");
            
            assertFalse(mgr.containsProvider("agent_1"));
            assertSame(provider, result);
        }
        
        @Test
        @DisplayName("移除不存在的Agent返回null")
        void testRemoveNonexistentAgentReturnsNull() {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            
            Supplier<?> result = mgr.removeAgent("nonexistent");
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("移除RemoteAgent")
        void testRemoveRemoteAgent() {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            MockRemoteAgent remoteAgent = new MockRemoteAgent("remote_1");
            mgr.addRemoteAgent("remote_1", remoteAgent);
            
            mgr.removeAgent("remote_1");
            
            assertFalse(mgr.hasRemoteAgent("remote_1"));
        }
    }
    
    @Nested
    @DisplayName("边界情况")
    class EdgeCasesTest {
        
        @Test
        @DisplayName("每次get_agent都调用Provider")
        void testProviderCalledOnEachGet() throws ExecutionException, InterruptedException {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            int[] callCount = {0};
            
            Supplier<MockBaseAgent> countingProvider = () -> {
                callCount[0]++;
                return new MockBaseAgent("agent_" + callCount[0]);
            };
            
            mgr.addAgent("agent_1", countingProvider);
            
            mgr.getAgent("agent_1").get();
            mgr.getAgent("agent_1").get();
            
            assertEquals(2, callCount[0]);
        }
        
        @Test
        @DisplayName("多个不同ID的Agent可以共存")
        void testMultipleAgentsCanCoexist() {
            AgentMgr<MockBaseAgent> mgr = new AgentMgr<>();
            
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                mgr.addAgent("agent_" + i, () -> new MockBaseAgent("agent_" + idx));
            }
            
            assertEquals(5, mgr.getProviderCount());
            
            for (int i = 0; i < 5; i++) {
                assertTrue(mgr.containsProvider("agent_" + i));
            }
        }
    }
}
