/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing.backends;

import java.util.List;

/**
 * Package bridge for sharing backend exports.
 *
 * <p>Mirrors Python's {@code openjiuwen/agent_evolving/sharing/backends/__init__.py}.</p>
 */
public final class SharingBackendsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/sharing/backends/__init__.py";
    public static final String DESCRIPTION = "Sharing backends.";
    public static final Class<SharingBackend> SHARING_BACKEND = SharingBackend.class;
    public static final Class<LocalFileBackend> LOCAL_FILE_BACKEND = LocalFileBackend.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("SharingBackend", "LocalFileBackend");

    private SharingBackendsPackage() {
    }
}
