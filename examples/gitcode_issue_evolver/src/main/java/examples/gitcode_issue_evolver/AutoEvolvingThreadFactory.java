/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates named service threads with a common uncaught-exception handler.
 *
 * @since 0.1.12
 */
public final class AutoEvolvingThreadFactory implements ThreadFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoEvolvingThreadFactory.class);
    private final AtomicInteger sequence = new AtomicInteger();
    private final String namePrefix;

    /**
     * Create a thread factory for one bounded executor.
     *
     * @param namePrefix stable prefix used in generated thread names
     */
    public AutoEvolvingThreadFactory(String namePrefix) {
        this.namePrefix = Objects.requireNonNull(namePrefix, "namePrefix must not be null");
        if (namePrefix.isBlank()) {
            throw new IllegalArgumentException("namePrefix must not be blank");
        }
    }

    @Override
    public Thread newThread(Runnable task) {
        Thread thread = new Thread(task, namePrefix + "-" + sequence.incrementAndGet());
        thread.setDaemon(false);
        thread.setUncaughtExceptionHandler((failedThread, failure) ->
                LOGGER.error("Uncaught exception in service thread {}", failedThread.getName(), failure));
        return thread;
    }
}
