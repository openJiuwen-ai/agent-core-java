/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.rails;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Tracks the base commit SHA for revert capability.
 * 
 * @since 0.1.7
 */
public class RevertOnFailureRail extends DeepAgentRail {
    private String baseCommit = "";

    /**
     * setBaseCommit.
     * 
     * @param sha sha
     * @since 0.1.7
     */
    public void setBaseCommit(String sha) {
        this.baseCommit = sha == null ? "" : sha;
    }

    /**
     * getBaseCommit.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getBaseCommit() {
        return baseCommit;
    }

    /**
     * beforeTaskIteration.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    public void beforeTaskIteration(AgentCallbackContext ctx) {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
            CompletableFuture<String> outputFuture = OpenJiuwenExecutors.supplyBackgroundAsync(
                    () -> readFirstLine(process));
            String output = outputFuture.join();
            if (process.onExit().join().exitValue() == 0 && output != null && !output.isBlank()) {
                setBaseCommit(output.trim());
            }
        } catch (IOException ignored) {
            // Python skips capture when git is unavailable.
        }
    }

    /**
     * revert.
     * 
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public boolean revert(Path workspace) {
        if (baseCommit.isBlank()) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("git", "reset", "--hard", baseCommit)
                    .directory(workspace == null ? null : workspace.toFile()).redirectErrorStream(true).start();
            CompletableFuture<Void> drain = OpenJiuwenExecutors.runBackgroundAsync(() -> discardOutput(process));
            boolean isSuccess = process.onExit().join().exitValue() == 0;
            drain.join();
            return isSuccess;
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * readFirstLine.
     * 
     * @param process process
     * @return the result
     * @since 0.1.7
     */
    private static String readFirstLine(Process process) {
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.readLine();
        } catch (IOException ignored) {
            return null;
        }
    }

    /**
     * discardOutput.
     * 
     * @param process process
     * @since 0.1.7
     */
    private static void discardOutput(Process process) {
        try {
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
        } catch (IOException ignored) {
            // Best-effort drain prevents small command output from blocking the process.
        }
    }
}
