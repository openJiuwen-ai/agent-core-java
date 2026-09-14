/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import com.openjiuwen.agent_teams.external.cli_agent.CliAgentAdapter;
import com.openjiuwen.agent_teams.external.cli_agent.Injector;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Streaming runtime backed by one long-lived CLI subprocess driven via stdin.
 *
 * <p>Mirrors Python's {@code ExternalCliRuntime} in
 * {@code openjiuwen/agent_teams/external/runtime.py}.</p>
 */
public final class ExternalCliRuntime extends CliRuntimeBase {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final Injector injector;
    private final Iterator<String> outputLines;
    private final Process process;
    private final AtomicReference<CompletableFuture<String>> stderrDrainFuture = new AtomicReference<>();

    private volatile boolean abortRequested;

    public ExternalCliRuntime(
            String memberName,
            CliAgentAdapter adapter,
            Injector injector,
            Iterator<String> outputLines
    ) {
        this(memberName, adapter, injector, outputLines, null);
    }

    public ExternalCliRuntime(
            String memberName,
            CliAgentAdapter adapter,
            Injector injector,
            Iterator<String> outputLines,
            Process process
    ) {
        super(memberName, adapter);
        this.injector = Objects.requireNonNull(injector, "injector");
        this.outputLines = Objects.requireNonNull(outputLines, "outputLines");
        this.process = process;
    }

    @Override
    protected void drive(Map<String, Object> inputs, Consumer<Object> emitter) {
        ensureStderrDrain();
        String text = queryText(inputs);
        abortRequested = false;
        injector.write(adapter().formatInput(text)).toCompletableFuture().join();
        int chunkIndex = 0;
        while (outputLines.hasNext()) {
            if (abortRequested) {
                TEAM_LOGGER.debug("[{}] external cli turn aborted", memberName());
                return;
            }
            String line = outputLines.next();
            String summary = adapter().summarizeOutputLine(line);
            if (summary != null) {
                TEAM_LOGGER.debug("[{}] {}", memberName(), summary);
                emitter.accept(makeChunk(summary, chunkIndex));
                chunkIndex += 1;
            }
            if (adapter().isTurnComplete(line)) {
                return;
            }
        }
        raiseOnPrematureEof();
    }

    @Override
    public CompletionStage<Void> steer(String content) {
        return injector.write(adapter().formatInput(content));
    }

    @Override
    public CompletionStage<Void> followUp(String content) {
        return injector.write(adapter().formatInput(content));
    }

    @Override
    public CompletionStage<Void> abort() {
        abortRequested = true;
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> aclose() {
        return CompletableFuture.runAsync(() -> {
            injector.aclose().toCompletableFuture().join();
            terminate(process);
            CompletableFuture<String> drain = stderrDrainFuture.getAndSet(null);
            if (drain == null) {
                return;
            }
            String stderrTail = "";
            try {
                stderrTail = drain.get(1L, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                drain.cancel(true);
            }
            Integer returnCode = returnCode(process);
            if (returnCode != null && returnCode != 0 && !stderrTail.isBlank()) {
                TEAM_LOGGER.warning(
                        "[external-cli] member {} CLI exited with code {} (likely auth/quota/credit exhaustion or a crash). stderr: {}",
                        memberName(),
                        returnCode,
                        stderrTail
                );
            }
        });
    }

    private void ensureStderrDrain() {
        if (process == null || process.errorReader() == null) {
            return;
        }
        stderrDrainFuture.compareAndSet(null, CompletableFuture.supplyAsync(() -> readStderrTail(process.getErrorStream())));
    }

    private void raiseOnPrematureEof() {
        Integer returnCode = returnCode(process);
        if (returnCode == null || returnCode == 0) {
            return;
        }
        String stderrTail = peekStderrTail();
        String detail = stderrTail.isBlank() ? "" : "; stderr: " + stderrTail;
        ErrorHelper.raiseError(
                StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                null,
                null,
                null,
                Map.of(
                        "error_msg",
                        "external CLI member '" + memberName() + "' exited with code " + returnCode
                                + " mid-turn (likely auth/quota/credit exhaustion or a crash)" + detail
                )
        );
    }

    private String peekStderrTail() {
        CompletableFuture<String> drain = stderrDrainFuture.get();
        if (drain == null) {
            return "";
        }
        try {
            return drain.get((long) STDERR_PEEK_TIMEOUT_S, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            return "";
        }
    }
}
