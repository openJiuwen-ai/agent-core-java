// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import java.util.Objects;

/**
 * File DataFrame.
 *
 * <p>Used for transmitting file-type data, supporting both bytes and URI methods.
 * Suitable for transmitting file content, such as images, documents, etc.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class FileDataFrame extends BaseDataFrame {

    private final String name;
    private final String mimeType;
    private final byte[] bytes;
    private final String uri;

    /**
     * Constructor with name and mimeType only.
     *
     * @param name     the file name (must not be null)
     * @param mimeType the MIME type (must not be null)
     */
    public FileDataFrame(String name, String mimeType) {
        this(name, mimeType, null, null);
    }

    /**
     * Full constructor.
     *
     * @param name     the file name (must not be null)
     * @param mimeType the MIME type (must not be null)
     * @param bytes    the byte data (optional)
     * @param uri      the file URI (optional)
     */
    public FileDataFrame(String name, String mimeType, byte[] bytes, String uri) {
        super("file");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType must not be null");
        this.bytes = bytes;
        this.uri = uri;
    }

    /**
     * Gets the file name.
     *
     * @return the file name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the MIME type.
     *
     * @return the MIME type
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Gets the byte data.
     *
     * @return the byte data, or null if not set
     */
    public byte[] getBytes() {
        return bytes;
    }

    /**
     * Gets the file URI.
     *
     * @return the URI, or null if not set
     */
    public String getUri() {
        return uri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FileDataFrame that = (FileDataFrame) o;
        return Objects.equals(name, that.name)
            && Objects.equals(mimeType, that.mimeType)
            && java.util.Arrays.equals(bytes, that.bytes)
            && Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), name, mimeType, uri);
        result = 31 * result + java.util.Arrays.hashCode(bytes);
        return result;
    }

    @Override
    public String toString() {
        return "FileDataFrame{type='file', name='" + name + "', mimeType='" + mimeType + "'}";
    }
}

