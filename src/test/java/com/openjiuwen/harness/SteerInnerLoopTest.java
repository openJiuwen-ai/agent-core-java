package com.openjiuwen.harness;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.task_loop.TaskLoopController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_steer_inner_loop.py} in
 * {@code tests.system_tests.harness.test_steer_inner_loop}.
 * System test: steering injection in the inner ReAct loop.
 */
@DisplayName("SteerInnerLoop tests")
@Tag("system-test")
class SteerInnerLoopTest {

    @Test
    @DisplayName("External steering is visible to the same bound inner-loop queue")
    @Tag("level0")
    void testSteerVisibleInSameInvoke() {
        String steerText = "请用中文输出简洁要点";
        Queue<String> sharedQueue = new ConcurrentLinkedQueue<>();
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        ctx.bindSteeringQueue(sharedQueue);

        ctx.pushSteering(steerText);

        assertTrue(ctx.hasPendingSteering());
        assertEquals(List.of(steerText), ctx.drainSteering());
        assertFalse(ctx.hasPendingSteering());
    }

    @Test
    @DisplayName("TaskLoopController drains steering messages in FIFO order")
    @Tag("level0")
    void testTaskLoopControllerSteeringQueue() {
        TaskLoopController controller = new TaskLoopController();

        controller.pushSteering("first");
        controller.pushSteering("second");

        assertEquals(List.of("first", "second"), controller.getPendingSteering());
        assertEquals(List.of(), controller.getPendingSteering());
    }
}
