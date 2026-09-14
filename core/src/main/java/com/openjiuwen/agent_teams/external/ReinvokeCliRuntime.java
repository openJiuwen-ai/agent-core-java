/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import com.openjiuwen.agent_teams.external.cli_agent.CliAgentAdapter;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * One-shot runtime that launches a fresh CLI subprocess for each turn.
 *
 * <p>Mirrors Python's {@code ReinvokeCliRuntime} in
 * {@code openjiuwen/agent_teams/external/runtime.py}.</p>
 */
public final class ReinvokeCliRuntime extends CliRuntimeBase {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final Map<String, String> env;
    private final String cwd;
    private final String cliSessionId;
    private final List<String> launchExtraArgs;
    private final long inactivityTimeoutMillis;
    private final Long turnTimeoutMillis;

    private boolean firstTurn = true;
    private final List<String> pending = new ArrayList<>();
    private volatile boolean aborted;
    private volatile Process current;

    public ReinvokeCliRuntime(
            String memberName,
            CliAgentAdapter adapter,
            Map<String, String> env
    ) {
        this(memberName, adapter, env, null, null, List.of(), DEFAULT_INACTIVITY_TIMEOUT_S, null);
    }

    public ReinvokeCliRuntime(
            String memberName,
            CliAgentAdapter adapter,
            Map<String, String> env,
            String cwd,
            String cliSessionId,
            List<String> launchExtraArgs,
            double inactivityTimeoutSeconds,
            Double turnTimeoutSeconds
    ) {
        super(memberName, adapter);
        this.env = env == null ? Map.of() : Map.copyOf(env);
        this.cwd = cwd;
        this.cliSessionId = cliSessionId == null || cliSessionId.isBlank() ? UUID.randomUUID().toString().replace("-", "") : cliSessionId;
        this.launchExtraArgs = launchExtraArgs == null ? List.of() : List.copyOf(launchExtraArgs);
        this.inactivityTimeoutMillis = Math.max(1L, Math.round(inactivityTimeoutSeconds * 1000.0d));
        this.turnTimeoutMillis = turnTimeoutSeconds == null ? null : Math.max(1L, Math.round(turnTimeoutSeconds * 1000.0d));
    }

    @Override
    protected void drive(Map<String, Object> inputs, Consumer<Object> emitter) {
        String prompt = queryText(inputs);
        int chunkIndex = 0;
        aborted = false;
        while (prompt != null && !aborted) {
            chunkIndex = runOnce(prompt, emitter, chunkIndex);
            if (aborted) {
                return;
            }
            prompt = drainPending();
        }
    }

    @Override
    public CompletionStage<Void> steer(String content) {
        pending.add(content);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> followUp(String content) {
        pending.add(content);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> abort() {
        aborted = true;
        terminate(current);
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> aclose() {
        return CompletableFuture.runAsync(() -> terminate(current));
    }

    private int runOnce(String prompt, Consumer<Object> emitter, int chunkIndex) {
        List<String> argv = adapter().buildTurnCommand(prompt, cliSessionId, firstTurn, launchExtraArgs);
        firstTurn = false;
        TEAM_LOGGER.info("[external-cli] re-invoke {} for member {}", argv, memberName());
        ProcessBuilder builder = new ProcessBuilder(argv);
        if (!cwdIsBlank()) {
            builder.directory(new java.io.File(cwd));
        }
        builder.redirectInput(ProcessBuilder.Redirect.PIPE);
        builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        builder.redirectError(ProcessBuilder.Redirect.PIPE);
        builder.environment().putAll(env);
        try {
            Process process = builder.start();
            current = process;
            try {
                if (process.getOutputStream() != null) {
                    process.getOutputStream().close();
                }
                return consumeTurn(process, emitter, chunkIndex);
            } finally {
                current = null;
            }
        } catch (IOException exception) {
            ErrorHelper.raiseError(
                    StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", "failed to start external CLI member '" + memberName() + "': " + exception.getMessage())
            );
            return chunkIndex;
        }
    }

    private int consumeTurn(Process process, Consumer<Object> emitter, int chunkIndex) {
        AtomicLong lastActivityNanos = new AtomicLong(System.nanoTime());
        AtomicReference<String> stderrTail = new AtomicReference<>("");
        AtomicInteger chunkIndexRef = new AtomicInteger(chunkIndex);
        CompletableFuture<Void> stdoutDrain = CompletableFuture.runAsync(() ->
                drainStdout(process.getInputStream(), lastActivityNanos, emitter, chunkIndexRef));
        CompletableFuture<Void> stderrDrain = CompletableFuture.runAsync(() ->
                stderrTail.set(drainStderr(process.getErrorStream(), lastActivityNanos)));
        long startedAtNanos = System.nanoTime();
        try {
            while (process.isAlive()) {
                if (timedOut(lastActivityNanos.get(), startedAtNanos)) {
                    boolean absolute = turnTimeoutMillis != null
                            && System.nanoTime() - startedAtNanos >= TimeUnit.MILLISECONDS.toNanos(turnTimeoutMillis);
                    TEAM_LOGGER.warning(
                            "[external-cli] member {} turn {} timeout; terminating subprocess",
                            memberName(),
                            absolute ? "absolute" : "inactivity"
                    );
                    terminate(process);
                    break;
                }
                Thread.sleep(50L);
            }
            stdoutDrain.get(5L, TimeUnit.SECONDS);
            stderrDrain.get(5L, TimeUnit.SECONDS);
            if (!aborted) {
                reportExit(returnCode(process), stderrTail.get());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            terminate(process);
        } catch (Exception exception) {
            terminate(process);
            throw new IllegalStateException("external CLI turn failed", exception);
        }
        return chunkIndexRef.get();
    }

    private void drainStdout(
            InputStream stream,
            AtomicLong lastActivityNanos,
            Consumer<Object> emitter,
            AtomicInteger chunkIndexRef
    ) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            while (true) {
                String line = reader.readLine();
                if (line == null || aborted) {
                    break;
                }
                lastActivityNanos.set(System.nanoTime());
                String summary = adapter().summarizeOutputLine(line);
                if (summary != null) {
                    TEAM_LOGGER.debug("[{}] {}", memberName(), summary);
                    emitter.accept(makeChunk(summary, chunkIndexRef.getAndIncrement()));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to drain external CLI stdout", exception);
        }
    }

    private String drainStderr(InputStream stream, AtomicLong lastActivityNanos) {
        StringBuilder builder = new StringBuilder();
        byte[] buffer = new byte[4096];
        try {
            while (true) {
                int read = stream.read(buffer);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                lastActivityNanos.set(System.nanoTime());
                builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                if (builder.length() > STDERR_TAIL_LIMIT) {
                    builder.delete(0, builder.length() - STDERR_TAIL_LIMIT);
                }
            }
        } catch (IOException ignored) {
            return builder.toString().trim();
        }
        return builder.toString().trim();
    }

    private boolean timedOut(long lastActivityNanos, long startedAtNanos) {
        if (System.nanoTime() - lastActivityNanos >= TimeUnit.MILLISECONDS.toNanos(inactivityTimeoutMillis)) {
            return true;
        }
        return turnTimeoutMillis != null
                && System.nanoTime() - startedAtNanos >= TimeUnit.MILLISECONDS.toNanos(turnTimeoutMillis);
    }

    private void reportExit(Integer returnCode, String stderrTail) {
        if (returnCode == null || returnCode == 0) {
            return;
        }
        String detail = stderrTail == null || stderrTail.isBlank() ? "" : " stderr: " + stderrTail;
        TEAM_LOGGER.warning(
                "[external-cli] member {} CLI turn exited with code {} and did no team work (likely auth/quota/credit exhaustion or a crash).{}",
                memberName(),
                returnCode,
                detail
        );
    }

    private String drainPending() {
        if (pending.isEmpty()) {
            return null;
        }
        String combined = pending.size() == 1 ? pending.get(0) : String.join(FOLLOWUP_SEPARATOR, pending);
        pending.clear();
        return combined;
    }

    private boolean cwdIsBlank() {
        return cwd == null || cwd.isBlank();
    }
}
