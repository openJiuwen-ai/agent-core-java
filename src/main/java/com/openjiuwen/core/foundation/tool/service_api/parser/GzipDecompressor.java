  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.foundation.tool.service_api.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * GZIP decompressor.
 * <p>
 * Mirrors Python's {@code GzipDecompressor}.
 */
public class GzipDecompressor extends BaseResponseDecompressor {

    @Override
    public boolean canDecompress(String encoding) {
        if (encoding == null) {
            return false;
        }
        String lower = encoding.toLowerCase();
        return "gzip".equals(lower) || "x-gzip".equals(lower);
    }

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

    private byte[] decompressGzip(byte[] data) throws IOException {
        try (var bais = new ByteArrayInputStream(data);
             var gis = new GZIPInputStream(bais);
             var baos = new ByteArrayOutputStream()) {
            gis.transferTo(baos);
            return baos.toByteArray();
        }
    }

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
