/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external.cli_agent;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Inject text by writing newline-framed lines to a subprocess stdin pipe.
 *
 * <p>Mirrors Python's {@code StdinPipeInjector} in
 * {@code openjiuwen/agent_teams/external/cli_agent/injector.py}.</p>
 */
public final class StdinPipeInjector implements Injector {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final OutputStream stdin;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public StdinPipeInjector(OutputStream stdin) {
        this.stdin = Objects.requireNonNull(stdin, "stdin");
    }

    @Override
    public CompletionStage<Void> write(String text) {
        if (closed.get()) {
            TEAM_LOGGER.debug("StdinPipeInjector.write after close; dropping");
            return CompletableFuture.completedFuture(null);
        }
        try {
            stdin.write((text + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            return CompletableFuture.completedFuture(null);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletionStage<Void> aclose() {
        if (!closed.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            stdin.close();
        } catch (IOException | RuntimeException exception) {
            TEAM_LOGGER.debug("StdinPipeInjector close failed: {}", exception);
        }
        return CompletableFuture.completedFuture(null);
    }
}
