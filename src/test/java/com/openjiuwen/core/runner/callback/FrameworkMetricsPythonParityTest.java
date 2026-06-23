/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_framework_metrics} in
 * {@code tests/unit_tests/core/runner/callback/test_framework_metrics.py}.
 */
class FrameworkMetricsPythonParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TestFactory
    List<DynamicTest> pythonFrameworkMetricsParityCases() {
        return List.of(
                parity("test_metrics_enabled_collects_data", this::metricsEnabledCollectsData),
                parity("test_metrics_disabled_no_collection", this::metricsDisabledNoCollection),
                parity("test_metrics_tracks_call_count", this::metricsTracksCallCount),
                parity("test_metrics_tracks_timing", this::metricsTracksTiming),
                parity("test_metrics_tracks_errors", this::metricsTracksErrors),
                parity("test_get_metrics_filter_by_event", this::getMetricsFilterByEvent),
                parity("test_get_metrics_filter_by_callback", this::getMetricsFilterByCallback),
                parity("test_reset_metrics", this::resetMetrics),
                parity("test_get_slow_callbacks", this::getSlowCallbacks),
                parity("test_history_disabled_by_default", this::historyDisabledByDefault),
                parity("test_enable_history", this::enableHistory),
                parity("test_history_filter_by_event", this::historyFilterByEvent),
                parity("test_history_filter_by_since", this::historyFilterBySince),
                parity("test_replay_events", this::replayEvents),
                parity("test_save_state_creates_file", this::saveStateCreatesFile),
                parity("test_save_state_contains_metrics", this::saveStateContainsMetrics),
                parity("test_save_state_contains_callbacks", this::saveStateContainsCallbacks),
                parity("test_save_state_contains_history", this::saveStateContainsHistory),
                parity("test_get_statistics_basic", this::getStatisticsBasic),
                parity("test_get_statistics_counts", this::getStatisticsCounts),
                parity("test_list_events", this::listEvents),
                parity("test_list_events_filter_by_namespace", this::listEventsFilterByNamespace),
                parity("test_list_callbacks", this::listCallbacks),
                parity("test_list_callbacks_empty_event", this::listCallbacksEmptyEvent)
        );
    }

    private void metricsEnabledCollectsData() {
        AsyncCallbackFramework framework = frameworkWithMetrics();
        framework.on("event").apply(named("callback", kwargs -> {
            sleepQuietly(10L);
            return "done";
        }));

        framework.triggerResults("event");

        Map<String, Map<String, Object>> metrics = framework.getMetrics();
        assertThat(metrics).isNotEmpty().containsKey("event:callback");
    }

    private void metricsDisabledNoCollection() {
        AsyncCallbackFramework framework = framework();
        framework.on("event").apply(named("callback", kwargs -> "done"));

        framework.triggerResults("event");

        assertThat(framework.getMetrics()).isEmpty();
    }

    private void metricsTracksCallCount() {
        AsyncCallbackFramework framework = frameworkWithMetrics();
        framework.on("event").apply(named("callback", kwargs -> "done"));

        for (int index = 0; index < 5; index++) {
            framework.triggerResults("event");
        }

        assertThat(framework.getMetrics("event", "callback").get("event:callback"))
                .containsEntry("call_count", 5);
    }

    private void metricsTracksTiming() {
        AsyncCallbackFramework framework = frameworkWithMetrics();
        framework.on("event").apply(named("slow_callback", kwargs -> {
            sleepQuietly(50L);
            return "done";
        }));

        framework.triggerResults("event");

        Map<String, Object> metric = framework.getMetrics().get("event:slow_callback");
        assertThat((Double) metric.get("avg_time")).isGreaterThanOrEqualTo(0.05d);
        assertThat((Double) metric.get("min_time")).isGreaterThanOrEqualTo(0.05d);
        assertThat((Double) metric.get("max_time")).isGreaterThanOrEqualTo(0.05d);
    }

    private void metricsTracksErrors() {
        AsyncCallbackFramework framework = frameworkWithMetrics();
        framework.on("event").apply(named("failing_callback", kwargs -> {
            throw new IllegalArgumentException("Error!");
        }));

        framework.triggerResults("event");
        framework.triggerResults("event");
        framework.triggerResults("event");

        Map<String, Object> metric = framework.getMetrics().get("event:failing_callback");
        assertThat(metric)
                .containsEntry("call_count", 3)
                .containsEntry("error_count", 3)
                .containsEntry("error_rate", 1.0d);
    }

    private void getMetricsFilterByEvent() {
        AsyncCallbackFramework framework = frameworkWithMetrics();
        framework.on("event1").apply(named("cb1", kwargs -> null));
        framework.on("event2").apply(named("cb2", kwargs -> null));

        framework.triggerResults("event1");
        framework.triggerResults("event2");

        Map<String, Map<String, Object>> metrics = framework.getMetrics("event1", null);
        assertThat(metrics).hasSize(1).containsKey("event1:cb1");
    }

    private void getMetricsFilterByCallback() {
        AsyncCallbackFramework framework = frameworkWithMetrics();
        framework.on("event").apply(named("callback_a", kwargs -> null));
        framework.on("event").apply(named("callback_b", kwargs -> null));

        framework.triggerResults("event");

        Map<String, Map<String, Object>> metrics = framework.getMetrics(null, "callback_a");
        assertThat(metrics).hasSize(1).containsKey("event:callback_a");
    }

    private void resetMetrics() {
        AsyncCallbackFramework framework = frameworkWithMetrics();
        framework.on("event").apply(named("callback", kwargs -> null));

        framework.triggerResults("event");
        assertThat(framework.getMetrics()).isNotEmpty();

        framework.resetMetrics();

        assertThat(framework.getMetrics()).isEmpty();
    }

    private void getSlowCallbacks() {
        AsyncCallbackFramework framework = frameworkWithMetrics();
        framework.on("event").apply(named("fast_callback", kwargs -> {
            sleepQuietly(10L);
            return null;
        }));
        framework.on("event").apply(named("slow_callback", kwargs -> {
            sleepQuietly(100L);
            return null;
        }));

        framework.triggerResults("event");

        List<Map<String, Object>> slow = framework.getSlowCallbacks(0.05d);
        assertThat(slow).hasSize(1);
        assertThat(slow.get(0)).containsEntry("callback", "event:slow_callback");
    }

    private void historyDisabledByDefault() {
        AsyncCallbackFramework framework = framework();
        framework.on("event").apply(named("callback", kwargs -> null));

        framework.triggerResults("event");

        assertThat(framework.getEventHistory(null, null)).isEmpty();
    }

    private void enableHistory() {
        AsyncCallbackFramework framework = framework();
        framework.enableEventHistory(true);
        framework.on("event").apply(named("callback", kwargs -> null));

        framework.triggerResults("event", Map.of("message", "hello"));

        List<Map<String, Object>> history = framework.getEventHistory(null, null);
        assertThat(history).hasSize(1);
        assertThat(history.get(0)).containsEntry("event", "event");
        assertThat(((Map<?, ?>) history.get(0).get("kwargs")).get("message")).isEqualTo("hello");
    }

    private void historyFilterByEvent() {
        AsyncCallbackFramework framework = framework();
        framework.enableEventHistory(true);
        framework.on("event1").apply(named("cb1", kwargs -> null));
        framework.on("event2").apply(named("cb2", kwargs -> null));

        framework.triggerResults("event1");
        framework.triggerResults("event2");
        framework.triggerResults("event1");

        assertThat(framework.getEventHistory("event1", null)).hasSize(2);
    }

    private void historyFilterBySince() {
        AsyncCallbackFramework framework = framework();
        framework.enableEventHistory(true);
        framework.on("event").apply(named("callback", kwargs -> null));

        framework.triggerResults("event");
        sleepQuietly(100L);

        double sinceTime = (System.currentTimeMillis() - 50L) / 1000.0d;

        framework.triggerResults("event");

        assertThat(framework.getEventHistory(null, sinceTime)).hasSize(1);
    }

    private void replayEvents() {
        AsyncCallbackFramework framework = framework();
        framework.enableEventHistory(true);
        AtomicInteger callCount = new AtomicInteger();
        framework.on("event").apply(named("callback", kwargs -> {
            callCount.incrementAndGet();
            return null;
        }));

        framework.triggerResults("event", Map.of("value", 1));
        framework.triggerResults("event", Map.of("value", 2));
        assertThat(callCount).hasValue(2);

        framework.replayEvents(null);

        assertThat(callCount).hasValue(4);
    }

    private void saveStateCreatesFile() throws IOException {
        AsyncCallbackFramework framework = frameworkWithMetrics();
        framework.on("event").apply(named("callback", kwargs -> null));
        framework.triggerResults("event");

        Path filepath = Files.createTempFile("callback-state-", ".json");
        try {
            framework.saveState(filepath.toString());
            assertThat(filepath).exists();
        } finally {
            Files.deleteIfExists(filepath);
        }
    }

    private void saveStateContainsMetrics() throws IOException {
        AsyncCallbackFramework framework = frameworkWithMetrics();
        framework.on("event").apply(named("callback", kwargs -> null));
        framework.triggerResults("event");

        Path filepath = Files.createTempFile("callback-state-", ".json");
        try {
            framework.saveState(filepath.toString());
            Map<String, Object> state = readState(filepath);
            assertThat(state).containsKey("metrics");
            assertThat(((Map<?, ?>) state.get("metrics")).containsKey("event:callback")).isTrue();
        } finally {
            Files.deleteIfExists(filepath);
        }
    }

    private void saveStateContainsCallbacks() throws IOException {
        AsyncCallbackFramework framework = framework();
        framework.on("event", 10, false, "custom", Set.of("tag1"), List.of(), 0, 0.0, null, "")
                .apply(named("my_callback", kwargs -> null));

        Path filepath = Files.createTempFile("callback-state-", ".json");
        try {
            framework.saveState(filepath.toString());
            Map<String, Object> state = readState(filepath);
            assertThat(state).containsKey("callbacks");
            Map<?, ?> callbacks = (Map<?, ?>) state.get("callbacks");
            assertThat(callbacks.containsKey("event")).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> eventCallbacks = (List<Map<String, Object>>) callbacks.get("event");
            Map<String, Object> callbackInfo = eventCallbacks.get(0);
            assertThat(callbackInfo)
                    .containsEntry("name", "my_callback")
                    .containsEntry("priority", 10)
                    .containsEntry("namespace", "custom");
            assertThat(callbackInfo.get("tags").toString()).contains("tag1");
        } finally {
            Files.deleteIfExists(filepath);
        }
    }

    private void saveStateContainsHistory() throws IOException {
        AsyncCallbackFramework framework = framework();
        framework.enableEventHistory(true);
        framework.on("event").apply(named("callback", kwargs -> null));
        framework.triggerResults("event", Map.of("value", 42));

        Path filepath = Files.createTempFile("callback-state-", ".json");
        try {
            framework.saveState(filepath.toString());
            Map<String, Object> state = readState(filepath);
            assertThat(state).containsKey("history");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> history = (List<Map<String, Object>>) state.get("history");
            assertThat(history).hasSize(1);
            assertThat(history.get(0)).containsEntry("event", "event");
        } finally {
            Files.deleteIfExists(filepath);
        }
    }

    private void getStatisticsBasic() {
        Map<String, Object> stats = framework().getStatistics();

        assertThat(stats).containsKeys(
                "total_events", "total_callbacks", "namespaces",
                "total_filters", "total_chains", "history_size", "metrics_collected"
        );
    }

    private void getStatisticsCounts() {
        AsyncCallbackFramework framework = framework();
        framework.on("event1").apply(named("cb1", kwargs -> null));
        framework.on("event1").apply(named("cb2", kwargs -> null));
        framework.on("event2", 0, false, "custom", Set.of(), List.of(), 0, 0.0, null, "")
                .apply(named("cb3", kwargs -> null));

        Map<String, Object> stats = framework.getStatistics();

        assertThat(stats)
                .containsEntry("total_events", 2)
                .containsEntry("total_callbacks", 3);
        assertThat(stats.get("namespaces").toString()).contains("default", "custom");
    }

    private void listEvents() {
        AsyncCallbackFramework framework = framework();
        framework.on("event1").apply(named("cb1", kwargs -> null));
        framework.on("event2").apply(named("cb2", kwargs -> null));

        assertThat(framework.listEvents(null).stream().map(String::valueOf).toList()).contains("event1", "event2");
    }

    private void listEventsFilterByNamespace() {
        AsyncCallbackFramework framework = framework();
        framework.on("event1", 0, false, "ns1", Set.of(), List.of(), 0, 0.0, null, "")
                .apply(named("cb1", kwargs -> null));
        framework.on("event2", 0, false, "ns2", Set.of(), List.of(), 0, 0.0, null, "")
                .apply(named("cb2", kwargs -> null));

        assertThat(framework.listEvents("ns1").stream().map(String::valueOf).toList()).containsExactly("event1");
    }

    private void listCallbacks() {
        AsyncCallbackFramework framework = framework();
        framework.on("event", 10, false, "default", Set.of("tag1"), List.of(), 0, 0.0, null, "")
                .apply(named("my_callback", kwargs -> null));

        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        assertThat(callbacks).hasSize(1);
        assertThat(callbacks.get(0)).containsEntry("name", "my_callback").containsEntry("priority", 10);
        assertThat(callbacks.get(0).get("tags").toString()).contains("tag1");
    }

    private void listCallbacksEmptyEvent() {
        assertThat(framework().listCallbacks("nonexistent")).isEmpty();
    }

    private DynamicTest parity(String pythonTestName, ThrowingRunnable runnable) {
        return DynamicTest.dynamicTest("Python parity: " + pythonTestName, () -> runnable.run());
    }

    private static AsyncCallbackFramework framework() {
        return new AsyncCallbackFramework(false, false);
    }

    private static AsyncCallbackFramework frameworkWithMetrics() {
        return new AsyncCallbackFramework(true, false);
    }

    private static Map<String, Object> readState(Path filepath) throws IOException {
        return OBJECT_MAPPER.readValue(filepath.toFile(), new TypeReference<>() {
        });
    }

    private static Function<Map<String, Object>, Object> named(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) {
        return new NamedCallback(name, delegate);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record NamedCallback(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) implements Function<Map<String, Object>, Object> {

        @Override
        public Object apply(Map<String, Object> kwargs) {
            return delegate.apply(kwargs);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
