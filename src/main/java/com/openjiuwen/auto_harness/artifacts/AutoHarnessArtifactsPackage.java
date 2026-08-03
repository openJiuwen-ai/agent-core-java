/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.artifacts;

/**
 * Package bridge for auto-harness artifact exports.
 * <p>
 * Mirrors Python's module docstring and exports in
 * {@code openjiuwen/auto_harness/artifacts/__init__.py}.
 */
public final class AutoHarnessArtifactsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/auto_harness/artifacts/__init__.py";
    public static final String DESCRIPTION = "Artifact storage primitives for auto-harness.";
    public static final Class<ArtifactStore> ARTIFACT_STORE = ArtifactStore.class;

    private AutoHarnessArtifactsPackage() {
    }
}
