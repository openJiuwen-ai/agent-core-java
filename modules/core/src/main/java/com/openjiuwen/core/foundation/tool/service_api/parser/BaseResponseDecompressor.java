/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api.parser;

/**
 * Base class for response decompressors.
 * <p>
 * Mirrors Python's {@code BaseResponseDecompressor}.
 * 
 * @since 0.1.7
 */
public abstract class BaseResponseDecompressor {
    /**
     * canDecompress.
     * 
     * @param encoding encoding
     * @return the result
     * @since 0.1.7
     */
    public abstract boolean canDecompress(String encoding);

    /**
     * Decompress the response data.
     * 
     * @param responseData the compressed bytes
     * @return decompressed bytes
     * @throws java.io.IOException if decompression fails
     * @since 0.1.7
     */
    public abstract byte[] decompress(byte[] responseData) throws java.io.IOException;
}
