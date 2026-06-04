/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.CallbackFramework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Framework metrics test cases.
 *
 * <p>Mirrors Python's {@code test_framework_metrics.py} in
 * {@code tests/unit_tests/core/runner/callback/test_framework_metrics}.</p>
 */
@DisplayName("Framework Metrics Tests")
class TestFrameworkMetrics {

    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        framework = new CallbackFramework(true, false);
    }

    @Test
    @DisplayName("test_metrics_enabled_collects_data")
    void testMetricsEnabledCollectsData() {
        framework.on("event", kwargs -> "done", "callback");

        framework.trigger("event");

        assertTrue(framework.getMetrics().containsKey("event:callback"));
    }

    @Test
    @DisplayName("test_metrics_disabled_no_collection")
    void testMetricsDisabledNoCollection() {
        CallbackFramework noMetrics = new CallbackFramework(false, false);
        noMetrics.on("event", kwargs -> "done", "callback");

        noMetrics.trigger("event");

        assertTrue(noMetrics.getMetrics().isEmpty());
    }

    @Test
    @DisplayName("test_metrics_tracks_call_count")
    void testMetricsTracksCallCount() {
        framework.on("event", kwargs -> "done", "callback");

        for (int i = 0; i < 5; i++) {
            framework.trigger("event");
        }

        assertEquals(5, framework.getMetrics("event", "callback").get("event:callback").get("call_count"));
    }

    @Test
    @DisplayName("test_metrics_tracks_timing")
    void testMetricsTracksTiming() {
        framework.on("event", kwargs -> {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "done";
        }, "callback");

        framework.trigger("event");

        Map<String, Object> metric = framework.getMetrics().get("event:callback");
        assertTrue((double) metric.get("avg_time") >= 0.0);
        assertTrue((double) metric.get("max_time") >= (double) metric.get("min_time"));
    }

    @Test
    @DisplayName("test_metrics_tracks_errors")
    void testMetricsTracksErrors() {
        framework.on("event", kwargs -> {
            throw new RuntimeException("Error!");
        }, "failing_callback");

        framework.trigger("event");
        framework.trigger("event");
        framework.trigger("event");

        Map<String, Object> metric = framework.getMetrics().get("event:failing_callback");
        assertEquals(3, metric.get("call_count"));
        assertEquals(3, metric.get("error_count"));
        assertEquals(1.0, (double) metric.get("error_rate"), 0.01);
    }

    @Test
    @DisplayName("test_get_metrics_filter_by_event")
    void testGetMetricsFilterByEvent() {
        framework.on("event1", kwargs -> null, "cb1");
        framework.on("event2", kwargs -> null, "cb2");
        framework.trigger("event1");
        framework.trigger("event2");

        Map<String, Map<String, Object>> metrics = framework.getMetrics("event1", null);

        assertEquals(1, metrics.size());
        assertTrue(metrics.containsKey("event1:cb1"));
    }

    @Test
    @DisplayName("test_get_metrics_filter_by_callback")
    void testGetMetricsFilterByCallback() {
        framework.on("event", kwargs -> null, "callback_a");
        framework.on("event", kwargs -> null, "callback_b");
        framework.trigger("event");

        Map<String, Map<String, Object>> metrics = framework.getMetrics(null, "callback_a");

        assertEquals(1, metrics.size());
        assertTrue(metrics.containsKey("event:callback_a"));
    }

    @Test
    @DisplayName("test_reset_metrics")
    void testResetMetrics() {
        framework.on("event", kwargs -> null, "callback");
        framework.trigger("event");
        assertFalse(framework.getMetrics().isEmpty());

        framework.resetMetrics();

        assertTrue(framework.getMetrics().isEmpty());
    }

    @Test
    @DisplayName("test_get_slow_callbacks")
    void testGetSlowCallbacks() {
        framework.on("event", kwargs -> {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }, "slow_callback");
        framework.trigger("event");

        List<Map<String, Object>> slowCallbacks = framework.getSlowCallbacks(0.0);

        assertFalse(slowCallbacks.isEmpty());
        assertEquals("event:slow_callback", slowCallbacks.get(0).get("callback"));
    }

    @Test
    @DisplayName("test_history_disabled_by_default")
    void testHistoryDisabledByDefault() {
        framework.on("event", kwargs -> null, "callback");
        framework.trigger("event");

        assertTrue(framework.getEventHistory(null, null).isEmpty());
    }

    @Test
    @DisplayName("test_enable_history")
    void testEnableHistory() {
        framework.enableEventHistory(true);
        framework.on("event", kwargs -> null, "callback");

        framework.trigger("event");

        List<Map<String, Object>> history = framework.getEventHistory(null, null);
        assertEquals(1, history.size());
        assertEquals("event", history.get(0).get("event"));
    }

    @Test
    @DisplayName("test_history_filter_by_event")
    void testHistoryFilterByEvent() {
        framework.enableEventHistory(true);
        framework.on("event1", kwargs -> null, "cb1");
        framework.on("event2", kwargs -> null, "cb2");
        framework.trigger("event1");
        framework.trigger("event2");

        List<Map<String, Object>> history = framework.getEventHistory("event1", null);

        assertEquals(1, history.size());
        assertEquals("event1", history.get(0).get("event"));
    }

    @Test
    @DisplayName("test_history_filter_by_since")
    void testHistoryFilterBySince() throws InterruptedException {
        framework.enableEventHistory(true);
        framework.on("event", kwargs -> null, "callback");
        framework.trigger("event");
        Thread.sleep(20);
        long since = System.currentTimeMillis();
        Thread.sleep(20);
        framework.trigger("event");

        List<Map<String, Object>> history = framework.getEventHistory(null, since);

        assertEquals(1, history.size());
    }

    @Test
    @DisplayName("test_replay_events")
    void testReplayEvents() {
        int[] callCount = {0};
        framework.enableEventHistory(true);
        framework.on("event", kwargs -> {
            callCount[0]++;
            return null;
        }, "callback");
        framework.trigger("event");
        framework.enableEventHistory(false);

        framework.replayEvents(null);

        assertEquals(2, callCount[0]);
    }

    @Test
    @DisplayName("test_save_state_creates_file")
    void testSaveStateCreatesFile() throws Exception {
        Path stateFile = Files.createTempFile("callback-state", ".json");

        framework.saveState(stateFile.toString());

        assertTrue(Files.exists(stateFile));
        assertTrue(Files.size(stateFile) > 0);
    }

    @Test
    @DisplayName("test_save_state_contains_metrics")
    void testSaveStateContainsMetrics() throws Exception {
        framework.on("event", kwargs -> "done", "callback");
        framework.trigger("event");
        Path stateFile = Files.createTempFile("callback-state-metrics", ".json");

        framework.saveState(stateFile.toString());

        String content = Files.readString(stateFile);
        assertTrue(content.contains("\"metrics\""));
        assertTrue(content.contains("event:callback"));
    }

    @Test
    @DisplayName("test_save_state_contains_callbacks")
    void testSaveStateContainsCallbacks() throws Exception {
        framework.on("event", kwargs -> "done", "callback");
        Path stateFile = Files.createTempFile("callback-state-callbacks", ".json");

        framework.saveState(stateFile.toString());

        String content = Files.readString(stateFile);
        assertTrue(content.contains("\"callbacks\""));
        assertTrue(content.contains("\"callback\""));
    }

    @Test
    @DisplayName("test_save_state_contains_history")
    void testSaveStateContainsHistory() throws Exception {
        framework.enableEventHistory(true);
        framework.on("event", kwargs -> "done", "callback");
        framework.trigger("event");
        Path stateFile = Files.createTempFile("callback-state-history", ".json");

        framework.saveState(stateFile.toString());

        String content = Files.readString(stateFile);
        assertTrue(content.contains("\"history\""));
        assertTrue(content.contains("\"event\""));
    }

    @Test
    @DisplayName("test_get_statistics_basic")
    void testGetStatisticsBasic() {
        Map<String, Object> stats = framework.getStatistics();

        assertEquals(0, stats.get("total_events"));
        assertEquals(0, stats.get("total_callbacks"));
        assertTrue(((List<?>) stats.get("namespaces")).isEmpty());
    }

    @Test
    @DisplayName("test_get_statistics_counts")
    void testGetStatisticsCounts() {
        framework.register("event1", kwargs -> null, 0, false, "ns1", null,
                null, null, null, 0, 0.0, null, "cb1");
        framework.register("event2", kwargs -> null, 0, false, "ns2", null,
                null, null, null, 0, 0.0, null, "cb2");

        Map<String, Object> stats = framework.getStatistics();

        assertEquals(2, stats.get("total_events"));
        assertEquals(2, stats.get("total_callbacks"));
        assertTrue(((List<?>) stats.get("namespaces")).containsAll(List.of("ns1", "ns2")));
    }

    @Test
    @DisplayName("test_list_events")
    void testListEvents() {
        framework.on("event1", kwargs -> null, "cb1");
        framework.on("event2", kwargs -> null, "cb2");

        List<String> events = framework.listEvents(null);

        assertTrue(events.containsAll(List.of("event1", "event2")));
    }

    @Test
    @DisplayName("test_list_events_filter_by_namespace")
    void testListEventsFilterByNamespace() {
        framework.register("event1", kwargs -> null, 0, false, "ns1", null,
                null, null, null, 0, 0.0, null, "cb1");
        framework.register("event2", kwargs -> null, 0, false, "ns2", null,
                null, null, null, 0, 0.0, null, "cb2");

        List<String> events = framework.listEvents("ns1");

        assertEquals(List.of("event1"), events);
    }

    @Test
    @DisplayName("test_list_callbacks")
    void testListCallbacks() {
        framework.register("event", kwargs -> null, 10, true, "ns",
                java.util.Set.of("tag"), null, null, null, 2, 0.1, 3.0, "callback");

        Map<String, Object> callback = framework.listCallbacks("event").get(0);

        assertEquals("callback", callback.get("name"));
        assertEquals(10, callback.get("priority"));
        assertEquals("ns", callback.get("namespace"));
        assertEquals(true, callback.get("once"));
        assertEquals(2, callback.get("max_retries"));
        assertEquals(3.0, callback.get("timeout"));
    }

    @Test
    @DisplayName("test_list_callbacks_empty_event")
    void testListCallbacksEmptyEvent() {
        assertTrue(framework.listCallbacks("missing").isEmpty());
    }
}
