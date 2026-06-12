/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.file_connector;

import java.util.List;
import java.util.Map;

/**
 * Package bridge for file connector exports.
 *
 * <p>Mirrors Python's {@code openjiuwen/extensions/context_evolver/core/file_connector/__init__.py}.</p>
 */
public final class FileConnectorPackage {

    public static final String PYTHON_MODULE = "openjiuwen/extensions/context_evolver/core/file_connector/__init__.py";
    public static final String DESCRIPTION = "File connector module for generic persistence operations.";
    public static final Class<JsonFileConnector> JSON_FILE_CONNECTOR = JsonFileConnector.class;
    public static final Class<JsonFileConnector> SAFE_MODEL_DUMP_OWNER = JsonFileConnector.class;
    public static final String SAFE_MODEL_DUMP_METHOD = "safeModelDump";
    public static final List<String> EXPORTED_SYMBOLS = List.of("JSONFileConnector", "safe_model_dump");

    private FileConnectorPackage() {
    }

    public static Map<String, Object> safeModelDump(Object value) {
        return JsonFileConnector.safeModelDump(value);
    }
}
