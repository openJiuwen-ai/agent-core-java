/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents.interrupt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.single_agent.interrupt.InterruptConstants;
import com.openjiuwen.core.single_agent.interrupt.ToolInterruptHandler;
import com.openjiuwen.core.single_agent.interrupt.ToolInterruptionState;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for fine-grained auto-confirm feature.
 *
 * <p>Mirrors Python's {@code test_fine_grained_auto_confirm.py} in
 * {@code tests/system_tests/agent/react_agent/interrupt/}.
 */
@DisplayName("Fine Grained Auto Confirm")
class FineGrainedAutoConfirmTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("auto confirm key derivation from tool call")
    void testAutoConfirmKeyDerivationFromToolCall() {
        String filepath = "/tmp/test_file.txt";
        String filename = filepath.substring(filepath.lastIndexOf('/') + 1);
        String nameWithoutExt = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;

        assertThat(nameWithoutExt).isEqualTo("test_file");
        assertThat("read_" + nameWithoutExt).isEqualTo("read_test_file");
    }

    @Test
    @DisplayName("auto confirm key for write tool")
    void testAutoConfirmKeyForWriteTool() {
        String filepath = "/tmp/output.txt";
        String filename = filepath.substring(filepath.lastIndexOf('/') + 1);
        String nameWithoutExt = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;

        assertThat("write_" + nameWithoutExt).isEqualTo("write_output");
    }

    @Test
    @DisplayName("auto confirm key for no extension file")
    void testAutoConfirmKeyForNoExtensionFile() {
        String filepath = "/tmp/README";
        String filename = filepath.substring(filepath.lastIndexOf('/') + 1);
        String nameWithoutExt = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;

        assertThat("read_" + nameWithoutExt).isEqualTo("read_README");
    }

    @Test
    @DisplayName("single agent fine grained auto confirm")
    void testSingleAgentFineGrainedAutoConfirm() {
        FineGrainedConfirmRail rail = new FineGrainedConfirmRail(List.of("read"));
        AgentSessionApi session = new AgentSessionApi("test_single_fg");

        ToolCall readA = readCall("call-a", "/tmp/a.txt");
        InterruptDecision firstDecision = rail.resolveInterrupt(null, readA, null, Map.of());
        assertTrue(firstDecision.isInterrupted());
        assertEquals("read_a", interruptRequest(firstDecision).get("auto_confirm_key"));

        saveAutoConfirm(session, "call-a", "read_a", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> autoConfig = (Map<String, Object>) session.getState(
                InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY);

        InterruptDecision sameFileDecision = rail.resolveInterrupt(null, readA, null, autoConfig);
        assertTrue(sameFileDecision.isApproved());

        ToolCall readB = readCall("call-b", "/tmp/b.txt");
        InterruptDecision differentFileDecision = rail.resolveInterrupt(null, readB, null, autoConfig);
        assertTrue(differentFileDecision.isInterrupted());
        assertEquals("read_b", interruptRequest(differentFileDecision).get("auto_confirm_key"));
    }

    @Test
    @DisplayName("three layer agent fine grained auto confirm")
    void testThreeLayerAgentFineGrainedAutoConfirm() {
        FineGrainedConfirmRail rail = new FineGrainedConfirmRail(List.of("read"));
        AgentSessionApi session = new AgentSessionApi("test_3layer_fg");

        ToolCall readA = readCall("inner-a", "/tmp/a.txt");
        ToolCall readB = readCall("inner-b", "/tmp/b.txt");
        assertEquals("read_a", rail.exposeAutoConfirmKey(readA));
        assertEquals("read_b", rail.exposeAutoConfirmKey(readB));

        saveAutoConfirm(session, "inner-a", "read_a", true);
        saveAutoConfirm(session, "inner-b", "read_b", false);

        @SuppressWarnings("unchecked")
        Map<String, Object> autoConfig = (Map<String, Object>) session.getState(
                InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY);

        assertTrue(rail.resolveInterrupt(null, readA, null, autoConfig).isApproved());
        InterruptDecision readBDecision = rail.resolveInterrupt(null, readB, null, autoConfig);
        assertTrue(readBDecision.isInterrupted());
        assertEquals("read_b", interruptRequest(readBDecision).get("auto_confirm_key"));
    }

    @Test
    @DisplayName("three layer agent fine grained auto confirm clear session")
    void testThreeLayerAgentFineGrainedAutoConfirmClearSession() {
        FineGrainedConfirmRail rail = new FineGrainedConfirmRail(List.of("read"));
        AgentSessionApi session = new AgentSessionApi("test_clear_session");
        ToolCall readA = readCall("clear-a", "/tmp/a.txt");

        saveAutoConfirm(session, "clear-a", "read_a", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> autoConfig = (Map<String, Object>) session.getState(
                InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY);
        assertTrue(rail.resolveInterrupt(null, readA, null, autoConfig).isApproved());

        Map<String, Object> clearUpdate = new HashMap<>();
        clearUpdate.put(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY, null);
        session.updateState(clearUpdate);

        InterruptDecision afterClear = rail.resolveInterrupt(null, readA, null, null);
        assertTrue(afterClear.isInterrupted());
        assertEquals("read_a", interruptRequest(afterClear).get("auto_confirm_key"));
    }

    @Test
    @DisplayName("fine grained auto confirm merge keys")
    void testFineGrainedAutoConfirmMergeKeys() {
        FineGrainedConfirmRail rail = new FineGrainedConfirmRail(List.of("read"));
        AgentSessionApi session = new AgentSessionApi("test_merge_keys");
        ToolCall readA = readCall("merge-a", "/tmp/a.txt");
        ToolCall readB = readCall("merge-b", "/tmp/b.txt");

        saveAutoConfirm(session, "merge-a", "read_a", true);
        saveAutoConfirm(session, "merge-b", "read_b", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> autoConfig = (Map<String, Object>) session.getState(
                InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY);
        assertThat(autoConfig).containsEntry("read_a", true).containsEntry("read_b", true);
        assertTrue(rail.resolveInterrupt(null, readA, null, autoConfig).isApproved());
        assertTrue(rail.resolveInterrupt(null, readB, null, autoConfig).isApproved());
    }

    private void saveAutoConfirm(AgentSessionApi session, String interruptId, String autoConfirmKey,
                                 boolean autoConfirm) {
        ToolInterruptionState state = new ToolInterruptionState();
        state.setAutoConfirmMapping(Map.of(interruptId, autoConfirmKey));
        InteractiveInput input = new InteractiveInput();
        input.update(interruptId, Map.of(
                "approved", true,
                "feedback", "Confirm " + autoConfirmKey,
                "auto_confirm", autoConfirm
        ));

        ToolInterruptHandler.saveAutoConfirmFromState(state, input, session);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> interruptRequest(InterruptDecision decision) {
        assertTrue(decision instanceof InterruptDecision.InterruptResult);
        return (Map<String, Object>) ((InterruptDecision.InterruptResult) decision).getRequest();
    }

    private ToolCall readCall(String id, String filepath) {
        try {
            return ToolCall.builder()
                    .id(id)
                    .name("read")
                    .arguments(JSON_MAPPER.writeValueAsString(Map.of("filepath", filepath)))
                    .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class FineGrainedConfirmRail extends ConfirmInterruptRail {
        FineGrainedConfirmRail(Iterable<String> toolNames) {
            super(toolNames);
        }

        String exposeAutoConfirmKey(ToolCall toolCall) {
            return getAutoConfirmKey(toolCall);
        }

        @Override
        protected String getAutoConfirmKey(Object toolCall) {
            if (!(toolCall instanceof ToolCall call)) {
                return super.getAutoConfirmKey(toolCall);
            }
            if (!"read".equals(call.getName())) {
                return call.getName();
            }
            Map<?, ?> args = parseArguments(call.getArguments());
            Object filepathObj = args.get("filepath");
            if (filepathObj == null || String.valueOf(filepathObj).isBlank()) {
                return call.getName();
            }
            String filepath = String.valueOf(filepathObj);
            String filename = filepath.substring(Math.max(filepath.lastIndexOf('/'), filepath.lastIndexOf('\\')) + 1);
            String nameWithoutExt = filename.contains(".")
                    ? filename.substring(0, filename.lastIndexOf('.'))
                    : filename;
            return call.getName() + "_" + nameWithoutExt;
        }

        private Map<?, ?> parseArguments(Object arguments) {
            if (arguments instanceof Map<?, ?> map) {
                return map;
            }
            if (arguments instanceof String text && !text.isBlank()) {
                try {
                    return JSON_MAPPER.readValue(text, Map.class);
                } catch (JsonProcessingException e) {
                    return Map.of();
                }
            }
            return Map.of();
        }
    }
}
