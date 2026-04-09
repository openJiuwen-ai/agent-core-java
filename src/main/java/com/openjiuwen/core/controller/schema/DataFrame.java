  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.controller.schema;

import java.util.Map;

/**
 * DataFrame sealed interface for transmitting different types of data in the controller.
 * <p>
 * Supported data types:
 * <ul>
 *   <li>{@link TextDataFrame} - text data</li>
 *   <li>{@link FileDataFrame} - file data (bytes or URI)</li>
 *   <li>{@link JsonDataFrame} - JSON format data</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code DataFrame = Union[TextDataFrame, FileDataFrame, JsonDataFrame]}.
 */
public sealed interface DataFrame permits DataFrame.TextDataFrame, DataFrame.FileDataFrame, DataFrame.JsonDataFrame {

    /**
     * Get the data frame type.
     *
     * @return one of "text", "file", "json"
     */
    String getType();

    /**
     * Text data frame.
     */
    record TextDataFrame(String text) implements DataFrame {
        @Override
        public String getType() {
            return "text";
        }
    }

    /**
     * File data frame supporting both bytes and URI.
     */
    record FileDataFrame(String name, String mimeType, byte[] bytes, String uri) implements DataFrame {
        @Override
        public String getType() {
            return "file";
        }

        public FileDataFrame(String name, String mimeType) {
            this(name, mimeType, null, null);
        }
    }

    /**
     * JSON format data frame.
     */
    record JsonDataFrame(Map<String, Object> data) implements DataFrame {
        @Override
        public String getType() {
            return "json";
        }
    }
}
