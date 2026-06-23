/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import com.openjiuwen.auto_harness.infra.SessionBudgetController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code SessionBudgetController} in
 * {@code openjiuwen/auto_harness/infra/session_budget.py}.
 *
 * <p>Mirrors Python's {@code TestSessionBudgetController} in
 * {@code tests/unit_tests/auto_harness/infra/test_session_budget.py}.</p>
 */
@DisplayName("Session Budget Tests")
class TestSessionBudget {

    @Test
    void testInitialState() {
        SessionBudgetController ctrl = new SessionBudgetController();
        assertEquals(0.0, ctrl.getElapsedSecs());
        assertFalse(ctrl.isShouldStop());
    }

    @Test
    void testRemainingBeforeStart() {
        SessionBudgetController ctrl = new SessionBudgetController(100.0, 10.0, 1200.0);
        assertEquals(100.0, ctrl.getRemainingSecs());
    }

    @Test
    void testStartRecordsTime() {
        SessionBudgetController ctrl = new SessionBudgetController(100.0, 10.0, 1200.0);
        ctrl.start();
        assertTrue(ctrl.getElapsedSecs() >= 0.0);
        assertTrue(ctrl.getRemainingSecs() <= 100.0);
    }

    @Test
    void testWallClockExceeded() throws ReflectiveOperationException {
        SessionBudgetController ctrl = new SessionBudgetController(10.0, 10.0, 1200.0);
        ctrl.start();
        setStartForTest(ctrl, monotonicSeconds() - 11.0);
        assertTrue(ctrl.isShouldStop());
    }

    @Test
    void testCostExceeded() {
        SessionBudgetController ctrl = new SessionBudgetController(3600.0, 1.0, 1200.0);
        ctrl.start();
        ctrl.addCost(1.5);
        assertTrue(ctrl.isShouldStop());
    }

    @Test
    void testCostNotExceeded() {
        SessionBudgetController ctrl = new SessionBudgetController(3600.0, 10.0, 1200.0);
        ctrl.start();
        ctrl.addCost(0.5);
        assertFalse(ctrl.isShouldStop());
    }

    @Test
    void testRemainingCost() {
        SessionBudgetController ctrl = new SessionBudgetController(3600.0, 5.0, 1200.0);
        ctrl.addCost(2.0);
        assertEquals(3.0, ctrl.getRemainingCostUsd());
    }

    @Test
    void testRemainingCostFloor() {
        SessionBudgetController ctrl = new SessionBudgetController(3600.0, 1.0, 1200.0);
        ctrl.addCost(5.0);
        assertEquals(0.0, ctrl.getRemainingCostUsd());
    }

    @Test
    void testCheckTaskBudgetSufficient() {
        SessionBudgetController ctrl = new SessionBudgetController(3600.0, 10.0, 600.0);
        ctrl.start();
        assertTrue(ctrl.checkTaskBudget());
    }

    @Test
    void testCheckTaskBudgetInsufficient() throws ReflectiveOperationException {
        SessionBudgetController ctrl = new SessionBudgetController(100.0, 10.0, 600.0);
        ctrl.start();
        setStartForTest(ctrl, monotonicSeconds() - 90.0);
        assertFalse(ctrl.checkTaskBudget());
    }

    @Test
    void testCheckTaskBudgetCustomTimeout() {
        SessionBudgetController ctrl = new SessionBudgetController(3600.0, 10.0, 600.0);
        ctrl.start();
        assertTrue(ctrl.checkTaskBudget(10.0));
    }

    private static void setStartForTest(SessionBudgetController ctrl, double start)
            throws ReflectiveOperationException {
        Method method = SessionBudgetController.class.getDeclaredMethod("setStartForTest", double.class);
        method.setAccessible(true);
        method.invoke(ctrl, start);
    }

    private static double monotonicSeconds() {
        return System.nanoTime() / 1_000_000_000.0;
    }
}
