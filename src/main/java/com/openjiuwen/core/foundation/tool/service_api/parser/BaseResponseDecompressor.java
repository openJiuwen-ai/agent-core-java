/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.service_api.parser;

/**
 * Base class for response decompressors.
 * <p>
 * Mirrors Python's {@code BaseResponseDecompressor}.
 */
public abstract class BaseResponseDecompressor {

    /**
     * Check if this decompressor supports the given content encoding.
     *
     * @param encoding the Content-Encoding value (e.g., "gzip", "deflate")
     * @return true if supported
     */
    public abstract boolean canDecompress(String encoding);

    /**
     * Decompress the response data.
     *
     * @param responseData the compressed bytes
     * @return decompressed bytes
     * @throws java.io.IOException if decompression fails
     */
    public abstract byte[] decompress(byte[] responseData) throws java.io.IOException;
}
