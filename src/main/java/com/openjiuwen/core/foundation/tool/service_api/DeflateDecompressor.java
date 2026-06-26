/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Deflate decompressor.
 * <p>
 * Mirrors Python's {@code DeflateDecompressor} in
 * {@code openjiuwen/core/foundation/tool/service_api/response_parser.py}.
 */
public class DeflateDecompressor extends BaseResponseDecompressor {

    @Override
    public boolean canDecompress(String encoding) {
        return encoding != null && "deflate".equalsIgnoreCase(encoding);
    }

    @Override
    public byte[] decompress(byte[] responseData) {
        try {
            return inflateData(responseData, false);
        } catch (Exception firstException) {
            try {
                return inflateData(responseData, true);
            } catch (Exception secondException) {
                throw new IllegalArgumentException(
                        "Deflate decompression failed: " + secondException.getMessage(),
                        secondException
                );
            }
        }
    }

    private byte[] inflateData(byte[] data, boolean nowrap) throws Exception {
        Inflater inflater = new Inflater(nowrap);
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
