/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import java.util.logging.Logger;

/**
 * Revert-on-failure rail — tracks base commit for revert.
 *
 * <p>Mirrors Python's {@code RevertOnFailureRail} in {@code openjiuwen.auto_harness.rails.revert_on_failure_rail}.</p>
 */
public class RevertOnFailureRail {

    private static final Logger logger = Logger.getLogger(RevertOnFailureRail.class.getName());

    private String baseSha = "";

    /**
     * Record the commit to revert to.
     *
     * @param sha Git commit SHA.
     */
    public void setBaseCommit(String sha) {
        this.baseSha = sha;
        logger.fine("Base commit set to " + sha);
    }

    /**
     * Return the current base commit SHA.
     *
     * @return the base commit SHA
     */
    public String getBaseCommit() {
        return baseSha;
    }

    /**
     * Capture current HEAD as base commit.
     *
     * @param ctx the agent callback context
     */
    public void beforeTaskIteration(Object ctx) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "HEAD");
            Process proc = pb.start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()));
            String sha = reader.readLine();
            proc.waitFor();
            if (proc.exitValue() == 0 && sha != null) {
                setBaseCommit(sha.trim());
            }
        } catch (Exception e) {
            logger.fine("git not found, skip capture");
        }
    }

    /**
     * Revert to the base commit.
     *
     * @param workspace Working directory for git.
     * @return True if revert succeeded.
     */
    public boolean revert(String workspace) {
        if (baseSha == null || baseSha.isEmpty()) {
            logger.warning("No base commit to revert to");
            return false;
        }

        logger.info("Reverting to base commit " + baseSha);
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "reset", "--hard", baseSha);
            pb.directory(new java.io.File(workspace));
            Process proc = pb.start();
            proc.waitFor();
            if (proc.exitValue() != 0) {
                logger.severe("git reset failed");
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.severe("git reset failed: " + e.getMessage());
            return false;
        }
    }
}