/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import java.util.Map;

/**
 * Data-frame union used by the controller.
 * <p>
 * Mirrors Python's {@code DataFrame} in
 * {@code openjiuwen/core/controller/schema/dataframe.py}.
 */
public sealed interface DataFrame permits DataFrame.TextDataFrame, DataFrame.FileDataFrame, DataFrame.JsonDataFrame {

    /**
     * Return the wire type.
     *
     * @return one of {@code text}, {@code file}, or {@code json}
     */
    String getType();

    /**
     * Mirrors Python's {@code TextDataFrame} in
     * {@code openjiuwen/core/controller/schema/dataframe.py}.
     */
    record TextDataFrame(String text) implements DataFrame {
        @Override
        public String getType() {
            return "text";
        }
    }

    /**
     * Mirrors Python's {@code FileDataFrame} in
     * {@code openjiuwen/core/controller/schema/dataframe.py}.
     */
    record FileDataFrame(String name, String mimeType, byte[] bytes, String uri) implements DataFrame {
        @Override
        public String getType() {
            return "file";
        }
    }

    /**
     * Mirrors Python's {@code JsonDataFrame} in
     * {@code openjiuwen/core/controller/schema/dataframe.py}.
     */
    record JsonDataFrame(Map<String, Object> data) implements DataFrame {
        @Override
        public String getType() {
            return "json";
        }
    }
}
