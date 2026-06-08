/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.artifacts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's package export checks in
 * {@code openjiuwen/auto_harness/artifacts/__init__.py}.
 */
class AutoHarnessArtifactsPackageTest {

    @Test
    void exportsMatchPythonPackageSurface() {
        assertEquals("openjiuwen/auto_harness/artifacts/__init__.py", AutoHarnessArtifactsPackage.PYTHON_MODULE);
        assertEquals("Artifact storage primitives for auto-harness.", AutoHarnessArtifactsPackage.DESCRIPTION);
        assertEquals(ArtifactStore.class, AutoHarnessArtifactsPackage.ARTIFACT_STORE);
    }
}
