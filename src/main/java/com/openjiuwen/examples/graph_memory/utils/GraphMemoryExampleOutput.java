/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.graph_memory.utils;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logger helpers for the graph memory example.
 *
 * <p>Mirrors Python's {@code examples.graph_memory.utils.output}.</p>
 */
public final class GraphMemoryExampleOutput {

    private static final Logger PRINT_LOGGER = Logger.getLogger("print");

    static {
        PRINT_LOGGER.setLevel(Level.WARNING);
    }

    private GraphMemoryExampleOutput() {
    }

    public static Logger getPrintLogger() {
        return PRINT_LOGGER;
    }

    public static void writeOutput(String message) {
        PRINT_LOGGER.warning(Objects.toString(message, "null"));
    }

    public static void writeOutput(String format, Object... args) {
        if (args == null || args.length == 0) {
            writeOutput(format);
            return;
        }
        PRINT_LOGGER.warning(String.format(format, args));
    }
}
