/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.HeartbeatReason;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.RunContext;
import com.openjiuwen.core.singleagent.rail.RunKind;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.prompts.sections.HeartbeatSection;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.HeartbeatRail;
import com.openjiuwen.harness.workspace.Workspace;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_heartbeat_rail} in
 * {@code tests.unit_tests.harness.test_heartbeat_rail}.
 */
class TestHeartbeatRail {

    @TempDir
    Path tempDir;

    @Test
    @Tag("level0")
    @DisplayName("init stores system prompt builder from agent")
    void testInitSetsSystemPromptBuilder() {
        HeartbeatRail rail = new HeartbeatRail();
        DeepAgent agent = makeAgent(tempDir);

        rail.init(agent);

        assertNotNull(readField(rail, "systemPromptBuilder"));
    }

    @Test
    @Tag("level0")
    @DisplayName("init populates sys operation from deep config")
    void testInitSetsSysOperation() {
        HeartbeatRail rail = new HeartbeatRail();
        DeepAgent agent = makeAgent(tempDir);

        rail.init(agent);

        assertNotNull(readInheritedField(rail, DeepAgentRail.class, "sysOperation"));
    }

    @Test
    @Tag("level0")
    @DisplayName("uninit removes heartbeat section from prompt builder")
    void testUninitRemovesHeartbeatSection() throws Exception {
        HeartbeatRail rail = new HeartbeatRail();
        DeepAgent agent = makeAgent(tempDir);
        rail.init(agent);
        Files.writeString(tempDir.resolve("HEARTBEAT.md"), "ping\n");

        AgentCallbackContext ctx = heartbeatCtx();
        rail.beforeInvoke(ctx);
        rail.beforeModelCall(ctx);
        assertTrue(agent.getSystemPromptBuilder().hasSection("heartbeat"));

        rail.uninit(agent);

        assertFalse(agent.getSystemPromptBuilder().hasSection("heartbeat"));
    }

    @Test
    @Tag("level0")
    @DisplayName("uninit is safe when system prompt builder is missing")
    void testUninitWithoutSystemPromptBuilder() {
        HeartbeatRail rail = new HeartbeatRail();
        DeepAgent agent = makeAgent(tempDir);

        writeField(rail, "systemPromptBuilder", null);

        assertDoesNotThrow(() -> rail.uninit(agent));
    }

    @Test
    @Tag("level0")
    @DisplayName("priority is 80")
    void testPriorityIs80() {
        HeartbeatRail rail = new HeartbeatRail();

        assertEquals(80, HeartbeatRail.PRIORITY);
        assertEquals(80, rail.getPriority());
    }

    @Test
    @Tag("level0")
    @DisplayName("beforeInvoke skips non-heartbeat runs")
    void testBeforeInvokeSkipsNonHeartbeat() {
        HeartbeatRail rail = new HeartbeatRail();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .inputs(InvokeInputs.builder().query("test").runKind(RunKind.NORMAL).build())
                .extra(new HashMap<>())
                .build();

        rail.beforeInvoke(ctx);

        assertFalse(ctx.getExtra().containsKey("run_kind"));
        assertFalse(ctx.getExtra().containsKey("run_context"));
    }

    @Test
    @Tag("level0")
    @DisplayName("beforeInvoke skips non invoke inputs")
    void testBeforeInvokeSkipsNonInvokeInputs() {
        HeartbeatRail rail = new HeartbeatRail();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .extra(new HashMap<>())
                .build();

        rail.beforeInvoke(ctx);

        assertTrue(ctx.getExtra().isEmpty());
    }

    @Test
    @Tag("level0")
    @DisplayName("beforeModelCall injects heartbeat section for heartbeat runs")
    void testBeforeModelCallInjectsHeartbeatSection() throws Exception {
        HeartbeatRail rail = new HeartbeatRail();
        DeepAgent agent = makeAgent(tempDir);
        rail.init(agent);
        Files.writeString(tempDir.resolve("HEARTBEAT.md"), "  Line 1  \n<!-- comment -->\nLine 2\n");

        AgentCallbackContext ctx = heartbeatCtx();
        rail.beforeInvoke(ctx);
        rail.beforeModelCall(ctx);

        SystemPromptBuilder builder = agent.getSystemPromptBuilder();
        assertTrue(builder.hasSection("heartbeat"));
        String rendered = builder.getSection("heartbeat").orElseThrow().render("en");
        assertTrue(rendered.contains("Line 1"));
        assertTrue(rendered.contains("Line 2"));
        assertFalse(rendered.contains("comment"));
    }

    @Test
    @Tag("level0")
    @DisplayName("beforeModelCall skips non-heartbeat runs")
    void testBeforeModelCallSkipsNonHeartbeat() {
        HeartbeatRail rail = new HeartbeatRail();
        DeepAgent agent = makeAgent(tempDir);
        rail.init(agent);

        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .inputs(InvokeInputs.builder().query("test").runKind(RunKind.NORMAL).build())
                .extra(new HashMap<>(Map.of("run_kind", RunKind.NORMAL)))
                .build();

        rail.beforeModelCall(ctx);

        assertFalse(agent.getSystemPromptBuilder().hasSection("heartbeat"));
    }

    @Test
    @Tag("level0")
    @DisplayName("beforeModelCall returns early without builder")
    void testBeforeModelCallWithoutSystemPromptBuilder() {
        HeartbeatRail rail = new HeartbeatRail();
        writeField(rail, "systemPromptBuilder", null);
        writeField(rail, "heartbeatPath", tempDir.resolve("HEARTBEAT.md"));
        writeInheritedField(rail, DeepAgentRail.class, "sysOperation", makeSysOperation(tempDir));

        assertDoesNotThrow(() -> rail.beforeModelCall(heartbeatCtx()));
    }

    @Test
    @Tag("level0")
    @DisplayName("invoke inputs report heartbeat mode")
    void testInvokeInputsIsHeartbeat() {
        InvokeInputs inputs = InvokeInputs.builder()
                .query("")
                .runKind(RunKind.HEARTBEAT)
                .runContext(RunContext.builder().reason(HeartbeatReason.INTERVAL).build())
                .build();

        assertTrue(inputs.isHeartbeat());
    }

    @Test
    @Tag("level0")
    @DisplayName("invoke inputs report non-heartbeat mode")
    void testInvokeInputsIsNotHeartbeat() {
        InvokeInputs inputs = InvokeInputs.builder().query("test").runKind(RunKind.NORMAL).build();

        assertFalse(inputs.isHeartbeat());
    }

    @Test
    @Tag("level0")
    @DisplayName("invoke inputs detect lightweight context")
    void testInvokeInputsIsLightweightContext() {
        InvokeInputs inputs = InvokeInputs.builder()
                .query("")
                .runKind(RunKind.HEARTBEAT)
                .runContext(RunContext.builder().contextMode("lightweight").build())
                .build();

        assertTrue(inputs.isLightweightContext());
    }

    @Test
    @Tag("level0")
    @DisplayName("invoke inputs detect non-lightweight context")
    void testInvokeInputsIsNotLightweightContext() {
        InvokeInputs inputs = InvokeInputs.builder()
                .query("")
                .runKind(RunKind.HEARTBEAT)
                .runContext(RunContext.builder().contextMode("full").build())
                .build();

        assertFalse(inputs.isLightweightContext());
    }

    @Test
    @Tag("level0")
    @DisplayName("invoke inputs treat missing run context as non-lightweight")
    void testInvokeInputsIsLightweightContextWithoutContext() {
        InvokeInputs inputs = InvokeInputs.builder().query("").runKind(RunKind.HEARTBEAT).build();

        assertFalse(inputs.isLightweightContext());
    }

    @Test
    @Tag("level0")
    @DisplayName("run kind enum has expected values")
    void testRunKindEnum() {
        assertEquals("normal", RunKind.NORMAL.getValue());
        assertEquals("heartbeat", RunKind.HEARTBEAT.getValue());
    }

    @Test
    @Tag("level0")
    @DisplayName("heartbeat reason enum has expected values")
    void testHeartbeatReasonEnum() {
        assertEquals("interval", HeartbeatReason.INTERVAL.getValue());
        assertEquals("manual", HeartbeatReason.MANUAL.getValue());
    }

    @Test
    @Tag("level0")
    @DisplayName("run context stores provided fields")
    void testRunContextDataclass() {
        RunContext context = RunContext.builder()
                .reason(HeartbeatReason.INTERVAL)
                .sessionId("test-session")
                .contextMode("lightweight")
                .extra(Map.of("key", "value"))
                .build();

        assertEquals(HeartbeatReason.INTERVAL, context.getReason());
        assertEquals("test-session", context.getSessionId());
        assertEquals("lightweight", context.getContextMode());
        assertEquals(Map.of("key", "value"), context.getExtra());
    }

    @Test
    @Tag("level0")
    @DisplayName("run context defaults are empty")
    void testRunContextDefaults() {
        RunContext context = new RunContext();

        assertNull(context.getReason());
        assertNull(context.getSessionId());
        assertNull(context.getContextMode());
        assertEquals(Map.of(), context.getExtra());
    }

    @Test
    @Tag("level0")
    @DisplayName("cleanHeartbeatContent removes html comments")
    void testCleanHeartbeatContentRemovesHtmlComments() {
        String result = HeartbeatSection.cleanHeartbeatContent(
                "Valid line 1\n<!-- comment -->\nValid line 2\n<!-- another -->\nValid line 3");

        assertFalse(result.contains("<!--"));
        assertTrue(result.contains("Valid line 1"));
        assertTrue(result.contains("Valid line 2"));
        assertTrue(result.contains("Valid line 3"));
    }

    @Test
    @Tag("level0")
    @DisplayName("cleanHeartbeatContent removes empty lines")
    void testCleanHeartbeatContentRemovesEmptyLines() {
        String result = HeartbeatSection.cleanHeartbeatContent("Line 1\n\nLine 2\n\n\nLine 3");

        assertEquals("Line 1\nLine 2\nLine 3", result);
    }

    @Test
    @Tag("level0")
    @DisplayName("cleanHeartbeatContent strips surrounding whitespace")
    void testCleanHeartbeatContentStripsWhitespace() {
        String result = HeartbeatSection.cleanHeartbeatContent("  Line 1  \n\tLine 2\t\n   Line 3   ");

        assertEquals("Line 1\nLine 2\nLine 3", result);
    }

    @Test
    @Tag("level0")
    @DisplayName("cleanHeartbeatContent handles mixed content")
    void testCleanHeartbeatContentMixedContent() {
        String result = HeartbeatSection.cleanHeartbeatContent(
                "  Valid line 1  \n<!-- Comment -->\n\tValid line 2\t\n\n<!-- Another -->\n   Valid line 3   ");

        assertEquals("Valid line 1\nValid line 2\nValid line 3", result);
    }

    private DeepAgent makeAgent(Path root) {
        AgentCard card = AgentCard.builder().name("deep").description("test").build();
        DeepAgent agent = new DeepAgent(card);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setWorkspace(new Workspace(root.toString(), "en"));
        config.setSysOperation(makeSysOperation(root));
        agent.configure(config);
        return agent;
    }

    private SysOperation makeSysOperation(Path root) {
        SysOperationCard card = SysOperationCard.builder()
                .id("heartbeat-test")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(root.toString()).build())
                .build();
        return new SysOperation(card);
    }

    private AgentCallbackContext heartbeatCtx() {
        InvokeInputs inputs = InvokeInputs.builder()
                .query("")
                .runKind(RunKind.HEARTBEAT)
                .runContext(RunContext.builder().reason(HeartbeatReason.INTERVAL).sessionId("s1").build())
                .build();
        return AgentCallbackContext.builder()
                .inputs(inputs)
                .extra(new HashMap<>())
                .build();
    }

    private static Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Object readInheritedField(Object target, Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void writeField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void writeInheritedField(Object target, Class<?> owner, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
