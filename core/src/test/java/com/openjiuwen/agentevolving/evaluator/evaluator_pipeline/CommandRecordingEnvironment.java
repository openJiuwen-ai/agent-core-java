/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Records the Docker boundary while keeping command construction real. */
public class CommandRecordingEnvironment extends DockerEnvironment {
    public final List<List<String>> commands = new ArrayList<>();
    public final List<String> copiedTargets = new ArrayList<>();
    public Function<List<String>, String> stdout = command -> "";

    public CommandRecordingEnvironment() {
        super("test");
        setContainerIdForTesting("test-container");
    }

    @Override
    protected String resolveDockerPath() {
        return "docker";
    }

    @Override
    protected CompletableFuture<ExecResult> runCommand(List<String> command, int timeout) {
        List<String> arguments = List.copyOf(command.subList(command.indexOf("test-container") + 1, command.size()));
        commands.add(arguments);
        return CompletableFuture.completedFuture(new ExecResult(stdout.apply(arguments), "", 0, false));
    }

    @Override
    public CompletableFuture<Boolean> copyTo(Path source, String destination) {
        copiedTargets.add(destination);
        return CompletableFuture.completedFuture(true);
    }
}
