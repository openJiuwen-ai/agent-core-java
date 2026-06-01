/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.agent.react_agent.interrupt;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_interrupt_stream.py} in
 * {@code tests.unit_tests.agent.react_agent.interrupt}.
 */
class InterruptStreamTest extends InterruptTestBase {

    @Test
    @DisplayName("stream mode detects read interrupt")
    void testHitlRailStreamInterruptDetected() {
        ReadTool readTool = new ReadTool();
        AssistantFlow flow = newConfirmFlow(new ConfirmInterruptRail(List.of("read")), readTool);

        List<OutputSchema> outputs = stream(flow.start(
            toolCall("read_id", "read", "{\"filepath\": \"/tmp/test.txt\"}")
        ));

        OutputSchema interaction = findInteraction(outputs);
        assertNotNull(interaction);
        assertEquals("read_id", interaction.getPayload());
        assertEquals(0, readTool.getInvokeCount());
    }

    @Test
    @DisplayName("stream mode confirm with auto-confirm lets later read pass")
    void testHitlRailStreamAgreeWithAutoconfirm() {
        ReadTool readTool = new ReadTool();
        AssistantFlow flow = newConfirmFlow(new ConfirmInterruptRail(List.of("read")), readTool);

        OutputSchema firstInteraction = findInteraction(stream(flow.start(
            toolCall("read1", "read", "{\"filepath\": \"/tmp/test1.txt\"}")
        )));
        assertNotNull(firstInteraction);
        assertEquals(0, readTool.getInvokeCount());

        List<OutputSchema> second = stream(flow.resume(confirmInterrupt("read1", true)));
        assertFalse(hasInteraction(second));
        assertEquals(1, readTool.getInvokeCount());

        List<OutputSchema> third = stream(flow.start(
            toolCall("read2", "read", "{\"filepath\": \"/tmp/test2.txt\"}")
        ));
        assertFalse(hasInteraction(third));
        assertEquals(2, readTool.getInvokeCount());
    }

    @Test
    @DisplayName("stream mode reject skips read execution")
    void testHitlRailStreamReject() {
        ReadTool readTool = new ReadTool();
        AssistantFlow flow = newConfirmFlow(new ConfirmInterruptRail(List.of("read")), readTool);

        OutputSchema firstInteraction = findInteraction(stream(flow.start(
            toolCall("read_id", "read", "{\"filepath\": \"/tmp/test.txt\"}")
        )));
        assertNotNull(firstInteraction);
        assertEquals(0, readTool.getInvokeCount());

        List<OutputSchema> second = stream(flow.resume(rejectInterrupt("read_id", "Reject this operation")));
        assertFalse(hasInteraction(second));
        assertEquals(0, readTool.getInvokeCount());
    }

    private List<OutputSchema> stream(Map<String, Object> result) {
        List<OutputSchema> outputs = new ArrayList<>();
        Iterator<OutputSchema> iterator = toStream(result);
        iterator.forEachRemaining(outputs::add);
        return outputs;
    }

    private Iterator<OutputSchema> toStream(Map<String, Object> result) {
        if ("interrupt".equals(result.get("result_type"))) {
            return interruptIds(result).stream()
                .map(id -> new OutputSchema(Constant.INTERACTION, 0, id))
                .iterator();
        }
        return List.of(new OutputSchema("answer", 0, result)).iterator();
    }

    private boolean hasInteraction(List<OutputSchema> outputs) {
        return outputs.stream().anyMatch(output -> Constant.INTERACTION.equals(output.getType()));
    }

    private OutputSchema findInteraction(List<OutputSchema> outputs) {
        return outputs.stream()
            .filter(output -> Constant.INTERACTION.equals(output.getType()))
            .findFirst()
            .orElse(null);
    }
}
