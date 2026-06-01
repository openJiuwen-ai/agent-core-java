/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.agent.react_agent.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code test_fine_grained_auto_confirm.py} in
 * {@code tests.unit_tests.agent.react_agent.interrupt}.
 */
class FineGrainedAutoConfirmTest extends InterruptTestBase {

    @Test
    @DisplayName("fine-grained auto-confirm distinguishes read file names")
    void testFineGrainedAutoConfirmSingleAgent() {
        ReadTool readTool = new ReadTool();
        AssistantFlow flow = newConfirmFlow(new FineGrainedConfirmRail(List.of("read")), readTool);

        Map<String, Object> first = flow.start(read("c1", "/tmp/a.txt"));
        List<String> ids = assertInterruptResult(first);

        Map<String, Object> second = flow.resume(confirmInterrupt(ids.get(0), true));
        assertAnswerResult(second);
        assertEquals(1, readTool.getInvokeCount());

        Map<String, Object> third = flow.start(read("c2", "/tmp/a.txt"));
        assertAnswerResult(third);
        assertEquals(2, readTool.getInvokeCount());

        Map<String, Object> fourth = flow.start(read("c3", "/tmp/b.txt"));
        assertInterruptResult(fourth);
        assertEquals(2, readTool.getInvokeCount());
    }

    @Test
    @DisplayName("fine-grained auto-confirm merges keys")
    void testFineGrainedAutoConfirmMergeKeys() {
        ReadTool readTool = new ReadTool();
        AssistantFlow flow = newConfirmFlow(new FineGrainedConfirmRail(List.of("read")), readTool);

        List<String> readA = assertInterruptResult(flow.start(read("a1", "/tmp/a.txt")));
        assertAnswerResult(flow.resume(confirmInterrupt(readA.get(0), true)));
        assertEquals(1, readTool.getInvokeCount());

        List<String> readB = assertInterruptResult(flow.start(read("b1", "/tmp/b.txt")));
        assertAnswerResult(flow.resume(confirmInterrupt(readB.get(0), true)));
        assertEquals(2, readTool.getInvokeCount());

        assertAnswerResult(flow.start(read("a2", "/tmp/a.txt")));
        assertAnswerResult(flow.start(read("b2", "/tmp/b.txt")));
        assertEquals(4, readTool.getInvokeCount());
        assertEquals(Map.of("read_a", true, "read_b", true), flow.autoConfirmConfig());
    }

    @Test
    @DisplayName("fine-grained auto-confirm keeps different tools independent")
    void testFineGrainedAutoConfirmDifferentTools() {
        ReadTool readTool = new ReadTool();
        WriteTool writeTool = new WriteTool();
        AssistantFlow flow = newConfirmFlow(
            new FineGrainedConfirmRail(List.of("read", "write")),
            readTool,
            writeTool
        );

        List<String> readA = assertInterruptResult(flow.start(read("r1", "/tmp/a.txt")));
        assertAnswerResult(flow.resume(confirmInterrupt(readA.get(0), true)));
        assertEquals(1, readTool.getInvokeCount());

        List<String> writeA = assertInterruptResult(flow.start(write("w1", "/tmp/a.txt", "hello")));
        assertAnswerResult(flow.resume(confirmInterrupt(writeA.get(0), true)));
        assertEquals(1, writeTool.getInvokeCount());

        assertAnswerResult(flow.start(read("r2", "/tmp/a.txt")));
        assertAnswerResult(flow.start(write("w2", "/tmp/a.txt", "world")));
        assertEquals(2, readTool.getInvokeCount());
        assertEquals(2, writeTool.getInvokeCount());
    }

    @Test
    @DisplayName("fine-grained auto-confirm handles concurrent tools")
    void testFineGrainedAutoConfirmConcurrentTools() {
        ReadTool readTool = new ReadTool();
        AssistantFlow flow = newConfirmFlow(new FineGrainedConfirmRail(List.of("read")), readTool);

        Map<String, Object> first = flow.start(read("c1", "/tmp/a.txt"), read("c2", "/tmp/b.txt"));
        List<String> ids = assertInterruptResult(first, 2);

        InteractiveInput confirmA = new InteractiveInput();
        confirmA.update(ids.get(0), Map.of(
            "approved", true,
            "feedback", "Confirm reading a.txt",
            "auto_confirm", true
        ));
        Map<String, Object> second = flow.resume(confirmA);
        List<String> remaining = assertInterruptResult(second);

        InteractiveInput confirmB = new InteractiveInput();
        confirmB.update(remaining.get(0), Map.of(
            "approved", true,
            "feedback", "Confirm reading b.txt",
            "auto_confirm", false
        ));
        assertAnswerResult(flow.resume(confirmB));
        assertEquals(2, readTool.getInvokeCount());

        Map<String, Object> third = flow.start(read("c3", "/tmp/a.txt"), read("c4", "/tmp/b.txt"));
        List<String> finalIds = assertInterruptResult(third);
        assertEquals(List.of("c4"), finalIds);
        assertEquals(3, readTool.getInvokeCount());
    }

    private ToolCall read(String id, String filepath) {
        return toolCall(id, "read", "{\"filepath\": \"" + filepath + "\"}");
    }

    private ToolCall write(String id, String filepath, String content) {
        return toolCall(id, "write", "{\"filepath\": \"" + filepath + "\", \"content\": \"" + content + "\"}");
    }

    private static class FineGrainedConfirmRail extends ConfirmInterruptRail {
        FineGrainedConfirmRail(Iterable<String> toolNames) {
            super(toolNames);
        }

        @Override
        protected String getAutoConfirmKey(Object toolCall) {
            if (!(toolCall instanceof ToolCall call)) {
                return super.getAutoConfirmKey(toolCall);
            }
            Map<String, Object> args = parseArgs(call.getArguments());
            Object filepath = args.get("filepath");
            if (filepath == null || String.valueOf(filepath).isBlank()) {
                return call.getName();
            }
            String filename = String.valueOf(filepath).replace('\\', '/');
            filename = filename.substring(filename.lastIndexOf('/') + 1);
            int dot = filename.lastIndexOf('.');
            String nameWithoutExt = dot >= 0 ? filename.substring(0, dot) : filename;
            return call.getName() + "_" + nameWithoutExt;
        }
    }
}
