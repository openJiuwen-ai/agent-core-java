/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

/**
 * Base class for response decompressors.
 * <p>
 * Mirrors Python's {@code BaseResponseDecompressor} in
 * {@code openjiuwen/core/foundation/tool/service_api/response_parser.py}.
 */
public abstract class BaseResponseDecompressor {

    public abstract boolean canDecompress(String encoding);

    public abstract byte[] decompress(byte[] responseData);
}
