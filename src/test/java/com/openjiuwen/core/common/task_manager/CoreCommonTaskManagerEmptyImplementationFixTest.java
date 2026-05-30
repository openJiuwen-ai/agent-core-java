package com.openjiuwen.core.common.task_manager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression coverage for core common task manager empty-implementation fixes.
 */
class CoreCommonTaskManagerEmptyImplementationFixTest {

    @AfterEach
    void resetTaskManager() {
        TaskManager.resetInstance();
    }

    @Test
    void setFutureStoresExecutionFutureAndSignalsDoneFuture() throws Exception {
        Task task = new Task("task-1");
        CompletableFuture<String> executionFuture = new CompletableFuture<>();

        task.setFuture(executionFuture);

        assertSame(executionFuture, task.getFuture());
        assertNotSame(task.getDoneFuture(), task.getFuture());

        executionFuture.complete("finished");

        assertEquals("finished", task.getDoneFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void waitAllUsesExecutionFutureCreatedByTaskManager() throws Exception {
        TaskManager manager = TaskManager.getInstance();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Task task = manager.createTask(() -> {
            started.countDown();
            release.await(1, TimeUnit.SECONDS);
            return "done";
        }, "managed-task", "Managed Task", "group-a", null, null, false);

        started.await(1, TimeUnit.SECONDS);
        CompletableFuture<Void> waitAll = manager.waitAll();

        assertFalse(waitAll.isDone());
        assertNotSame(task.getDoneFuture(), task.getFuture());

        release.countDown();

        waitAll.get(1, TimeUnit.SECONDS);
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertEquals("done", task.getDoneFuture().get(1, TimeUnit.SECONDS));
    }
}
