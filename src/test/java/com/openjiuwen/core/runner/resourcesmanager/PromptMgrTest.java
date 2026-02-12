// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PromptMgr 测试类
 * 
 * 对应Python: test_prompt_manager.py
 */
@DisplayName("PromptMgr 测试")
class PromptMgrTest {
    
    static class MockPromptTemplate {
        private final String templateId;
        private final String content;
        
        MockPromptTemplate(String templateId) {
            this(templateId, "default content");
        }
        
        MockPromptTemplate(String templateId, String content) {
            this.templateId = templateId;
            this.content = content;
        }
        
        String getContent() {
            return content;
        }
    }
    
    @Nested
    @DisplayName("添加Prompt")
    class AddPromptTest {
        
        @Test
        @DisplayName("成功添加Prompt")
        void testAddPromptSuccess() {
            PromptMgr mgr = new PromptMgr();
            MockPromptTemplate template = new MockPromptTemplate("prompt_1", "Hello {name}");
            
            mgr.addPrompt("prompt_1", template);
            
            assertNotNull(mgr.getPrompt("prompt_1"));
        }
        
        @Test
        @DisplayName("template_id为null时抛出SESSION_PROMPT_ADD_FAILED")
        void testAddPromptTemplateIdNullRaisesException() {
            PromptMgr mgr = new PromptMgr();
            MockPromptTemplate template = new MockPromptTemplate("p1");
            
            JiuWenBaseException exception = assertThrows(
                JiuWenBaseException.class,
                () -> mgr.addPrompt(null, template)
            );
            
            assertEquals(StatusCode.SESSION_PROMPT_ADD_FAILED.getCode(), exception.getErrorCode());
            assertTrue(exception.getMessage().toLowerCase().contains("template_id"));
        }
        
        @Test
        @DisplayName("template为null时抛出SESSION_PROMPT_ADD_FAILED")
        void testAddPromptTemplateNullRaisesException() {
            PromptMgr mgr = new PromptMgr();
            
            JiuWenBaseException exception = assertThrows(
                JiuWenBaseException.class,
                () -> mgr.addPrompt("prompt_1", null)
            );
            
            assertEquals(StatusCode.SESSION_PROMPT_ADD_FAILED.getCode(), exception.getErrorCode());
            assertTrue(exception.getMessage().toLowerCase().contains("template"));
        }
        
        @Test
        @DisplayName("添加相同ID的Prompt会覆盖")
        void testAddPromptOverwriteExisting() {
            PromptMgr mgr = new PromptMgr();
            MockPromptTemplate template1 = new MockPromptTemplate("p1", "content_v1");
            MockPromptTemplate template2 = new MockPromptTemplate("p1", "content_v2");
            
            mgr.addPrompt("prompt_1", template1);
            mgr.addPrompt("prompt_1", template2);
            
            // 直接添加会覆盖
            assertSame(template2, mgr.getPrompt("prompt_1"));
        }
        
        @Test
        @DisplayName("批量添加Prompts")
        void testAddPromptsBatch() {
            PromptMgr mgr = new PromptMgr();
            List<PromptMgr.PromptEntry> templates = Arrays.asList(
                new PromptMgr.PromptEntry("p1", new MockPromptTemplate("1")),
                new PromptMgr.PromptEntry("p2", new MockPromptTemplate("2")),
                new PromptMgr.PromptEntry("p3", new MockPromptTemplate("3"))
            );
            
            mgr.addPrompts(templates);
            
            assertNotNull(mgr.getPrompt("p1"));
            assertNotNull(mgr.getPrompt("p2"));
            assertNotNull(mgr.getPrompt("p3"));
        }
        
        @Test
        @DisplayName("批量添加null不执行任何操作")
        void testAddPromptsNullNoOp() {
            PromptMgr mgr = new PromptMgr();
            
            mgr.addPrompts(null);
            
            // 不应该抛出异常
        }
    }
    
    @Nested
    @DisplayName("获取Prompt")
    class GetPromptTest {
        
        @Test
        @DisplayName("成功获取Prompt")
        void testGetPromptSuccess() {
            PromptMgr mgr = new PromptMgr();
            MockPromptTemplate template = new MockPromptTemplate("prompt_1", "Hello {name}");
            mgr.addPrompt("prompt_1", template);
            
            Object result = mgr.getPrompt("prompt_1");
            
            assertSame(template, result);
        }
        
        @Test
        @DisplayName("template_id为null时抛出SESSION_PROMPT_GET_FAILED")
        void testGetPromptTemplateIdNullRaisesException() {
            PromptMgr mgr = new PromptMgr();
            
            JiuWenBaseException exception = assertThrows(
                JiuWenBaseException.class,
                () -> mgr.getPrompt(null)
            );
            
            assertEquals(StatusCode.SESSION_PROMPT_GET_FAILED.getCode(), exception.getErrorCode());
            assertTrue(exception.getMessage().toLowerCase().contains("template_id"));
        }
        
        @Test
        @DisplayName("获取不存在的Prompt返回null")
        void testGetNonexistentPromptReturnsNull() {
            PromptMgr mgr = new PromptMgr();
            
            Object result = mgr.getPrompt("nonexistent");
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("获取的Prompt保持原始内容")
        void testGetPromptPreservesContent() {
            PromptMgr mgr = new PromptMgr();
            String originalContent = "This is a test template with {variable}";
            MockPromptTemplate template = new MockPromptTemplate("test", originalContent);
            mgr.addPrompt("test_prompt", template);
            
            MockPromptTemplate result = (MockPromptTemplate) mgr.getPrompt("test_prompt");
            
            assertEquals(originalContent, result.getContent());
        }
    }
    
    @Nested
    @DisplayName("移除Prompt")
    class RemovePromptTest {
        
        @Test
        @DisplayName("成功移除Prompt")
        void testRemovePromptSuccess() {
            PromptMgr mgr = new PromptMgr();
            MockPromptTemplate template = new MockPromptTemplate("prompt_1");
            mgr.addPrompt("prompt_1", template);
            
            Object result = mgr.removePrompt("prompt_1");
            
            assertNull(mgr.getPrompt("prompt_1"));
            assertSame(template, result);
        }
        
        @Test
        @DisplayName("移除不存在的Prompt返回null")
        void testRemoveNonexistentPromptReturnsNull() {
            PromptMgr mgr = new PromptMgr();
            
            Object result = mgr.removePrompt("nonexistent");
            
            assertNull(result);
        }
        
        @Test
        @DisplayName("移除后可以重新添加相同ID的Prompt")
        void testRemoveThenReaddPrompt() {
            PromptMgr mgr = new PromptMgr();
            MockPromptTemplate template1 = new MockPromptTemplate("v1", "content_v1");
            MockPromptTemplate template2 = new MockPromptTemplate("v2", "content_v2");
            
            mgr.addPrompt("prompt_1", template1);
            mgr.removePrompt("prompt_1");
            
            mgr.addPrompt("prompt_1", template2);
            
            assertSame(template2, mgr.getPrompt("prompt_1"));
        }
    }
    
    @Nested
    @DisplayName("集成场景")
    class IntegrationTest {
        
        @Test
        @DisplayName("Prompt完整生命周期")
        void testPromptLifecycle() {
            PromptMgr mgr = new PromptMgr();
            MockPromptTemplate template = new MockPromptTemplate("lifecycle", "Hello {user}");
            
            // 添加
            mgr.addPrompt("prompt_1", template);
            assertNotNull(mgr.getPrompt("prompt_1"));
            
            // 获取
            Object result = mgr.getPrompt("prompt_1");
            assertSame(template, result);
            
            // 移除
            Object removed = mgr.removePrompt("prompt_1");
            assertSame(template, removed);
            assertNull(mgr.getPrompt("prompt_1"));
        }
        
        @Test
        @DisplayName("管理多个Prompts")
        void testMultiplePromptsManagement() {
            PromptMgr mgr = new PromptMgr();
            
            // 添加多个Prompts
            for (int i = 0; i < 10; i++) {
                mgr.addPrompt("prompt_" + i, new MockPromptTemplate("p" + i, "content_" + i));
            }
            
            // 获取特定的
            MockPromptTemplate result = (MockPromptTemplate) mgr.getPrompt("prompt_5");
            assertEquals("content_5", result.getContent());
            
            // 移除部分
            for (int i = 0; i < 5; i++) {
                mgr.removePrompt("prompt_" + i);
            }
            
            assertNull(mgr.getPrompt("prompt_0"));
            assertNotNull(mgr.getPrompt("prompt_5"));
        }
    }
    
    @Nested
    @DisplayName("线程安全性")
    class ThreadSafetyTest {
        
        @Test
        @DisplayName("并发添加和获取")
        void testConcurrentAddGet() throws InterruptedException {
            PromptMgr mgr = new PromptMgr();
            int threadCount = 5;
            int operationsPerThread = 20;
            CountDownLatch latch = new CountDownLatch(threadCount * 2);
            AtomicInteger errorCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2);
            
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                
                // 添加线程
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            MockPromptTemplate template = new MockPromptTemplate("t_" + threadId + "_" + i);
                            mgr.addPrompt("prompt_" + threadId + "_" + i, template);
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
                                mgr.getPrompt("prompt_" + threadId + "_" + i);
                            } catch (JiuWenBaseException e) {
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

