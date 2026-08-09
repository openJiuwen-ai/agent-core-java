/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.HeartbeatReason;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.RunContext;
import com.openjiuwen.core.singleagent.rail.RunKind;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.HeartbeatSection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_heartbeat_rail} in
 * {@code tests/unit_tests/harness/test_heartbeat_rail.py}.
 */
class HeartbeatRailPythonParityTest {

    @TestFactory
    Collection<DynamicTest> heartbeatRailPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "test_init_sets_system_prompt_builder", this::initSetsSystemPromptBuilder);
        add(tests, "test_init_sets_sys_operation", this::initSetsSysOperation);
        add(tests, "test_uninit_removes_heartbeat_section", this::uninitRemovesHeartbeatSection);
        add(tests, "test_uninit_without_system_prompt_builder", this::uninitWithoutSystemPromptBuilder);
        add(tests, "test_priority_is_80", this::priorityIs80);
        add(tests, "test_before_invoke_skips_non_heartbeat", this::beforeInvokeSkipsNonHeartbeat);
        add(tests, "test_before_invoke_skips_non_invoke_inputs", this::beforeInvokeSkipsNonInvokeInputs);
        add(tests, "test_before_model_call_injects_heartbeat_section", this::beforeModelCallInjectsHeartbeatSection);
        add(tests, "test_before_model_call_skips_non_heartbeat", this::beforeModelCallSkipsNonHeartbeat);
        add(tests, "test_before_model_call_without_system_prompt_builder", this::beforeModelCallWithoutBuilder);
        add(tests, "test_invoke_inputs_is_heartbeat", this::invokeInputsIsHeartbeat);
        add(tests, "test_invoke_inputs_is_not_heartbeat", this::invokeInputsIsNotHeartbeat);
        add(tests, "test_invoke_inputs_is_lightweight_context", this::invokeInputsIsLightweightContext);
        add(tests, "test_invoke_inputs_is_not_lightweight_context", this::invokeInputsIsNotLightweightContext);
        add(tests, "test_invoke_inputs_is_lightweight_context_without_context",
                this::invokeInputsIsLightweightContextWithoutContext);
        add(tests, "test_run_kind_enum", this::runKindEnum);
        add(tests, "test_heartbeat_reason_enum", this::heartbeatReasonEnum);
        add(tests, "test_run_context_dataclass", this::runContextDataclass);
        add(tests, "test_run_context_defaults", this::runContextDefaults);
        add(tests, "test_build_heartbeat_section_instructs_direct_return",
                this::buildHeartbeatSectionInstructsDirectReturn);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, Executable executable) {
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private void initSetsSystemPromptBuilder() {
        HeartbeatRail rail = makeRail();

        rail.init(makeAgent(new Object()));

        assertNotNull(rail.getSystemPromptBuilder());
    }

    private void initSetsSysOperation() {
        Object operation = new Object();
        HeartbeatRail rail = makeRail();

        rail.init(makeAgent(operation));

        assertSame(operation, rail.getSysOperation());
    }

    private void uninitRemovesHeartbeatSection() {
        HeartbeatRail rail = initializedRail();
        CallbackContext ctx = ctx("run_kind", RunKind.HEARTBEAT);
        rail.beforeModelCall(ctx);
        assertTrue(rail.getSystemPromptBuilder().hasSection(SectionName.HEARTBEAT));

        rail.uninit(makeAgent(new Object()));

        assertFalse(rail.getSystemPromptBuilder().hasSection(SectionName.HEARTBEAT));
    }

    private void uninitWithoutSystemPromptBuilder() {
        HeartbeatRail rail = makeRail();
        rail.setSystemPromptBuilder(null);

        assertDoesNotThrow(() -> rail.uninit(makeAgent(null)));
    }

    private void priorityIs80() {
        assertEquals(80, makeRail().getPriority());
    }

    private void beforeInvokeSkipsNonHeartbeat() {
        HeartbeatRail rail = initializedRail();
        InvokeInputs inputs = new InvokeInputs();
        inputs.setQuery("test query");
        inputs.setRunKind(RunKind.NORMAL);
        CallbackContext ctx = ctx("inputs", inputs);

        rail.beforeInvoke(ctx);

        assertNull(ctx.get("run_kind"));
        assertNull(ctx.get("run_context"));
    }

    private void beforeInvokeSkipsNonInvokeInputs() {
        HeartbeatRail rail = initializedRail();
        CallbackContext ctx = ctx("inputs", Map.of("query", "test"));

        rail.beforeInvoke(ctx);

        assertNull(ctx.get("run_kind"));
    }

    private void beforeModelCallInjectsHeartbeatSection() {
        HeartbeatRail rail = initializedRail();
        RunContext runContext = runContext(HeartbeatReason.INTERVAL, "test-session", null, Map.of());
        CallbackContext ctx = ctx("run_kind", RunKind.HEARTBEAT, "run_context", runContext);

        rail.beforeModelCall(ctx);

        assertSame(RunKind.HEARTBEAT, ctx.get("run_kind"));
        assertNotNull(ctx.get("heartbeat_section"));
        assertTrue(rail.getSystemPromptBuilder().hasSection(SectionName.HEARTBEAT));
    }

    private void beforeModelCallSkipsNonHeartbeat() {
        HeartbeatRail rail = initializedRail();
        CallbackContext ctx = ctx("run_kind", RunKind.NORMAL);

        rail.beforeModelCall(ctx);

        assertNull(ctx.get("heartbeat_section"));
        assertFalse(rail.getSystemPromptBuilder().hasSection(SectionName.HEARTBEAT));
    }

    private void beforeModelCallWithoutBuilder() {
        HeartbeatRail rail = makeRail();
        rail.setSystemPromptBuilder(null);

        assertDoesNotThrow(() -> rail.beforeModelCall(ctx("run_kind", RunKind.HEARTBEAT)));
    }

    private void invokeInputsIsHeartbeat() {
        InvokeInputs inputs = new InvokeInputs();
        inputs.setRunKind(RunKind.HEARTBEAT);
        inputs.setRunContext(runContext(HeartbeatReason.INTERVAL, null, null, Map.of()));

        assertTrue(inputs.isHeartbeat());
    }

    private void invokeInputsIsNotHeartbeat() {
        InvokeInputs inputs = new InvokeInputs();
        inputs.setRunKind(RunKind.NORMAL);

        assertFalse(inputs.isHeartbeat());
    }

    private void invokeInputsIsLightweightContext() {
        InvokeInputs inputs = new InvokeInputs();
        inputs.setRunKind(RunKind.HEARTBEAT);
        inputs.setRunContext(runContext(HeartbeatReason.INTERVAL, null, "lightweight", Map.of()));

        assertTrue(inputs.isLightweightContext());
    }

    private void invokeInputsIsNotLightweightContext() {
        InvokeInputs inputs = new InvokeInputs();
        inputs.setRunKind(RunKind.HEARTBEAT);
        inputs.setRunContext(runContext(HeartbeatReason.INTERVAL, null, "full", Map.of()));

        assertFalse(inputs.isLightweightContext());
    }

    private void invokeInputsIsLightweightContextWithoutContext() {
        InvokeInputs inputs = new InvokeInputs();
        inputs.setRunKind(RunKind.HEARTBEAT);

        assertFalse(inputs.isLightweightContext());
    }

    private void runKindEnum() {
        assertEquals("normal", RunKind.NORMAL.getValue());
        assertEquals("heartbeat", RunKind.HEARTBEAT.getValue());
    }

    private void heartbeatReasonEnum() {
        assertEquals("interval", HeartbeatReason.INTERVAL.getValue());
        assertEquals("manual", HeartbeatReason.MANUAL.getValue());
    }

    private void runContextDataclass() {
        RunContext context = runContext(
                HeartbeatReason.INTERVAL,
                "test-session",
                "lightweight",
                Map.of("key", "value")
        );

        assertSame(HeartbeatReason.INTERVAL, context.getReason());
        assertEquals("test-session", context.getSessionId());
        assertEquals("lightweight", context.getContextMode());
        assertEquals(Map.of("key", "value"), context.getExtra());
    }

    private void runContextDefaults() {
        RunContext context = new RunContext();

        assertNull(context.getReason());
        assertNull(context.getSessionId());
        assertNull(context.getContextMode());
        assertEquals(Map.of(), context.getExtra());
    }

    private void buildHeartbeatSectionInstructsDirectReturn() {
        PromptSection sectionCn = HeartbeatSection.build("cn");
        PromptSection sectionEn = HeartbeatSection.build("en");

        assertTrue(sectionCn.render("cn").contains("心跳执行结果必须直接返回"));
        assertTrue(sectionCn.render("cn").contains("不要写入 daily memory 或其他记忆文件"));
        assertTrue(sectionEn.render("en").contains("Return heartbeat execution results directly"));
        assertTrue(sectionEn.render("en").contains("do not write them to daily memory or other memory files"));
    }

    private static HeartbeatRail makeRail() {
        return new HeartbeatRail();
    }

    private static HeartbeatRail initializedRail() {
        HeartbeatRail rail = makeRail();
        rail.init(makeAgent(new Object()));
        return rail;
    }

    private static DeepAgent makeAgent(Object sysOperation) {
        DeepAgent agent = new DeepAgent(new AgentCard("deep", "deep", "test"));
        DeepAgentConfig config = new DeepAgentConfig();
        config.setEnableTaskLoop(true);
        config.setSysOperation(sysOperation);
        agent.configure(config);
        return agent;
    }

    private static CallbackContext ctx(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return new CallbackContext(makeAgent(null), map);
    }

    private static RunContext runContext(
            HeartbeatReason reason,
            String sessionId,
            String contextMode,
            Map<String, Object> extra
    ) {
        RunContext context = new RunContext();
        context.setReason(reason);
        context.setSessionId(sessionId);
        context.setContextMode(contextMode);
        context.setExtra(extra);
        return context;
    }
}
