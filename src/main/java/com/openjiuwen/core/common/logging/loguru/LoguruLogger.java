/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.loguru;

import java.util.Map;

/**
 * Loguru backend adapter entry point.
 *
 * <p>Mirrors Python's {@code LoguruLogger} in
 * {@code openjiuwen.core.common.logging.loguru.loguru_impl}.</p>
 */
public class LoguruLogger extends com.openjiuwen.core.common.logging.LoguruLogger {

    public LoguruLogger(String name, Map<String, Object> config) {
        super(name, config);
    }

    public LoguruLogger(String name) {
        super(name);
    }
}
