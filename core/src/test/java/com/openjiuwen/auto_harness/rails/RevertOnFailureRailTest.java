/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Mirrors Python's {@code RevertOnFailureRail} in
 * {@code openjiuwen/auto_harness/rails/revert_on_failure_rail.py}.
 */
class RevertOnFailureRailTest {

    @TempDir
    Path tempDir;

    @Test
    void setBaseCommitStoresCurrentSha() {
        RevertOnFailureRail rail = new RevertOnFailureRail();

        rail.setBaseCommit("abc123");

        assertEquals("abc123", rail.getBaseCommit());
    }

    @Test
    void revertReturnsFalseWhenBaseCommitIsMissing() throws Exception {
        RevertOnFailureRail rail = new RevertOnFailureRail();

        assertFalse(rail.revert(tempDir.toString()));
    }
}
