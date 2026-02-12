// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SysOperationMgr 测试类
 * 
 * 对应Python: test_sys_operation_manager.py
 */
@DisplayName("SysOperationMgr 测试")
class SysOperationMgrTest {
    
    static class MockSysOperation {
        private final String opId;
        
        MockSysOperation(String opId) {
            this.opId = opId;
        }
        
        String getOpId() {
            return opId;
        }
    }
    
    @Nested
    @DisplayName("添加SysOperation")
    class AddSysOperationTest {
        
        @Test
        @DisplayName("成功添加SysOperation")
        void testAddSysOperationSuccess() {
            SysOperationMgr mgr = new SysOperationMgr();
            MockSysOperation sysOp = new MockSysOperation("sys_op_1");
            
            mgr.addSysOperation("sys_op_1", sysOp);
            
            assertNotNull(mgr.getSysOperation("sys_op_1"));
        }
        
        @Test
        @DisplayName("sys_operation_id为null时抛出SYS_OPERATION_MANAGER_PROCESS_ERROR")
        void testAddSysOperationIdNullRaisesException() {
            SysOperationMgr mgr = new SysOperationMgr();
            MockSysOperation sysOp = new MockSysOperation("test");
            
            BaseError exception = assertThrows(
                BaseError.class,
                () -> mgr.addSysOperation(null, sysOp)
            );
            
            assertEquals(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR.getCode(), exception.getCode());
            assertTrue(exception.getMessage().toLowerCase().contains("can not be none"));
        }
        
        @Test
        @DisplayName("sys_operation_id已存在时抛出SYS_OPERATION_MANAGER_PROCESS_ERROR")
        void testAddSysOperationDuplicateIdRaisesException() {
            SysOperationMgr mgr = new SysOperationMgr();
            MockSysOperation sysOp1 = new MockSysOperation("op1");
            MockSysOperation sysOp2 = new MockSysOperation("op2");
            
            mgr.addSysOperation("sys_op_1", sysOp1);
            
            BaseError exception = assertThrows(
                BaseError.class,
                () -> mgr.addSysOperation("sys_op_1", sysOp2)
            );
            
            assertEquals(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR.getCode(), exception.getCode());
            assertTrue(exception.getMessage().toLowerCase().contains("already exists"));
        }
        
        @Test
        @DisplayName("添加多个不同ID的SysOperation")
        void testAddMultipleSysOperations() {
            SysOperationMgr mgr = new SysOperationMgr();
            
            for (int i = 0; i < 5; i++) {
                mgr.addSysOperation("sys_op_" + i, new MockSysOperation("op_" + i));
            }
            
            for (int i = 0; i < 5; i++) {
                assertNotNull(mgr.getSysOperation("sys_op_" + i));
            }
        }
    }
    
    @Nested
    @DisplayName("获取SysOperation")
    class GetSysOperationTest {
        
        @Test
        @DisplayName("成功获取SysOperation")
        void testGetSysOperationSuccess() {
            SysOperationMgr mgr = new SysOperationMgr();
            MockSysOperation sysOp = new MockSysOperation("sys_op_1");
            mgr.addSysOperation("sys_op_1", sysOp);
            
            Object result = mgr.getSysOperation("sys_op_1");
            
            assertSame(sysOp, result);
        }
        
        @Test
        @DisplayName("sys_operation_id为null时抛出SYS_OPERATION_MANAGER_PROCESS_ERROR")
        void testGetSysOperationIdNullRaisesException() {
            SysOperationMgr mgr = new SysOperationMgr();
            
            BaseError exception = assertThrows(
                BaseError.class,
                () -> mgr.getSysOperation(null)
            );
            
            assertEquals(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR.getCode(), exception.getCode());
            assertTrue(exception.getMessage().toLowerCase().contains("can not be none"));
        }
        
        @Test
        @DisplayName("获取不存在的SysOperation返回null")
        void testGetNonexistentSysOperationReturnsNull() {
            SysOperationMgr mgr = new SysOperationMgr();
            
            Object result = mgr.getSysOperation("nonexistent");
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("获取的SysOperation是原始实例")
        void testGetSysOperationPreservesInstance() {
            SysOperationMgr mgr = new SysOperationMgr();
            MockSysOperation sysOp = new MockSysOperation("original");
            mgr.addSysOperation("sys_op_1", sysOp);
            
            Object result1 = mgr.getSysOperation("sys_op_1");
            Object result2 = mgr.getSysOperation("sys_op_1");
            
            // 应该是同一个实例
            assertSame(result1, result2);
            assertSame(result1, sysOp);
        }
    }
    
    @Nested
    @DisplayName("移除SysOperation")
    class RemoveSysOperationTest {
        
        @Test
        @DisplayName("成功移除SysOperation")
        void testRemoveSysOperationSuccess() {
            SysOperationMgr mgr = new SysOperationMgr();
            MockSysOperation sysOp = new MockSysOperation("sys_op_1");
            mgr.addSysOperation("sys_op_1", sysOp);
            
            Object result = mgr.removeSysOperation("sys_op_1");
            
            assertNull(mgr.getSysOperation("sys_op_1"));
            assertSame(sysOp, result);
        }
        
        @Test
        @DisplayName("sys_operation_id为null时抛出SYS_OPERATION_MANAGER_PROCESS_ERROR")
        void testRemoveSysOperationIdNullRaisesException() {
            SysOperationMgr mgr = new SysOperationMgr();
            
            BaseError exception = assertThrows(
                BaseError.class,
                () -> mgr.removeSysOperation(null)
            );
            
            assertEquals(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR.getCode(), exception.getCode());
            assertTrue(exception.getMessage().toLowerCase().contains("can not be none"));
        }
        
        @Test
        @DisplayName("移除不存在的SysOperation返回null")
        void testRemoveNonexistentSysOperationReturnsNull() {
            SysOperationMgr mgr = new SysOperationMgr();
            
            Object result = mgr.removeSysOperation("nonexistent");
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("移除后可以重新添加相同ID的SysOperation")
        void testRemoveThenReaddSysOperation() {
            SysOperationMgr mgr = new SysOperationMgr();
            MockSysOperation sysOp1 = new MockSysOperation("v1");
            MockSysOperation sysOp2 = new MockSysOperation("v2");
            
            mgr.addSysOperation("sys_op_1", sysOp1);
            mgr.removeSysOperation("sys_op_1");
            
            // 应该可以重新添加
            assertDoesNotThrow(() -> mgr.addSysOperation("sys_op_1", sysOp2));
            assertSame(sysOp2, mgr.getSysOperation("sys_op_1"));
        }
    }
    
    @Nested
    @DisplayName("集成场景")
    class IntegrationTest {
        
        @Test
        @DisplayName("SysOperation完整生命周期")
        void testSysOperationLifecycle() {
            SysOperationMgr mgr = new SysOperationMgr();
            MockSysOperation sysOp = new MockSysOperation("lifecycle_op");
            
            // 添加
            mgr.addSysOperation("sys_op_1", sysOp);
            assertNotNull(mgr.getSysOperation("sys_op_1"));
            
            // 获取
            Object result = mgr.getSysOperation("sys_op_1");
            assertSame(sysOp, result);
            
            // 移除
            Object removed = mgr.removeSysOperation("sys_op_1");
            assertSame(sysOp, removed);
            assertNull(mgr.getSysOperation("sys_op_1"));
        }
        
        @Test
        @DisplayName("管理多个SysOperations")
        void testMultipleSysOperationsManagement() {
            SysOperationMgr mgr = new SysOperationMgr();
            
            // 添加多个
            for (int i = 0; i < 10; i++) {
                mgr.addSysOperation("sys_op_" + i, new MockSysOperation("op_" + i));
            }
            
            // 获取特定的
            MockSysOperation result = (MockSysOperation) mgr.getSysOperation("sys_op_5");
            assertEquals("op_5", result.getOpId());
            
            // 移除部分
            for (int i = 0; i < 5; i++) {
                mgr.removeSysOperation("sys_op_" + i);
            }
            
            assertNull(mgr.getSysOperation("sys_op_0"));
            assertNotNull(mgr.getSysOperation("sys_op_5"));
        }
    }
    
    @Nested
    @DisplayName("线程安全性")
    class ThreadSafetyTest {
        
        @Test
        @DisplayName("并发添加、获取和移除")
        void testConcurrentAddGetRemove() throws InterruptedException {
            SysOperationMgr mgr = new SysOperationMgr();
            int threadCount = 3;
            int operationsPerThread = 20;
            CountDownLatch latch = new CountDownLatch(threadCount * 3);
            AtomicInteger errorCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount * 3);
            
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                
                // 添加线程
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            try {
                                MockSysOperation sysOp = new MockSysOperation("op_" + threadId + "_" + i);
                                mgr.addSysOperation("sys_op_" + threadId + "_" + i, sysOp);
                            } catch (BaseError e) {
                                // 可能的重复ID
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
                
                // 获取线程
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            try {
                                mgr.getSysOperation("sys_op_" + threadId + "_" + i);
                            } catch (BaseError e) {
                                // 预期的异常
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
                
                // 移除线程
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            try {
                                mgr.removeSysOperation("sys_op_" + threadId + "_" + i);
                            } catch (BaseError e) {
                                // 预期的异常
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            assertEquals(0, errorCount.get(), "应该没有非预期的错误");
        }
    }
}

