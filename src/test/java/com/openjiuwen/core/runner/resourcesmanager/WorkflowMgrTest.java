// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.session.tracer.TracerDecorator;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkflowMgr 测试类
 * 
 * 对应Python: test_workflow_manager.py
 */
@DisplayName("WorkflowMgr 测试")
class WorkflowMgrTest {
    
    static class MockWorkflow {
        private final String workflowId;
        
        MockWorkflow(String workflowId) {
            this.workflowId = workflowId;
        }
        
        String getWorkflowId() {
            return workflowId;
        }
    }
    
    @Nested
    @DisplayName("添加Workflow")
    class AddWorkflowTest {
        
        @Test
        @DisplayName("成功添加单个Workflow")
        void testAddWorkflowSuccess() {
            WorkflowMgr mgr = new WorkflowMgr();
            Supplier<MockWorkflow> provider = () -> new MockWorkflow("wf_1");
            
            mgr.addWorkflow("workflow_1", provider);
            
            assertTrue(mgr.containsProvider("workflow_1"));
        }
        
        @Test
        @DisplayName("添加重复ID抛出异常")
        void testAddWorkflowDuplicateIdRaisesError() {
            WorkflowMgr mgr = new WorkflowMgr();
            Supplier<MockWorkflow> provider1 = () -> new MockWorkflow("wf_1");
            Supplier<MockWorkflow> provider2 = () -> new MockWorkflow("wf_2");
            
            mgr.addWorkflow("workflow_1", provider1);
            
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mgr.addWorkflow("workflow_1", provider2)
            );
            
            assertTrue(exception.getMessage().toLowerCase().contains("already exist"));
        }
        
        @Test
        @DisplayName("批量添加Workflow")
        void testAddWorkflowsBatch() {
            WorkflowMgr mgr = new WorkflowMgr();
            
            List<WorkflowMgr.WorkflowEntry> workflows = Arrays.asList(
                new WorkflowMgr.WorkflowEntry("wf_1", () -> new MockWorkflow("1")),
                new WorkflowMgr.WorkflowEntry("wf_2", () -> new MockWorkflow("2")),
                new WorkflowMgr.WorkflowEntry("wf_3", () -> new MockWorkflow("3"))
            );
            
            mgr.addWorkflows(workflows);
            
            assertTrue(mgr.containsProvider("wf_1"));
            assertTrue(mgr.containsProvider("wf_2"));
            assertTrue(mgr.containsProvider("wf_3"));
        }
        
        @Test
        @DisplayName("添加空列表不执行任何操作")
        void testAddWorkflowsEmptyListNoOp() {
            WorkflowMgr mgr = new WorkflowMgr();
            
            mgr.addWorkflows(Arrays.asList());
            
            assertEquals(0, mgr.getProviderCount());
        }
        
        @Test
        @DisplayName("添加null不执行任何操作")
        void testAddWorkflowsNullNoOp() {
            WorkflowMgr mgr = new WorkflowMgr();
            
            mgr.addWorkflows(null);
            
            assertEquals(0, mgr.getProviderCount());
        }
    }
    
    @Nested
    @DisplayName("获取Workflow")
    class GetWorkflowTest {
        
        @Test
        @DisplayName("获取Workflow（无Session时包装在TracedWorkflow中）")
        void testGetWorkflowWithoutSession() throws ExecutionException, InterruptedException {
            WorkflowMgr<MockWorkflow> mgr = new WorkflowMgr<>();
            MockWorkflow workflow = new MockWorkflow("wf_1");
            Supplier<MockWorkflow> provider = () -> workflow;
            
            mgr.addWorkflow("workflow_1", provider);
            
            CompletableFuture<TracerDecorator.TracedWorkflow<MockWorkflow>> result = mgr.getWorkflow("workflow_1", null);
            
            // 返回TracedWorkflow，通过getWorkflow()获取原始对象
            TracerDecorator.TracedWorkflow<MockWorkflow> tracedWorkflow = result.get();
            assertNotNull(tracedWorkflow);
            assertSame(workflow, tracedWorkflow.getWorkflow());
        }
        
        @Test
        @DisplayName("获取不存在的Workflow返回null")
        void testGetNonexistentWorkflow() throws ExecutionException, InterruptedException {
            WorkflowMgr mgr = new WorkflowMgr();
            
            CompletableFuture<MockWorkflow> result = mgr.getWorkflow("nonexistent", null);
            
            assertNull(result.get());
        }
        
        @Test
        @DisplayName("使用异步Provider获取Workflow")
        void testGetWorkflowWithAsyncProvider() throws ExecutionException, InterruptedException {
            WorkflowMgr<MockWorkflow> mgr = new WorkflowMgr<>();
            MockWorkflow workflow = new MockWorkflow("async_wf");
            Supplier<CompletableFuture<MockWorkflow>> asyncProvider = 
                () -> CompletableFuture.completedFuture(workflow);
            
            mgr.addAsyncWorkflow("async_workflow", asyncProvider);
            
            CompletableFuture<TracerDecorator.TracedWorkflow<MockWorkflow>> result = mgr.getWorkflow("async_workflow", null);
            
            TracerDecorator.TracedWorkflow<MockWorkflow> tracedWorkflow = result.get();
            assertNotNull(tracedWorkflow);
            assertSame(workflow, tracedWorkflow.getWorkflow());
        }
    }
    
    @Nested
    @DisplayName("移除Workflow")
    class RemoveWorkflowTest {
        
        @Test
        @DisplayName("成功移除Workflow")
        void testRemoveWorkflowSuccess() {
            WorkflowMgr mgr = new WorkflowMgr();
            Supplier<MockWorkflow> provider = () -> new MockWorkflow("wf_1");
            
            mgr.addWorkflow("workflow_1", provider);
            assertTrue(mgr.containsProvider("workflow_1"));
            
            Supplier<?> result = mgr.removeWorkflow("workflow_1");
            
            assertFalse(mgr.containsProvider("workflow_1"));
            assertSame(provider, result);
        }
        
        @Test
        @DisplayName("移除不存在的Workflow返回null")
        void testRemoveNonexistentWorkflowReturnsNull() {
            WorkflowMgr mgr = new WorkflowMgr();
            
            Supplier<?> result = mgr.removeWorkflow("nonexistent");
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("移除后可以重新添加相同ID的Workflow")
        void testRemoveThenReaddWorkflow() {
            WorkflowMgr mgr = new WorkflowMgr();
            Supplier<MockWorkflow> provider1 = () -> new MockWorkflow("wf_v1");
            Supplier<MockWorkflow> provider2 = () -> new MockWorkflow("wf_v2");
            
            mgr.addWorkflow("workflow_1", provider1);
            mgr.removeWorkflow("workflow_1");
            
            assertDoesNotThrow(() -> mgr.addWorkflow("workflow_1", provider2));
            assertTrue(mgr.containsProvider("workflow_1"));
        }
    }
    
    @Nested
    @DisplayName("集成场景")
    class IntegrationTest {
        
        @Test
        @DisplayName("Workflow完整生命周期")
        void testWorkflowLifecycle() throws ExecutionException, InterruptedException {
            WorkflowMgr<MockWorkflow> mgr = new WorkflowMgr<>();
            MockWorkflow workflow = new MockWorkflow("lifecycle_wf");
            
            // 添加
            mgr.addWorkflow("wf_1", () -> workflow);
            assertTrue(mgr.containsProvider("wf_1"));
            
            // 获取
            TracerDecorator.TracedWorkflow<MockWorkflow> tracedWorkflow = mgr.getWorkflow("wf_1", null).get();
            assertNotNull(tracedWorkflow);
            assertSame(workflow, tracedWorkflow.getWorkflow());
            
            // 移除
            Supplier<?> removed = mgr.removeWorkflow("wf_1");
            assertNotNull(removed);
            assertFalse(mgr.containsProvider("wf_1"));
            
            // 再次获取返回null
            tracedWorkflow = mgr.getWorkflow("wf_1", null).get();
            assertNull(tracedWorkflow);
        }
    }
}

