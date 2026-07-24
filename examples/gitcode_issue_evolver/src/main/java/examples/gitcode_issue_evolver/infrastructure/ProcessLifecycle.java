/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.infrastructure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Bounded termination support shared by trusted local child-process runners. */
final class ProcessLifecycle {
    private static final Duration GRACE_PERIOD = Duration.ofSeconds(1);

    private ProcessLifecycle() {
    }

    /**
     * Stop a process tree and wait until every observed process exits or the deadline expires.
     *
     * @param process root child process
     * @param timeout total termination timeout
     * @return whether the root and all observed descendants terminated
     */
    static boolean terminateAndWait(Process process, Duration timeout) {
        if (process == null) {
            return true;
        }
        Duration requiredTimeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (requiredTimeout.isNegative() || requiredTimeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        List<ProcessHandle> handles = new ArrayList<>(process.descendants().toList());
        handles.add(process.toHandle());
        destroy(handles, false);
        long deadline = System.nanoTime() + requiredTimeout.toNanos();
        long graceNanos = Math.min(GRACE_PERIOD.toNanos(), requiredTimeout.toNanos());
        boolean interrupted = waitUntilStopped(handles, System.nanoTime() + graceNanos);
        if (isAnyAlive(handles)) {
            destroy(handles, true);
            interrupted |= waitUntilStopped(handles, deadline);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return !isAnyAlive(handles);
    }

    private static boolean waitUntilStopped(List<ProcessHandle> handles, long deadline) {
        boolean interrupted = false;
        while (isAnyAlive(handles)) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0L) {
                break;
            }
            long sleepMillis = Math.max(1L, Math.min(25L,
                    TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException ex) {
                interrupted = true;
            }
        }
        return interrupted;
    }

    private static boolean isAnyAlive(List<ProcessHandle> handles) {
        return handles.stream().anyMatch(ProcessHandle::isAlive);
    }

    private static void destroy(List<ProcessHandle> handles, boolean forcibly) {
        for (ProcessHandle handle : handles) {
            if (!handle.isAlive()) {
                continue;
            }
            if (forcibly) {
                handle.destroyForcibly();
            } else {
                handle.destroy();
            }
        }
    }
}
