package com.openjiuwen.agent_teams.spawn;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code InProcessSpawnHandle} in
 * {@code openjiuwen.agent_teams.spawn.inprocess_handle}.
 */
class InProcessHandleTest {

    @Test
    void shutdownCancelsRunningTaskAndReportsCancellationAsCompleted() {
        CompletableFuture<Void> task = new CompletableFuture<>();
        InProcessHandle handle = new InProcessHandle("inproc-running", task);

        assertTrue(handle.isAlive());

        boolean result = handle.shutdown(0.01);

        assertTrue(result);
        assertTrue(task.isCancelled());
        assertFalse(handle.isAlive());
        assertTrue(handle.isShutdownRequested());
    }

    @Test
    void waitForCompletionMatchesPythonExitCodes() {
        InProcessHandle empty = new InProcessHandle("inproc-empty");
        assertEquals(-1, empty.waitForCompletion());

        CompletableFuture<Void> completed = CompletableFuture.completedFuture(null);
        InProcessHandle successful = new InProcessHandle("inproc-success", completed);
        assertEquals(0, successful.waitForCompletion());

        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        cancelled.cancel(true);
        InProcessHandle cancelledHandle = new InProcessHandle("inproc-cancelled", cancelled);
        assertEquals(-1, cancelledHandle.waitForCompletion());
    }
}
