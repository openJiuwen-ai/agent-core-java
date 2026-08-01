// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for callback framework data models.
 *
 * <p>Mirrors Python's {@code test_models.py} in
 * {@code tests/unit_tests/core/runner/callback/test_models.py}.</p>
 */
@DisplayName("Callback Models Tests")
class CallbackModelsTest {

    @Test
    @DisplayName("CallbackMetrics default values")
    void testCallbackMetricsDefaultValues() {
        CallbackMetrics metrics = new CallbackMetrics();
        assertEquals(0, metrics.getCallCount());
        assertEquals(0.0, metrics.getTotalTime());
        assertEquals(Double.POSITIVE_INFINITY, metrics.getMinTime());
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

        assertEquals(0.0, metrics.getAvgTime(), 0.0);
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
    @DisplayName("CallbackMetrics toMap keeps python keys")
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
    @DisplayName("CallbackMetrics toMap no calls keeps null last_call_time")
    void testCallbackMetricsToMapNoCalls() {
        CallbackMetrics metrics = new CallbackMetrics();
        Map<String, Object> result = metrics.toMap();
        assertEquals(0, result.get("call_count"));
        assertEquals(0.0, result.get("min_time"));
        assertEquals(0.0, result.get("error_rate"));
        assertTrue(result.containsKey("last_call_time"));
        assertNull(result.get("last_call_time"));
    }

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

    @Test
    @DisplayName("ChainContext default initialization")
    void testChainContextDefaultInit() {
        ChainContext context = new ChainContext("test_event", new Object[]{"arg1"}, Map.of("key", "value"));
        assertEquals("test_event", context.getEvent());
        assertArrayEquals(new Object[]{"arg1"}, context.getInitialArgs());
        assertEquals("value", context.getInitialKwargs().get("key"));
        assertTrue(context.getResults().isEmpty());
        assertTrue(context.getMetadata().isEmpty());
        assertEquals(0, context.getCurrentIndex());
        assertFalse(context.isCompleted());
        assertFalse(context.isRolledBack());
        assertTrue(context.getStartTime() > 0.0);
    }

    @Test
    @DisplayName("ChainContext result and metadata helpers")
    void testChainContextHelpers() {
        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        context.getResults().add("first");
        context.getResults().add("second");
        context.setMetadata("key1", "value1");

        assertEquals("second", context.getLastResult());
        assertEquals("value1", context.getMetadata("key1", null));
        assertEquals("default", context.getMetadata("missing", "default"));
        assertEquals(2, context.getAllResults().size());
        assertTrue(context.getElapsedTime() >= 0.0);
    }

    @Test
    @DisplayName("ChainContext getLastResult returns null when empty")
    void testChainContextGetLastResultEmpty() {
        ChainContext context = new ChainContext("test", new Object[0], Map.of());

        assertNull(context.getLastResult());
    }

    @Test
    @DisplayName("ChainContext getAllResults returns copy")
    void testChainContextGetAllResultsReturnsCopy() {
        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        context.getResults().add("a");
        context.getResults().add("b");

        var results = context.getAllResults();
        results.add("c");

        assertEquals(2, context.getResults().size());
        assertEquals(3, results.size());
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

    @Disabled("remote env do not support node")
    @Test
    @DisplayName("ChainContext elapsed time increases")
    void testChainContextElapsedTime() throws InterruptedException {
        ChainContext context = new ChainContext("test", new Object[0], Map.of());

        Thread.sleep(50L);

        assertTrue(context.getElapsedTime() >= 0.05d);
    }

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
        ChainContext context = new ChainContext("test", new Object[0], Map.of());
        ChainResult result = ChainResult.builder()
                .action(ChainAction.ROLLBACK)
                .context(context)
                .error(error)
                .build();
        assertEquals(ChainAction.ROLLBACK, result.getAction());
        assertSame(context, result.getContext());
        assertSame(error, result.getError());
    }

    @Test
    @DisplayName("ChainResult break")
    void testChainResultBreak() {
        Map<String, Object> resultData = Map.of("data", "value");
        ChainResult result = ChainResult.builder()
                .action(ChainAction.BREAK)
                .result(resultData)
                .build();

        assertEquals(ChainAction.BREAK, result.getAction());
        assertEquals(resultData, result.getResult());
    }

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
        assertTrue(info.getCreatedAt() > 0.0);
        assertNull(info.getWrapper());
        assertEquals("", info.getCallbackType());
    }

    @Test
    @DisplayName("CallbackInfo full initialization")
    void testCallbackInfoFullInit() {
        Function<Map<String, Object>, Object> callback = kwargs -> null;
        Function<Map<String, Object>, Object> wrapper = kwargs -> "wrapped";
        CallbackInfo info = CallbackInfo.builder()
                .callback(callback)
                .priority(10)
                .once(true)
                .enabled(false)
                .namespace("custom")
                .tags(Set.of("tag1", "tag2"))
                .maxRetries(3)
                .retryDelay(1.0)
                .timeout(30.0)
                .wrapper(wrapper)
                .callbackType("transform")
                .build();
        assertEquals(10, info.getPriority());
        assertTrue(info.isOnce());
        assertFalse(info.isEnabled());
        assertEquals("custom", info.getNamespace());
        assertEquals(2, info.getTags().size());
        assertEquals(3, info.getMaxRetries());
        assertEquals(1.0, info.getRetryDelay());
        assertEquals(30.0, info.getTimeout());
        assertSame(wrapper, info.getWrapper());
        assertEquals("transform", info.getCallbackType());
    }

    @Test
    @DisplayName("CallbackInfo hash is callback identity")
    void testCallbackInfoHash() {
        Function<Map<String, Object>, Object> callback1 = kwargs -> null;
        Function<Map<String, Object>, Object> callback2 = kwargs -> null;

        CallbackInfo info1 = CallbackInfo.builder().callback(callback1).priority(0).build();
        CallbackInfo info2 = CallbackInfo.builder().callback(callback1).priority(10).build();
        CallbackInfo info3 = CallbackInfo.builder().callback(callback2).priority(0).build();

        assertEquals(info1.hashCode(), info2.hashCode());
        assertNotEquals(info1.hashCode(), info3.hashCode());
    }
}
