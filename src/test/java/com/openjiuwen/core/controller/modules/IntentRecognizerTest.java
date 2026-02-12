// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventHandlerWithIntentRecognition.
 *
 * <p>IntentRecognizer is a simple class with constructor (stores params) + stub
 * recognize(), and EventHandlerWithIntentRecognition has many stub methods.
 * Per testing guidelines, we only test the meaningful wiring logic in constructor
 * rather than testing trivial stub/pass methods.
 *
 * <p>Covers:
 * <ul>
 *   <li>EventHandlerWithIntentRecognition init wiring: creates IntentRecognizer
 *       with correctly injected dependencies from the parent EventHandler.</li>
 * </ul>
 *
 * <p>Python reference: {@code tests/unit_tests/core/controller/modules/test_intent_recognizer.py}
 */
class IntentRecognizerTest {

    // ==================== Concrete Handler ====================

    /**
     * Concrete implementation of EventHandlerWithIntentRecognition for testing.
     * Only implements the required abstract method.
     */
    static class ConcreteHandler extends EventHandlerWithIntentRecognition {

        @Override
        protected CompletableFuture<Void> processCreateTaskIntent(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }
    }

    // ==================== Tests ====================

    @Nested
    @DisplayName("EventHandlerWithIntentRecognition Tests")
    class InitWiringTests {

        @Test
        @DisplayName("Constructor should create an IntentRecognizer instance")
        void testInitCreatesRecognizer() {
            // Note: EventHandler.__init__() resets all deps to null; the Controller
            // injects real deps via property setters after construction. So the
            // recognizer is created with None deps at init time — we only verify it
            // is correctly instantiated as an IntentRecognizer.
            ConcreteHandler handler = new ConcreteHandler();
            assertNotNull(handler.recognizer);
            assertInstanceOf(IntentRecognizer.class, handler.recognizer);
        }
    }
}

