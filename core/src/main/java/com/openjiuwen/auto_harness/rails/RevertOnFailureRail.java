/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracks the base commit SHA for revert capability.
 *
 * <p>Mirrors Python's {@code RevertOnFailureRail} in
 * {@code openjiuwen/auto_harness/rails/revert_on_failure_rail.py}.</p>
 */
public class RevertOnFailureRail extends DeepAgentRail {

    private static final Logger LOGGER = Logger.getLogger(RevertOnFailureRail.class.getName());

    private String baseSha = "";

    /**
     * Record the commit to revert to.
     *
     * @param sha Git commit SHA.
     */
    public void setBaseCommit(String sha) {
        baseSha = sha == null ? "" : sha;
        LOGGER.fine(() -> "Base commit set to " + baseSha);
    }

    /**
     * Return the current base commit SHA.
     *
     * @return current base commit SHA.
     */
    public String getBaseCommit() {
        return baseSha;
    }

    @Override
    public void beforeTaskIteration(CallbackContext ctx) {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD").start();
            String sha = readFirstLine(process);
            int exitCode = process.waitFor();
            if (exitCode == 0 && sha != null && !sha.isBlank()) {
                setBaseCommit(sha.trim());
            }
        } catch (IOException exception) {
            LOGGER.log(Level.FINE, "git not found, skip capture", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.FINE, "git rev-parse interrupted, skip capture", exception);
        }
    }

    /**
     * Revert to the base commit.
     *
     * @param workspace working directory for git.
     * @return true if revert succeeded.
     * @throws IOException if the git process cannot be started.
     * @throws InterruptedException if the current thread is interrupted.
     */
    public boolean revert(String workspace) throws IOException, InterruptedException {
        if (baseSha == null || baseSha.isEmpty()) {
            LOGGER.warning("No base commit to revert to");
            return false;
        }

        LOGGER.info(() -> "Reverting to base commit " + baseSha);
        ProcessBuilder builder = new ProcessBuilder("git", "reset", "--hard", baseSha);
        builder.directory(new File(workspace));
        Process process = builder.start();
        String stderr = readStream(process.getErrorStream());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            LOGGER.severe(() -> "git reset failed: " + stderr);
            return false;
        }
        return true;
    }

    private static String readFirstLine(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.readLine();
        }
    }

    private static String readStream(java.io.InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line = reader.readLine();
            while (line != null) {
                if (builder.length() > 0) {
                    builder.append(System.lineSeparator());
                }
                builder.append(line);
                line = reader.readLine();
            }
            return builder.toString();
        }
    }
}
