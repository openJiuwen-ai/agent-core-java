/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.callback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CallbackManager and related classes.
 * 
 * <p>Converted from Python: test_callback_manager.py</p>
 */
class CallbackManagerTest {
    
    /**
     * Mock handler for testing.
     */
    static class MockHandler extends BaseHandler {
        boolean onStartCalled = false;
        boolean onEndCalled = false;
        Map<String, Object> kwargs;
        
        MockHandler(Object owner) {
            super(owner);
        }
        
        @Override
        public String eventName() {
            return "mock_handler";
        }
        
        @TriggerEvent
        public CompletableFuture<Void> onStart(Map<String, Object> kwargs) {
            this.onStartCalled = true;
            this.kwargs = kwargs;
            return CompletableFuture.completedFuture(null);
        }
        
        @TriggerEvent
        public CompletableFuture<Void> onEnd(Map<String, Object> kwargs) {
            this.onEndCalled = true;
            this.kwargs = kwargs;
            return CompletableFuture.completedFuture(null);
        }
        
        public void nonTriggerMethod() {
            // This method is not a trigger event
        }
    }
    
    /**
     * Another mock handler with different events.
     */
    static class AnotherMockHandler extends BaseHandler {
        boolean onProcessCalled = false;
        
        AnotherMockHandler(Object owner) {
            super(owner);
        }
        
        @Override
        public String eventName() {
            return "another_handler";
        }
        
        @TriggerEvent
        public CompletableFuture<Void> onProcess(Map<String, Object> kwargs) {
            this.onProcessCalled = true;
            return CompletableFuture.completedFuture(null);
        }
    }
    
    @Nested
    @DisplayName("CallbackManager Tests")
    class CallbackManagerTests {
        
        private CallbackManager manager;
        private MockHandler mockHandler;
        
        @BeforeEach
        void setUp() {
            manager = new CallbackManager();
            mockHandler = new MockHandler(manager);
        }
        
        @Test
        @DisplayName("construction initializes empty handlers")
        void testConstructionInitializesEmptyHandlers() {
            assertTrue(manager.getHandlers().isEmpty());
            assertTrue(manager.getTriggerEvents().isEmpty());
        }
        
        @Test
        @DisplayName("register adds handler")
        void testRegisterAddsHandler() {
            manager.register(Map.of("mock_handler", mockHandler));
            assertTrue(manager.getHandlers().containsKey("mock_handler"));
            assertSame(mockHandler, manager.getHandlers().get("mock_handler"));
        }
        
        @Test
        @DisplayName("register extracts trigger events")
        void testRegisterExtractsTriggerEvents() {
            manager.register(Map.of("mock_handler", mockHandler));
            List<String> events = manager.getTriggerEvents().get("mock_handler");
            assertTrue(events.contains("onStart"));
            assertTrue(events.contains("onEnd"));
            assertFalse(events.contains("nonTriggerMethod"));
        }
        
        @Test
        @DisplayName("register multiple handlers")
        void testRegisterMultipleHandlers() {
            MockHandler mock1 = new MockHandler(manager);
            AnotherMockHandler mock2 = new AnotherMockHandler(manager);
            manager.register(Map.of("mock_handler", mock1, "another_handler", mock2));
            assertEquals(2, manager.getHandlers().size());
            assertTrue(manager.getHandlers().containsKey("mock_handler"));
            assertTrue(manager.getHandlers().containsKey("another_handler"));
        }
        
        @Test
        @DisplayName("trigger calls handler method")
        void testTriggerCallsHandlerMethod() throws ExecutionException, InterruptedException {
            manager.register(Map.of("mock_handler", mockHandler));
            manager.trigger("mock_handler", "onStart", Map.of("key", "value")).get();
            assertTrue(mockHandler.onStartCalled);
            assertEquals(Map.of("key", "value"), mockHandler.kwargs);
        }
        
        @Test
        @DisplayName("trigger with multiple kwargs")
        void testTriggerWithMultipleKwargs() throws ExecutionException, InterruptedException {
            manager.register(Map.of("mock_handler", mockHandler));
            manager.trigger("mock_handler", "onEnd", 
                Map.of("arg1", "val1", "arg2", "val2", "arg3", 123)).get();
            assertTrue(mockHandler.onEndCalled);
            assertEquals("val1", mockHandler.kwargs.get("arg1"));
            assertEquals("val2", mockHandler.kwargs.get("arg2"));
            assertEquals(123, mockHandler.kwargs.get("arg3"));
        }
        
        @Test
        @DisplayName("trigger nonexistent handler raises")
        void testTriggerNonexistentHandlerRaises() {
            assertThrows(CallbackManager.TypeError.class, () -> {
                manager.trigger("nonexistent_handler", "onStart", Map.of());
            });
        }
        
        @Test
        @DisplayName("trigger nonexistent event raises")
        void testTriggerNonexistentEventRaises() {
            manager.register(Map.of("mock_handler", mockHandler));
            assertThrows(CallbackManager.TypeError.class, () -> {
                manager.trigger("mock_handler", "nonexistentEvent", Map.of());
            });
        }
        
        @Test
        @DisplayName("trigger non-trigger event raises")
        void testTriggerNonTriggerEventRaises() {
            manager.register(Map.of("mock_handler", mockHandler));
            assertThrows(CallbackManager.TypeError.class, () -> {
                manager.trigger("mock_handler", "nonTriggerMethod", Map.of());
            });
        }
        
        @Test
        @DisplayName("trigger different handlers")
        void testTriggerDifferentHandlers() throws ExecutionException, InterruptedException {
            MockHandler mock1 = new MockHandler(manager);
            AnotherMockHandler mock2 = new AnotherMockHandler(manager);
            manager.register(Map.of("mock_handler", mock1, "another_handler", mock2));
            
            manager.trigger("mock_handler", "onStart", Map.of()).get();
            manager.trigger("another_handler", "onProcess", Map.of()).get();
            
            assertTrue(mock1.onStartCalled);
            assertTrue(mock2.onProcessCalled);
        }
    }
    
    @Nested
    @DisplayName("TriggerEvent Annotation Tests")
    class TriggerEventAnnotationTests {
        
        @Test
        @DisplayName("annotation marks method")
        void testAnnotationMarksMethod() throws NoSuchMethodException {
            var method = MockHandler.class.getMethod("onStart", Map.class);
            assertTrue(method.isAnnotationPresent(TriggerEvent.class));
        }
        
        @Test
        @DisplayName("non-annotated method not marked")
        void testNonAnnotatedMethodNotMarked() throws NoSuchMethodException {
            var method = MockHandler.class.getMethod("nonTriggerMethod");
            assertFalse(method.isAnnotationPresent(TriggerEvent.class));
        }
    }
    
    @Nested
    @DisplayName("BaseHandler Tests")
    class BaseHandlerTests {
        
        @Test
        @DisplayName("get trigger events returns annotated methods")
        void testGetTriggerEventsReturnsAnnotatedMethods() {
            MockHandler handler = new MockHandler(null);
            List<String> events = handler.getTriggerEvents();
            assertTrue(events.contains("onStart"));
            assertTrue(events.contains("onEnd"));
            assertFalse(events.contains("nonTriggerMethod"));
            assertFalse(events.contains("eventName"));
        }
        
        @Test
        @DisplayName("handler stores owner")
        void testHandlerStoresOwner() {
            CallbackManager manager = new CallbackManager();
            MockHandler handler = new MockHandler(manager);
            assertSame(manager, handler.getOwner());
        }
    }
}

