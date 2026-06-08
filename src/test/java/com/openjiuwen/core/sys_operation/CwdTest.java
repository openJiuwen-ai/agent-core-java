/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's cwd module semantics for
 * {@code openjiuwen/core/sys_operation/cwd.py}.
 */
class CwdTest {

    @AfterEach
    void tearDown() {
        Cwd.clear();
    }

    @Test
    void testStateAutoCreated() {
        assertNotNull(Cwd.getState());
    }

    @Test
    void testGetCwdFallsBackToUserDir() {
        assertEquals(System.getProperty("user.dir"), Cwd.getCwd());
    }

    @Test
    void testInitCwdSetsAllLayers() {
        Path cwd = Path.of("target");
        Path workspace = Path.of("target", "workspace");
        Path teamWorkspace = Path.of("target", "team");

        Cwd.initCwd(cwd.toString(), cwd.toString(), workspace.toString(), teamWorkspace.toString());

        assertEquals(cwd.toAbsolutePath().normalize().toString(), Cwd.getCwd());
        assertEquals(cwd.toAbsolutePath().normalize().toString(), Cwd.getOriginalCwd());
        assertEquals(cwd.toAbsolutePath().normalize().toString(), Cwd.getProjectRoot());
        assertEquals(workspace.toAbsolutePath().normalize().toString(), Cwd.getWorkspace());
        assertEquals(teamWorkspace.toAbsolutePath().normalize().toString(), Cwd.getTeamWorkspace());
    }

    @Test
    void testProjectRootFallsBackToOriginalCwd() {
        Path cwd = Path.of("target");
        Cwd.initCwd(cwd.toString());
        assertEquals(cwd.toAbsolutePath().normalize().toString(), Cwd.getProjectRoot());
    }

    @Test
    void testSettersResolvePaths() {
        Cwd.setCwd("src");
        Cwd.setOriginalCwd("target");
        Cwd.setProjectRoot(".");
        Cwd.setWorkspace("build");
        Cwd.setTeamWorkspace("logs");

        assertEquals(Path.of("src").toAbsolutePath().normalize().toString(), Cwd.getCwd());
        assertEquals(Path.of("target").toAbsolutePath().normalize().toString(), Cwd.getOriginalCwd());
        assertEquals(Path.of(".").toAbsolutePath().normalize().toString(), Cwd.getProjectRoot());
        assertEquals(Path.of("build").toAbsolutePath().normalize().toString(), Cwd.getWorkspace());
        assertEquals(Path.of("logs").toAbsolutePath().normalize().toString(), Cwd.getTeamWorkspace());
    }

    @Test
    void testWorkspaceAndTeamWorkspaceDefaultToNull() {
        Cwd.initCwd("target");
        assertNull(Cwd.getWorkspace());
        assertNull(Cwd.getTeamWorkspace());
    }

    @Test
    void testChildThreadSharesSameMutableStateReference() throws Exception {
        Cwd.initCwd("target");
        CwdState parentState = Cwd.getState();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<CwdState> childState = new AtomicReference<>();

        Thread child = new Thread(() -> {
            childState.set(Cwd.getState());
            Cwd.setCwd("src/test");
            latch.countDown();
        });
        child.start();
        latch.await();
        child.join();

        assertSame(parentState, childState.get());
        assertEquals(Path.of("src/test").toAbsolutePath().normalize().toString(), Cwd.getCwd());
    }
}
