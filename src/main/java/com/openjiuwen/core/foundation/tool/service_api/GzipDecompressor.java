/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * GZIP decompressor.
 * <p>
 * Mirrors Python's {@code GzipDecompressor} in
 * {@code openjiuwen/core/foundation/tool/service_api/response_parser.py}.
 */
public class GzipDecompressor extends BaseResponseDecompressor {

    @Override
    public boolean canDecompress(String encoding) {
        if (encoding == null) {
            return false;
        }
        String encodingLower = encoding.toLowerCase();
        return "gzip".equals(encodingLower) || "x-gzip".equals(encodingLower);
    }

    @Override
    public byte[] decompress(byte[] responseData) {
        try {
            return decompressGzip(responseData);
        } catch (Exception firstException) {
            try {
                return decompressRawDeflate(responseData);
            } catch (Exception secondException) {
                throw new IllegalArgumentException(
                        "GZIP decompression failed: " + secondException.getMessage(),
                        secondException
                );
            }
        }
    }

    private byte[] decompressGzip(byte[] data) throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(data);
             GZIPInputStream gzip = new GZIPInputStream(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            gzip.transferTo(output);
            return output.toByteArray();
        }
    }

    private byte[] decompressRawDeflate(byte[] data) throws Exception {
        Inflater inflater = new Inflater(true);
        try (ByteArrayInputStream input = new ByteArrayInputStream(data);
             InflaterInputStream inflaterStream = new InflaterInputStream(input, inflater);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            inflaterStream.transferTo(output);
            return output.toByteArray();
        } finally {
            inflater.end();
        }
    }
}
