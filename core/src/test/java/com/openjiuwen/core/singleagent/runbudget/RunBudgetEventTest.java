/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import com.openjiuwen.core.common.logging.events.BaseLogEvent;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.singleagent.agents.ReActAgent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Event-surface tests for FEAT-057: all eleven LogEventType values are registered, the
 * guarantee event payload carries the contract keys, terminated events log at WARNING, and
 * the facade degrades cleanly for host-registered custom loggers.
 */
class RunBudgetEventTest {
    private static final List<String> ALL_TYPES = List.of(
            "turn_budget_guarantee_effective",
            "turn_budget_checkpoint",
            "turn_budget_extended",
            "turn_budget_stall_prompted",
            "turn_budget_gentle_reminder",
            "turn_budget_safety_valve_triggered",
            "turn_budget_final_round_granted",
            "turn_budget_terminated",
            "time_budget_effective",
            "time_budget_near_deadline_prompted",
            "time_budget_terminated");

    private final List<String> toolIds = new ArrayList<>();
    private ReActAgent agent;
    private RunBudgetRail rail;

    @AfterEach
    void tearDown() {
        RunBudgetTestFixture.unregisterRail(agent, rail);
        RunBudgetTestFixture.cleanupTools(toolIds);
    }

    @Test
    void allElevenEventTypes_registeredInLogEventType() {
        // Given / When / Then every contract event value round-trips through fromValue
        assertThat(LogEventType.fromValue("turn_budget_guarantee_effective"))
                .isEqualTo(LogEventType.TURN_BUDGET_GUARANTEE_EFFECTIVE);
        for (String typeValue : ALL_TYPES) {
            assertThat(LogEventType.fromValue(typeValue)).as("event type %s", typeValue).isNotNull();
        }
    }

    @Test
    void guaranteeEvent_payloadCarriesContractKeys() {
        // Given a budgeted invocation
        agent = RunBudgetTestFixture.newAgent("event-guarantee", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.answer("done")).model());

        // When invoked
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                    RunBudgetTestFixture.newSession("evt-s1", agent));

            // Then the payload carries identity and guarantee fields
            List<Map<String, Object>> payloads = events.payloadsOf("turn_budget_guarantee_effective");
            assertThat(payloads).hasSize(1);
            Map<String, Object> payload = payloads.get(0);
            assertThat(payload).containsKeys("event_type", "conversation_id", "agent_name", "agent_id",
                    "guaranteed_rounds", "source", "hard_limit", "module_type");
            assertThat(payload).containsEntry("module_type", "agent");
            assertThat(payload).containsEntry("agent_id", "event-guarantee");
        }
    }

    @Test
    void terminatedEvents_logAtWarningLevel() {
        // Given a 1s time budget with a slow tool-looping model
        agent = RunBudgetTestFixture.newAgent("event-terminated", 10);
        rail = RunBudgetTestFixture.registerBudgetRail(agent, RunBudgetConfig.builder()
                .turnEnabled(false).totalBudgetSeconds(1).build());
        AtomicInteger toolCalls = new AtomicInteger();
        String toolId = RunBudgetTestFixture.registerEchoTool(agent, "evt", toolIds, 0L, toolCalls);
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.toolCall("slow", toolId, 999), 600L).model());

        // When the budget expires
        try (RunBudgetTestFixture.RecordingEvents events = RunBudgetTestFixture.RecordingEvents.install()) {
            RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                    RunBudgetTestFixture.newSession("evt-s2", agent));

            // Then the termination event is logged at WARNING with the contract payload
            List<Map<String, Object>> payloads = events.payloadsOf("time_budget_terminated");
            assertThat(payloads).hasSize(1);
            assertThat(payloads.get(0)).containsEntry("log_level", "WARNING")
                    .containsEntry("reason", "time_budget_exhausted")
                    .containsEntry("terminated_at", "model_call");
        }
    }

    @Test
    void defaultLoggerMainPath_eventObjectPassesThroughIntact() {
        // Given the real DefaultLogger branch of the facade (captured via subclass)
        agent = RunBudgetTestFixture.newAgent("event-main-path", 201);
        rail = RunBudgetTestFixture.registerBudgetRail(agent,
                RunBudgetConfig.builder().turnEnabled(true).suggestedRounds(10).build());
        agent.setLlm(RunBudgetTestFixture.scriptedModel(
                List.of(), RunBudgetTestFixture.answer("done")).model());
        LoggerProtocol original = LogManager.getLogger("agent");
        RecordingDefaultLogger recording = new RecordingDefaultLogger();
        LogManager.registerLogger("agent", recording);

        try {
            // When invoked
            RunBudgetTestFixture.runSync(agent, Map.of("query", "hi"),
                    RunBudgetTestFixture.newSession("evt-s3", agent));

            // Then logEvent received the typed event object with the right type
            assertThat(recording.eventTypes).contains(LogEventType.TURN_BUDGET_GUARANTEE_EFFECTIVE);
            assertThat(recording.events).allSatisfy(event -> assertThat(event)
                    .isInstanceOf(RunBudgetEvent.class));
        } finally {
            LogManager.registerLogger("agent", original);
        }
    }

    /**
     * Recording {@link DefaultLogger} double capturing structured logEvent calls.
     */
    private static final class RecordingDefaultLogger extends DefaultLogger {
        private final List<LogEventType> eventTypes = new CopyOnWriteArrayList<>();
        private final List<BaseLogEvent> events = new CopyOnWriteArrayList<>();

        private RecordingDefaultLogger() {
            super("agent", Map.of());
        }

        /**
         * Records the structured event instead of writing it out.
         *
         * @param msg log message
         * @param eventType event type
         * @param event event payload
         */
        @Override
        public void logEvent(String msg, LogEventType eventType, BaseLogEvent event) {
            eventTypes.add(eventType);
            events.add(event);
        }
    }
}
