/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.db_connector;

import java.util.List;

/**
 * Package bridge for database connector exports.
 *
 * <p>Mirrors Python's {@code openjiuwen/extensions/context_evolver/core/db_connector/__init__.py}.</p>
 */
public final class DbConnectorPackage {

    public static final String PYTHON_MODULE = "openjiuwen/extensions/context_evolver/core/db_connector/__init__.py";
    public static final String DESCRIPTION = "Database connector module for VectorNode persistence.";
    public static final Class<MilvusConnector> MILVUS_CONNECTOR = MilvusConnector.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("MilvusConnector");

    private DbConnectorPackage() {
    }
}
