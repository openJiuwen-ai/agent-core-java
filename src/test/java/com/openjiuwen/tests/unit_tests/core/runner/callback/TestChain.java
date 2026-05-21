/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.CallbackChain;
import com.openjiuwen.core.runner.callback.CallbackInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CallbackChain test cases.
 *
 * <p>Mirrors Python's {@code test_chain.py} in
 * {@code tests/unit_tests/core/runner/callback/test_chain}.</p>
 */
@DisplayName("CallbackChain Tests")
class TestChain {

    @Nested
    @DisplayName("Initialization")
    class InitTests {

        @Test
        @DisplayName("test_initialization - CallbackChain initialization")
        void testInitialization() {
            CallbackChain chain = new CallbackChain("test_chain");
            assertThat(chain.getName()).isEqualTo("test_chain");
            assertThat(chain.getCallbacks()).isEmpty();
        }

        @Test
        @DisplayName("test_initialization_null_name - null name defaults to empty")
        void testInitializationNullName() {
            CallbackChain chain = new CallbackChain(null);
            assertThat(chain.getName()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Add Callback")
    class AddCallbackTests {

        @Test
        @DisplayName("test_add_callback - adding callback to chain")
        void testAddCallback() {
            CallbackChain chain = new CallbackChain();

            Function<Map<String, Object>, Object> callback = (ctx) -> "result";
            CallbackInfo info = new CallbackInfo(callback, 10, null, null);
            chain.add(info, null, null);

            assertThat(chain.getCallbacks()).hasSize(1);
            assertThat(chain.getCallbacks().get(0)).isEqualTo(info);
        }

        @Test
        @DisplayName("test_add_multiple_callbacks_sorted_by_priority - callbacks sorted by priority")
        void testAddMultipleCallbacksSortedByPriority() {
            CallbackChain chain = new CallbackChain();

            Function<Map<String, Object>, Object> lowPriority = (ctx) -> "low";
            Function<Map<String, Object>, Object> highPriority = (ctx) -> "high";
            Function<Map<String, Object>, Object> mediumPriority = (ctx) -> "medium";

            chain.add(new CallbackInfo(lowPriority, 1, null, null), null, null);
            chain.add(new CallbackInfo(highPriority, 10, null, null), null, null);
            chain.add(new CallbackInfo(mediumPriority, 5, null, null), null, null);

            assertThat(chain.getCallbacks().get(0).getPriority()).isEqualTo(10);
            assertThat(chain.getCallbacks().get(1).getPriority()).isEqualTo(5);
            assertThat(chain.getCallbacks().get(2).getPriority()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Remove Callback")
    class RemoveCallbackTests {

        @Test
        @DisplayName("test_remove_callback - removing callback from chain")
        void testRemoveCallback() {
            CallbackChain chain = new CallbackChain();

            Function<Map<String, Object>, Object> callback1 = (ctx) -> "result1";
            Function<Map<String, Object>, Object> callback2 = (ctx) -> "result2";

            chain.add(new CallbackInfo(callback1, 0, null, null), null, null);
            chain.add(new CallbackInfo(callback2, 0, null, null), null, null);

            assertThat(chain.getCallbacks()).hasSize(2);

            chain.remove(callback1);
            assertThat(chain.getCallbacks()).hasSize(1);
            assertThat(chain.getCallbacks().get(0).getCallback()).isEqualTo(callback2);
        }
    }
}