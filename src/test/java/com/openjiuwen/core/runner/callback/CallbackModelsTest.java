// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for callback framework data models.
 * Translated from Python test_models.py
 */
@DisplayName("Callback Models Tests")
class CallbackModelsTest {

    // ========== CallbackMetrics ==========

    @Test
    @DisplayName("CallbackMetrics default values")
    void testCallbackMetricsDefaultValues() {
        CallbackMetrics metrics = new CallbackMetrics();
        assertEquals(0, metrics.getCallCount());
        assertEquals(0.0, metrics.getTotalTime());
        assertEquals(Double.MAX_VALUE, metrics.getMinTime());
        assertEquals(0.0, metrics.getMaxTime());
        assertEquals(0, metrics.getErrorCount());
        assertNull(metrics.getLastCallTime());
    }

    @Test
    @DisplayName("CallbackMetrics update success")
    void testCallbackMetricsUpdateSuccess() {
        CallbackMetrics metrics = new CallbackMetrics();
        metrics.update(0.5, false);
        assertEquals(1, metrics.getCallCount());
        assertEquals(0.5, metrics.getTotalTime(), 0.01);
        assertEquals(0.5, metrics.getMinTime(), 0.01);
        assertEquals(0.5, metrics.getMaxTime(), 0.01);
        assertEquals(0, metrics.getErrorCount());
        assertNotNull(metrics.getLastCallTime());
    }

    @Test
    @DisplayName("CallbackMetrics update error")
    void testCallbackMetricsUpdateError() {
        CallbackMetrics metrics = new CallbackMetrics();
        metrics.update(0.3, true);
        assertEquals(1, metrics.getCallCount());
        assertEquals(1, metrics.getErrorCount());
    }

    @Test
    @DisplayName("CallbackMetrics update multiple calls")
    void testCallbackMetricsUpdateMultipleCalls() {
        CallbackMetrics metrics = new CallbackMetrics();
        metrics.update(0.1, false);
        metrics.update(0.3, false);
        metrics.update(0.2, false);
        assertEquals(3, metrics.getCallCount());
        assertEquals(0.6, metrics.getTotalTime(), 0.01);
        assertEquals(0.1, metrics.getMinTime(), 0.01);
        assertEquals(0.3, metrics.getMaxTime(), 0.01);
    }

    @Test
    @DisplayName("CallbackMetrics avg_time no calls")
    void testCallbackMetricsAvgTimeNoCalls() {
        CallbackMetrics metrics = new CallbackMetrics();
        assertEquals(0.0, metrics.getAvgTime(), 0.001);
    }

    @Test
    @DisplayName("CallbackMetrics avg_time with calls")
    void testCallbackMetricsAvgTimeWithCalls() {
        CallbackMetrics metrics = new CallbackMetrics();
        metrics.update(0.1, false);
        metrics.update(0.3, false);
        assertEquals(0.2, metrics.getAvgTime(), 0.01);
    }

    @Test
    @DisplayName("CallbackMetrics toMap")
    void testCallbackMetricsToMap() {
        CallbackMetrics metrics = new CallbackMetrics();
        metrics.update(0.5, false);
        metrics.update(0.3, true);

        Map<String, Object> result = metrics.toMap();
        assertEquals(2, result.get("call_count"));
        assertEquals(0.4, (double) result.get("avg_time"), 0.01);
        assertEquals(0.3, (double) result.get("min_time"), 0.01);
        assertEquals(0.5, (double) result.get("max_time"), 0.01);
        assertEquals(1, result.get("error_count"));
        assertEquals(0.5, (double) result.get("error_rate"), 0.01);
        assertNotNull(result.get("last_call_time"));
    }

    @Test
    @DisplayName("CallbackMetrics toMap no calls")
    void testCallbackMetricsToMapNoCalls() {
        CallbackMetrics metrics = new CallbackMetrics();
        Map<String, Object> result = metrics.toMap();
        assertEquals(0, result.get("call_count"));
        assertEquals(0.0, result.get("min_time")); // should convert MAX_VALUE to 0
        assertEquals(0.0, result.get("error_rate"));
    }

    // ========== FilterResult ==========

    @Test
    @DisplayName("FilterResult continue")
    void testFilterResultContinue() {
        FilterResult result = FilterResult.continueResult();
        assertEquals(FilterAction.CONTINUE, result.getAction());
        assertNull(result.getModifiedArgs());
        assertNull(result.getModifiedKwargs());
        assertNull(result.getReason());
    }

    @Test
    @DisplayName("FilterResult skip with reason")
    void testFilterResultSkipWithReason() {
        FilterResult result = FilterResult.skipResult("Rate limit exceeded");
        assertEquals(FilterAction.SKIP, result.getAction());
        assertEquals("Rate limit exceeded", result.getReason());
    }

    @Test
    @DisplayName("FilterResult modify")
    void testFilterResultModify() {
        Object[] newArgs = {1, 2, 3};
        Map<String, Object> newKwargs = Map.of("key", "value");
        FilterResult result = FilterResult.modifyResult(newArgs, newKwargs);
        assertEquals(FilterAction.MODIFY, result.getAction());
        assertArrayEquals(newArgs, result.getModifiedArgs());
        assertEquals(newKwargs, result.getModifiedKwargs());
    }

    // ========== ChainContext ==========

    @Test
    @DisplayName("ChainContext default initialization")
    void testChainContextDefaultInit() {
        ChainContext context = new ChainContext("test_event",
                new Object[]{"arg1"}, Map.of("key", "value"));
        assertEquals("test_event", context.getEvent());
        assertArrayEquals(new Object[]{"arg1"}, context.getInitialArgs());
        assertEquals("value", context.getInitialKwargs().get("key"));
        assertTrue(context.getResults().isEmpty());
        assertTrue(context.getMetadata().isEmpty());
        assertEquals(0, context.getCurrentIndex());
        assertFalse(context.isCompleted());
        assertFalse(context.isRolledBack());
        assertTrue(context.getStartTime() > 0);
    }

    @Test
    @DisplayName("ChainContext get_last_result empty")
    void testChainContextGetLastResultEmpty() {
        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        assertNull(context.getLastResult());
    }

    @Test
    @DisplayName("ChainContext get_last_result")
    void testChainContextGetLastResult() {
        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        context.getResults().add("first");
        context.getResults().add("second");
        context.getResults().add("third");
        assertEquals("third", context.getLastResult());
    }

    @Test
    @DisplayName("ChainContext getAllResults returns copy")
    void testChainContextGetAllResults() {
        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        context.getResults().add("a");
        context.getResults().add("b");
        var results = context.getAllResults();
        assertEquals(2, results.size());
        results.add("c");
        assertEquals(2, context.getResults().size());
    }

    @Test
    @DisplayName("ChainContext metadata operations")
    void testChainContextMetadataOperations() {
        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        context.setMetadata("key1", "value1");
        assertEquals("value1", context.getMetadata("key1", null));
        assertNull(context.getMetadata("nonexistent", null));
        assertEquals("default", context.getMetadata("nonexistent", "default"));
    }

    @Test
    @DisplayName("ChainContext elapsed time")
    void testChainContextElapsedTime() throws InterruptedException {
        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        Thread.sleep(50);
        double elapsed = context.getElapsedTime();
        assertTrue(elapsed >= 0.05);
    }

    // ========== ChainResult ==========

    @Test
    @DisplayName("ChainResult continue")
    void testChainResultContinue() {
        ChainResult result = ChainResult.builder()
                .action(ChainAction.CONTINUE)
                .result("success")
                .build();
        assertEquals(ChainAction.CONTINUE, result.getAction());
        assertEquals("success", result.getResult());
        assertNull(result.getContext());
        assertNull(result.getError());
    }

    @Test
    @DisplayName("ChainResult rollback with error")
    void testChainResultRollbackWithError() {
        Exception error = new RuntimeException("Something went wrong");
        ChainContext ctx = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = ChainResult.builder()
                .action(ChainAction.ROLLBACK)
                .context(ctx)
                .error(error)
                .build();
        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertSame(ctx, result.getContext());
        assertSame(error, result.getError());
    }

    @Test
    @DisplayName("ChainResult break")
    void testChainResultBreak() {
        ChainResult result = ChainResult.builder()
                .action(ChainAction.BREAK)
                .result(Map.of("data", "value"))
                .build();
        assertEquals(ChainAction.BREAK, result.getAction());
        assertEquals(Map.of("data", "value"), result.getResult());
    }

    // ========== CallbackInfo ==========

    @Test
    @DisplayName("CallbackInfo default initialization")
    void testCallbackInfoDefaultInit() {
        CallbackInfo info = CallbackInfo.builder()
                .callback(kwargs -> null)
                .priority(0)
                .build();
        assertNotNull(info.getCallback());
        assertEquals(0, info.getPriority());
        assertFalse(info.isOnce());
        assertTrue(info.isEnabled());
        assertEquals("default", info.getNamespace());
        assertTrue(info.getTags().isEmpty());
        assertEquals(0, info.getMaxRetries());
        assertEquals(0.0, info.getRetryDelay());
        assertNull(info.getTimeout());
        assertTrue(info.getCreatedAt() > 0);
    }

    @Test
    @DisplayName("CallbackInfo full initialization")
    void testCallbackInfoFullInit() {
        CallbackInfo info = CallbackInfo.builder()
                .callback(kwargs -> null)
                .priority(10)
                .once(true)
                .enabled(false)
                .namespace("custom")
                .tags(java.util.Set.of("tag1", "tag2"))
                .maxRetries(3)
                .retryDelay(1.0)
                .timeout(30.0)
                .build();
        assertEquals(10, info.getPriority());
        assertTrue(info.isOnce());
        assertFalse(info.isEnabled());
        assertEquals("custom", info.getNamespace());
        assertEquals(2, info.getTags().size());
        assertEquals(3, info.getMaxRetries());
        assertEquals(1.0, info.getRetryDelay());
        assertEquals(30.0, info.getTimeout());
    }
}
