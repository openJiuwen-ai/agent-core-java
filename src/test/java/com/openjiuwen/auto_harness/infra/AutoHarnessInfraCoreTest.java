package com.openjiuwen.auto_harness.infra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoHarnessInfraCoreTest {

    @Test
    void sessionBudgetControllerMirrorsCoreBudgetSemantics() {
        SessionBudgetController controller = new SessionBudgetController();
        assertEquals(0.0, controller.getElapsedSecs());
        assertFalse(controller.isShouldStop());

        SessionBudgetController remaining = new SessionBudgetController(100.0, 10.0, 20.0);
        assertEquals(100.0, remaining.getRemainingSecs());

        SessionBudgetController started = new SessionBudgetController(100.0, 10.0, 20.0);
        started.start();
        assertTrue(started.getElapsedSecs() >= 0.0);
        assertTrue(started.getRemainingSecs() <= 100.0);

        SessionBudgetController wallClock = new SessionBudgetController(10.0, 10.0, 20.0);
        wallClock.start();
        wallClock.setStartForTest(System.nanoTime() / 1_000_000_000.0 - 11.0);
        assertTrue(wallClock.isShouldStop());

        SessionBudgetController cost = new SessionBudgetController(100.0, 1.0, 20.0);
        cost.start();
        cost.addCost(1.5);
        assertTrue(cost.isShouldStop());

        SessionBudgetController taskBudget = new SessionBudgetController(3600.0, 10.0, 600.0);
        taskBudget.start();
        assertTrue(taskBudget.checkTaskBudget());
    }

    @Test
    void fixLoopControllerMirrorsCoreLoopSemantics() {
        FixLoopResult defaults = new FixLoopResult();
        assertFalse(defaults.isSuccess());
        assertEquals(0, defaults.getAttempts());
        assertEquals(1, defaults.getPhase());
        assertTrue(defaults.getErrorLog().isEmpty());

        FixLoopController passFirst = new FixLoopController(3, 0, 1.0);
        FixLoopResult result = passFirst.run(
                () -> new FixLoopController.CiResult(true, ""),
                errors -> { }
        );
        assertTrue(result.isSuccess());
        assertEquals(1, result.getAttempts());
        assertEquals(1, result.getPhase());

        final int[] callCount = {0};
        FixLoopController passAfterRetries = new FixLoopController(5, 0, 1.0);
        FixLoopResult retryResult = passAfterRetries.run(
                () -> {
                    callCount[0]++;
                    return new FixLoopController.CiResult(callCount[0] >= 3, "lint error");
                },
                errors -> { }
        );
        assertTrue(retryResult.isSuccess());
        assertEquals(3, retryResult.getAttempts());

        FixLoopController exhaust = new FixLoopController(2, 0, 1.0);
        FixLoopResult exhausted = exhaust.run(
                () -> new FixLoopController.CiResult(false, "fail"),
                errors -> { }
        );
        assertFalse(exhausted.isSuccess());
        assertEquals(2, exhausted.getAttempts());
        assertEquals(2, exhausted.getErrorLog().size());
    }
}
