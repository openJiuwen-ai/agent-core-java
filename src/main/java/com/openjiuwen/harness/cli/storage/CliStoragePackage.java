/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.storage;

/**
 * Package bridge for CLI storage exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/harness/cli/storage/__init__.py}.
 */
public final class CliStoragePackage {

    public static final String PYTHON_MODULE = "openjiuwen/harness/cli/storage/__init__.py";
    public static final Class<CliSessionStore> SESSION_STORE = CliSessionStore.class;
    public static final Class<CliSessionStore.StoredMessage> STORED_MESSAGE = CliSessionStore.StoredMessage.class;
    public static final Class<CliSessionStore.StoredSession> STORED_SESSION = CliSessionStore.StoredSession.class;

    private CliStoragePackage() {
    }
}
