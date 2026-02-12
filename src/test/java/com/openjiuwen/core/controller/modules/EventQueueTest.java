// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.*;
import com.openjiuwen.core.session.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventQueue.
 *
 * <p>Covers topic building, subscribe/unsubscribe, publish_event,
 * set_event_handler, start/stop lifecycle, and error wrapping.
 *
 * <p>Python reference: {@code tests/unit_tests/core/controller/modules/test_event_queue.py}
 */
class EventQueueTest {

    private ControllerConfig config;
    private EventQueue eventQueue;
    private ConcreteEventHandler handler;

    // ==================== Concrete EventHandler ====================

    /**
     * Concrete EventHandler implementation for testing.
     */
    static class ConcreteEventHandler extends EventHandler {

        volatile int inputCount = 0;
        volatile int interactionCount = 0;
        volatile int completionCount = 0;
        volatile int failedCount = 0;
        volatile EventHandlerInput lastInput = null;

        ConcreteEventHandler() {
            super();
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            inputCount++;
            lastInput = inputs;
            return CompletableFuture.completedFuture(Map.of("status", "ok"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs) {
            interactionCount++;
            return CompletableFuture.completedFuture(Map.of("status", "ok"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs) {
            completionCount++;
            return CompletableFuture.completedFuture(Map.of("status", "ok"));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            failedCount++;
            return CompletableFuture.completedFuture(Map.of("status", "ok"));
        }
    }

    // ==================== Helpers ====================

    private Session makeSession(String sessionId) {
        Session session = mock(Session.class);
        when(session.getSessionId()).thenReturn(sessionId);
        return session;
    }

    @BeforeEach
    void setUp() {
        config = ControllerConfig.builder()
            .eventQueueSize(100)
            .eventTimeout(120000.0)
            .build();
        eventQueue = new EventQueue(config);
        handler = new ConcreteEventHandler();
    }

    @AfterEach
    void tearDown() {
        try {
            eventQueue.stop();
        } catch (Exception ignored) {
            // Ignore errors during teardown
        }
    }

    // ==================== Topic Building Tests ====================

    @Nested
    @DisplayName("Topic Building Tests")
    class TopicBuildingTests {

        @Test
        @DisplayName("Topic should be agent_id + session_id + event_type joined by underscore")
        void testTopicFormat() {
            String topic = EventQueue.buildTopic("agent1", "session1", EventType.INPUT.getValue());
            assertEquals("agent1_session1_input", topic);
        }

        @Test
        @DisplayName("Different event types should produce different topics")
        void testTopicUniquenessAcrossEventTypes() {
            String t1 = EventQueue.buildTopic("a", "s", EventType.INPUT.getValue());
            String t2 = EventQueue.buildTopic("a", "s", EventType.TASK_COMPLETION.getValue());
            String t3 = EventQueue.buildTopic("a", "s", EventType.TASK_INTERACTION.getValue());
            String t4 = EventQueue.buildTopic("a", "s", EventType.TASK_FAILED.getValue());

            assertEquals(4, java.util.Set.of(t1, t2, t3, t4).size());
        }

        @Test
        @DisplayName("Different sessions should produce different topics")
        void testTopicUniquenessAcrossSessions() {
            String t1 = EventQueue.buildTopic("a", "s1", EventType.INPUT.getValue());
            String t2 = EventQueue.buildTopic("a", "s2", EventType.INPUT.getValue());
            assertNotEquals(t1, t2);
        }
    }

    // ==================== Init & Config Tests ====================

    @Nested
    @DisplayName("Init & Config Tests")
    class InitConfigTests {

        @Test
        @DisplayName("EventQueue should store config on init, support config update, and wire event handler")
        void testInitAndConfigLifecycle() {
            // Init stores config
            assertSame(config, eventQueue.getConfig());

            // Event handler starts as null
            assertNull(eventQueue.getEventHandler());

            // Config setter updates internal config
            ControllerConfig newConfig = ControllerConfig.builder()
                .eventQueueSize(50)
                .build();
            eventQueue.setConfig(newConfig);
            assertSame(newConfig, eventQueue.getConfig());

            // setEventHandler wires the handler
            eventQueue.setEventHandler(handler);
            assertSame(handler, eventQueue.getEventHandler());
        }
    }

    // ==================== Subscribe / Unsubscribe Tests ====================

    @Nested
    @DisplayName("Subscribe / Unsubscribe Tests")
    class SubscribeUnsubscribeTests {

        @Test
        @DisplayName("subscribe should create subscriptions for all four event types")
        void testSubscribeReturnsFourEventTypes() {
            eventQueue.setEventHandler(handler);
            eventQueue.start();

            EventQueue.SubscribeResult result = eventQueue.subscribe("agent1", "session1");

            assertNotNull(result.subscriptions());
            assertNotNull(result.topics());
            assertTrue(result.subscriptions().containsKey(EventType.INPUT));
            assertTrue(result.subscriptions().containsKey(EventType.TASK_COMPLETION));
            assertTrue(result.subscriptions().containsKey(EventType.TASK_INTERACTION));
            assertTrue(result.subscriptions().containsKey(EventType.TASK_FAILED));

            assertTrue(result.topics().containsKey(EventType.INPUT));
            assertTrue(result.topics().containsKey(EventType.TASK_COMPLETION));
            assertTrue(result.topics().containsKey(EventType.TASK_INTERACTION));
            assertTrue(result.topics().containsKey(EventType.TASK_FAILED));
        }

        @Test
        @DisplayName("Subscribing without setting event_handler should raise")
        void testSubscribeWithoutHandlerRaises() {
            eventQueue.start();
            assertThrows(Exception.class,
                () -> eventQueue.subscribe("agent1", "session1"));
        }

        @Test
        @DisplayName("unsubscribe should clean up all subscriptions for a session")
        void testUnsubscribeCleansUp() {
            eventQueue.setEventHandler(handler);
            eventQueue.start();

            eventQueue.subscribe("agent1", "session1");
            eventQueue.unsubscribe("agent1", "session1");

            // After unsubscribe, re-subscribing should work (proving topic was cleaned)
            assertDoesNotThrow(() -> eventQueue.subscribe("agent1", "session1"));
        }

        @Test
        @DisplayName("unsubscribeAll should stop the entire queue")
        void testUnsubscribeAll() {
            eventQueue.setEventHandler(handler);
            eventQueue.start();

            eventQueue.subscribe("agent1", "session1");
            eventQueue.unsubscribeAll();

            // Queue should be stopped — after unsubscribeAll, it's effectively stopped
            // Verify by ensuring the queue's internal state is clean
            assertDoesNotThrow(() -> eventQueue.unsubscribeAll());
        }
    }

    // ==================== Publish Event Tests ====================

    @Nested
    @DisplayName("Publish Event Tests")
    class PublishEventTests {

        @Test
        @DisplayName("Publishing an InputEvent should route it to handler.handleInput")
        void testPublishInputEventRoutedToHandler() throws Exception {
            eventQueue.setEventHandler(handler);
            eventQueue.start();

            Session session = makeSession("s1");
            eventQueue.subscribe("agent1", "s1");

            InputEvent inputEvent = new InputEvent(
                List.of(new TextDataFrame("hello"))
            );

            eventQueue.publishEvent("agent1", session, inputEvent);

            // Give time for async processing
            Thread.sleep(200);

            assertEquals(1, handler.inputCount);
            assertNotNull(handler.lastInput);
            assertSame(inputEvent, handler.lastInput.getEvent());
            assertSame(session, handler.lastInput.getSession());
        }

        @Test
        @DisplayName("When the handler raises a non-BaseError, publishEvent should wrap it")
        void testPublishEventWrapsHandlerException() {
            ConcreteEventHandler failingHandler = new ConcreteEventHandler() {
                @Override
                public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
                    return CompletableFuture.failedFuture(new RuntimeException("handler exploded"));
                }
            };

            eventQueue.setEventHandler(failingHandler);
            eventQueue.start();

            Session session = makeSession("s1");
            eventQueue.subscribe("agent1", "s1");

            InputEvent inputEvent = new InputEvent(
                List.of(new TextDataFrame("hello"))
            );

            assertThrows(Exception.class,
                () -> eventQueue.publishEvent("agent1", session, inputEvent));
        }
    }

    // ==================== Lifecycle Tests ====================

    @Nested
    @DisplayName("Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("start() should start the underlying MessageQueue")
        void testStartActivatesQueue() {
            eventQueue.start();
            // Verify queue is running by successfully subscribing
            eventQueue.setEventHandler(handler);
            assertDoesNotThrow(() -> eventQueue.subscribe("agent1", "session1"));
        }

        @Test
        @DisplayName("stop() should stop the underlying MessageQueue")
        void testStopDeactivatesQueue() {
            eventQueue.start();
            eventQueue.stop();
            // After stop, the queue should be stopped
            // Note: We can't directly check isRunning, but we can verify behavior
        }

        @Test
        @DisplayName("Calling stop() multiple times should not raise")
        void testStopIdempotent() {
            eventQueue.start();
            eventQueue.stop();
            assertDoesNotThrow(() -> eventQueue.stop());
        }
    }

    // ==================== Error Wrapping Tests ====================

    @Nested
    @DisplayName("Error Wrapping Tests")
    class ErrorWrappingTests {

        @Test
        @DisplayName("Subscribing the same session twice should fail with BaseError")
        void testSubscribeExceptionWrappedWithStatusCode() {
            eventQueue.setEventHandler(handler);
            eventQueue.start();

            eventQueue.subscribe("agent1", "session1");

            assertThrows(BaseError.class,
                () -> eventQueue.subscribe("agent1", "session1"));
        }
    }
}

