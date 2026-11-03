/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Deflate decompressor.
 * <p>
 * Mirrors Python's {@code DeflateDecompressor}.
 * 
 * @since 0.1.7
 */
public class DeflateDecompressor extends BaseResponseDecompressor {
    /**
     * canDecompress.
     * 
     * @param encoding encoding
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean canDecompress(String encoding) {
        return encoding != null && "deflate".equalsIgnoreCase(encoding);
    }

    /**
     * decompress.
     * 
     * @param responseData responseData
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    @Override
    public byte[] decompress(byte[] responseData) throws IOException {
        try {
            return inflateData(responseData, false);
        } catch (IOException e) {
            // Fallback: try raw inflate (no zlib header)
            try {
                return inflateData(responseData, true);
            } catch (IOException e2) {
                throw new IOException("Deflate decompression failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * inflateData.
     * 
     * @param data data
     * @param nowrap nowrap
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    private byte[] inflateData(byte[] data, boolean nowrap) throws IOException {
        Inflater inflater = new Inflater(nowrap);
        try (var bais = new ByteArrayInputStream(data);
                var iis = new InflaterInputStream(bais, inflater);
                var baos = new ByteArrayOutputStream()) {
            iis.transferTo(baos);
            return baos.toByteArray();
        } finally {
            inflater.end();
        }
    }
}
