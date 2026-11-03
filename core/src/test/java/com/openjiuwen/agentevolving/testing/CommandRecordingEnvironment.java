/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.testing;

import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.DockerEnvironment;
import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.ExecResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Records the Docker boundary while keeping command construction real.
 *
 * @since 0.1.17
 */
public class CommandRecordingEnvironment extends DockerEnvironment {
    private final List<List<String>> commands = new ArrayList<>();
    private Function<List<String>, String> stdout = command -> "";

    /**
     * Initializes a running container through the simulated Docker boundary.
     */
    public CommandRecordingEnvironment() {
        super("test");
        start().join();
    }

    /**
     * Returns the literal arguments executed inside the container.
     *
     * @return immutable snapshot of recorded command arguments
     */
    public List<List<String>> getCommands() {
        return List.copyOf(commands);
    }

    /**
     * Configures command output at the Docker boundary.
     *
     * @param output output function receiving the literal command arguments
     */
    public void setStdout(Function<List<String>, String> output) {
        stdout = output;
    }

    @Override
    protected String resolveDockerPath() {
        return "docker";
    }

    @Override
    protected CompletableFuture<Void> delay(long millis) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected CompletableFuture<ExecResult> runCommand(List<String> command, int timeout) {
        if ("run".equals(command.get(1))) {
            return CompletableFuture.completedFuture(new ExecResult("test-container", "", 0, false));
        }
        List<String> arguments = List.copyOf(command.subList(command.indexOf("test-container") + 1, command.size()));
        commands.add(arguments);
        return CompletableFuture.completedFuture(new ExecResult(stdout.apply(arguments), "", 0, false));
    }

    @Override
    public CompletableFuture<Boolean> copyTo(Path source, String destination) {
        return CompletableFuture.completedFuture(true);
    }
}
