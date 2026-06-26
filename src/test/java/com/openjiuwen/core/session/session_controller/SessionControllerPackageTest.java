/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chained session-controller package parity tests.
 *
 * <p>Mirrors Python's module exports in
 * {@code openjiuwen/core/session/session_controller/__init__.py}.</p>
 */
class SessionControllerPackageTest {

    @Test
    void exposesPythonModuleAndAllSymbols() {
        assertEquals("openjiuwen/core/session/session_controller/__init__.py",
                SessionControllerPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "Scope",
                "Subject",
                "SessionScope",
                "SessionScopeKey",
                "SessionScopeFactory",
                "DataContainer",
                "Permission",
                "SharingPolicy",
                "DataContainerFactory",
                "ChainSession",
                "SessionController",
                "GlobalSessionController"
        ), SessionControllerPackage.all());
    }

    @Test
    void resolvesExportedTypesOnly() {
        assertTrue(SessionControllerPackage.exports("Scope"));
        assertTrue(SessionControllerPackage.exports("GlobalSessionController"));
        assertSame(Scope.class, SessionControllerPackage.typeFor("Scope"));
        assertSame(Subject.class, SessionControllerPackage.typeFor("Subject"));
        assertSame(SessionScopeFactory.class, SessionControllerPackage.typeFor("SessionScopeFactory"));
        assertSame(DataContainer.class, SessionControllerPackage.typeFor("DataContainer"));
        assertSame(Permission.class, SessionControllerPackage.typeFor("Permission"));
        assertSame(SharingPolicy.class, SessionControllerPackage.typeFor("SharingPolicy"));
        assertSame(ChainSession.class, SessionControllerPackage.typeFor("ChainSession"));
        assertSame(GlobalSessionController.class, SessionControllerPackage.typeFor("GlobalSessionController"));

        assertFalse(SessionControllerPackage.exports("MainScope"));
        assertFalse(SessionControllerPackage.exports("AgentSessionContainer"));
        assertNull(SessionControllerPackage.typeFor("MainScope"));
    }
}
