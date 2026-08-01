/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * GZIP decompressor.
 * <p>
 * Mirrors Python's {@code GzipDecompressor}.
 * 
 * @since 0.1.7
 */
public class GzipDecompressor extends BaseResponseDecompressor {
    /**
     * canDecompress.
     * 
     * @param encoding encoding
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean canDecompress(String encoding) {
        if (encoding == null) {
            return false;
        }
        String lower = encoding.toLowerCase(Locale.ROOT);
        return "gzip".equals(lower) || "x-gzip".equals(lower);
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
            return decompressGzip(responseData);
        } catch (IOException e) {
            // Fallback: try raw deflate (no gzip header)
            try {
                return decompressRawDeflate(responseData);
            } catch (IOException e2) {
                throw new IOException("GZIP decompression failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * decompressGzip.
     * 
     * @param data data
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    private byte[] decompressGzip(byte[] data) throws IOException {
        try (var bais = new ByteArrayInputStream(data);
                var gis = new GZIPInputStream(bais);
                var baos = new ByteArrayOutputStream()) {
            gis.transferTo(baos);
            return baos.toByteArray();
        }
    }

    /**
     * decompressRawDeflate.
     * 
     * @param data data
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    private byte[] decompressRawDeflate(byte[] data) throws IOException {
        Inflater inflater = new Inflater(true); // nowrap = true
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
