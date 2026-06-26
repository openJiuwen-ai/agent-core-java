/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent.react_agent.interrupt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.harness.rails.interrupt.ApproveResult;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import com.openjiuwen.harness.rails.interrupt.InterruptResult;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests.unit_tests.agent.react_agent.interrupt.test_fine_grained_auto_confirm} in
 * {@code tests/unit_tests/agent/react_agent/interrupt/test_fine_grained_auto_confirm.py}.
 */
class FineGrainedAutoConfirmMissingTest {
    private static final FineGrainedConfirmRail RAIL = new FineGrainedConfirmRail();

    @Test
    void testFineGrainedAutoConfirmSingleAgent() {
        ToolCall readA = toolCall("read", "{\"filepath\":\"/tmp/a.txt\"}");
        ToolCall readB = toolCall("read", "{\"filepath\":\"/tmp/b.txt\"}");

        InterruptDecision first = RAIL.resolveInterrupt(null, readA, null, Map.of());
        assertInterruptKey(first, "read_a");

        assertThat(RAIL.resolveInterrupt(null, readA, null, Map.of("read_a", true)))
                .isInstanceOf(ApproveResult.class);
        assertInterruptKey(RAIL.resolveInterrupt(null, readB, null, Map.of("read_a", true)), "read_b");
    }

    @Test
    void testFineGrainedAutoConfirmMergeKeys() {
        Map<String, Object> mergedKeys = Map.of("read_a", true, "read_b", true);

        assertThat(RAIL.resolveInterrupt(null, toolCall("read", "{\"filepath\":\"/tmp/a.txt\"}"),
                null, mergedKeys)).isInstanceOf(ApproveResult.class);
        assertThat(RAIL.resolveInterrupt(null, toolCall("read", "{\"filepath\":\"/tmp/b.txt\"}"),
                null, mergedKeys)).isInstanceOf(ApproveResult.class);
    }

    @Test
    void testFineGrainedAutoConfirmDifferentTools() {
        ToolCall readA = toolCall("read", "{\"filepath\":\"/tmp/a.txt\"}");
        ToolCall writeA = toolCall("write", "{\"filepath\":\"/tmp/a.txt\",\"content\":\"hello\"}");

        assertThat(RAIL.resolveInterrupt(null, readA, null, Map.of("read_a", true)))
                .isInstanceOf(ApproveResult.class);
        assertInterruptKey(RAIL.resolveInterrupt(null, writeA, null, Map.of("read_a", true)), "write_a");
        assertThat(RAIL.resolveInterrupt(null, writeA, null, Map.of("read_a", true, "write_a", true)))
                .isInstanceOf(ApproveResult.class);
    }

    @Test
    void testFineGrainedAutoConfirmConcurrentTools() {
        ToolCall readA = toolCall("read", "{\"filepath\":\"/tmp/a.txt\"}");
        ToolCall readB = toolCall("read", "{\"filepath\":\"/tmp/b.txt\"}");

        assertInterruptKey(RAIL.resolveInterrupt(null, readA, null, Map.of()), "read_a");
        assertInterruptKey(RAIL.resolveInterrupt(null, readB, null, Map.of()), "read_b");
        assertThat(RAIL.resolveInterrupt(null, readA, null, Map.of("read_a", true)))
                .isInstanceOf(ApproveResult.class);
        assertInterruptKey(RAIL.resolveInterrupt(null, readB, null, Map.of("read_a", true)), "read_b");
    }

    private static ToolCall toolCall(String name, String arguments) {
        return ToolCall.builder()
                .id("call-" + name)
                .name(name)
                .arguments(arguments)
                .build();
    }

    private static void assertInterruptKey(InterruptDecision decision, String expectedKey) {
        assertThat(decision).isInstanceOf(InterruptResult.class);
        InterruptResult interrupt = (InterruptResult) decision;
        assertThat(interrupt.request().getAutoConfirmKey()).isEqualTo(expectedKey);
    }

    private static final class FineGrainedConfirmRail extends ConfirmInterruptRail {
        private static final ObjectMapper JSON = new ObjectMapper();

        private FineGrainedConfirmRail() {
            super(java.util.List.of("read", "write"));
        }

        @Override
        protected String getAutoConfirmKey(Object toolCall) {
            if (!(toolCall instanceof ToolCall call)) {
                return "";
            }
            Map<String, Object> args = parseArguments(call.getArguments());
            if ("read".equals(call.getName()) || "write".equals(call.getName())) {
                String filePath = String.valueOf(args.getOrDefault("filepath", ""));
                if (!filePath.isBlank()) {
                    String fileName = new File(filePath).getName();
                    int dot = fileName.lastIndexOf('.');
                    String stem = dot >= 0 ? fileName.substring(0, dot) : fileName;
                    return call.getName() + "_" + stem;
                }
            }
            return call.getName();
        }

        private static Map<String, Object> parseArguments(String arguments) {
            try {
                return JSON.readValue(arguments, new TypeReference<>() {
                });
            } catch (Exception exception) {
                throw new IllegalArgumentException("Invalid tool call arguments: " + arguments, exception);
            }
        }
    }
}
