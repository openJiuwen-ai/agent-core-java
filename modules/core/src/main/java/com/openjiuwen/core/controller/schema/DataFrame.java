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
 * <li>{@link TextDataFrame} - text data</li>
 * <li>{@link FileDataFrame} - file data (bytes or URI)</li>
 * <li>{@link JsonDataFrame} - JSON format data</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code DataFrame = Union[TextDataFrame, FileDataFrame, JsonDataFrame]}.
 * 
 * @since 0.1.7
 */
public sealed interface DataFrame permits DataFrame.TextDataFrame, DataFrame.FileDataFrame, DataFrame.JsonDataFrame {
    /**
     * getType.
     * 
     * @return the result
     * @since 0.1.7
     */
    String getType();

    /**
     * Text data frame.
     * 
     * @param text text
     * @since 0.1.7
     */
    record TextDataFrame(String text) implements DataFrame {
        /**
         * getType.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getType() {
            return "text";
        }
    }

    /**
     * File data frame supporting both bytes and URI.
     * 
     * @param name name
     * @param mimeType mimeType
     * @param bytes bytes
     * @param uri uri
     * @since 0.1.7
     */
    record FileDataFrame(String name, String mimeType, byte[] bytes, String uri) implements DataFrame {
        /**
         * getType.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getType() {
            return "file";
        }

        /**
         * FileDataFrame.
         * 
         * @param name name
         * @param mimeType mimeType
         * @since 0.1.7
         */
        public FileDataFrame(String name, String mimeType) {
            this(name, mimeType, null, null);
        }
    }

    /**
     * JSON format data frame.
     * 
     * @param data data
     * @since 0.1.7
     */
    record JsonDataFrame(Map<String, Object> data) implements DataFrame {
        /**
         * getType.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getType() {
            return "json";
        }
    }
}
