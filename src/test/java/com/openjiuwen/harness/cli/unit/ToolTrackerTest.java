/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.rails.ToolTracker;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for tool usage tracking.
 * <p>
 * Mirrors Python's {@code test_tool_tracker} in
 * {@code tests.cli.unit.test_tool_tracker}.
 */
class ToolTrackerTest {

    @Test
    void afterToolCallReadFileRecordsCall() {
        ToolTracker tracker = new ToolTracker();
        tracker.recordCall("read_file");
        assertEquals(1, tracker.getCallCount("read_file"));
        assertEquals(1, tracker.getTotalCalls());
    }

    @Test
    void afterToolCallNonReadFileRecordsCall() {
        ToolTracker tracker = new ToolTracker();
        tracker.recordCall("bash");
        assertEquals(1, tracker.getCallCount("bash"));
        assertEquals(1, tracker.getTotalCalls());
    }

    @Test
    void multipleCallsToSameTool() {
        ToolTracker tracker = new ToolTracker();
        tracker.recordCall("read_file");
        tracker.recordCall("read_file");
        tracker.recordCall("read_file");
        assertEquals(3, tracker.getCallCount("read_file"));
    }

    @Test
    void callHistoryIsMaintained() {
        ToolTracker tracker = new ToolTracker();
        tracker.recordCall("bash");
        tracker.recordCall("read_file");
        tracker.recordCall("bash");
        assertEquals(3, tracker.getCallHistory().size());
        assertEquals("bash", tracker.getCallHistory().get(0));
        assertEquals("read_file", tracker.getCallHistory().get(1));
        assertEquals("bash", tracker.getCallHistory().get(2));
    }

    @Test
    void getAllCallCounts() {
        ToolTracker tracker = new ToolTracker();
        tracker.recordCall("bash");
        tracker.recordCall("read_file");
        tracker.recordCall("bash");
        Map<String, Integer> counts = tracker.getAllCallCounts();
        assertEquals(2, counts.get("bash"));
        assertEquals(1, counts.get("read_file"));
    }

    @Test
    void resetClearsAll() {
        ToolTracker tracker = new ToolTracker();
        tracker.recordCall("bash");
        tracker.recordCall("read_file");
        tracker.reset();
        assertEquals(0, tracker.getTotalCalls());
        assertEquals(0, tracker.getCallCount("bash"));
        assertTrue(tracker.getCallHistory().isEmpty());
    }
}
