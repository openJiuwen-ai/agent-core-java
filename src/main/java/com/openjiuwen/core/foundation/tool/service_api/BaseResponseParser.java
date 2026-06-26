/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Base class for response parsers.
 * <p>
 * Mirrors Python's {@code BaseResponseParser} in
 * {@code openjiuwen/core/foundation/tool/service_api/response_parser.py}.
 */
public abstract class BaseResponseParser {

    public abstract boolean canParse(String contentType, int statusCode, Map<String, String> headers);

    public abstract Object parse(byte[] responseData, String encoding, Map<String, String> headers);

    protected String decodeBytes(byte[] data, String contentType) {
        if (data == null || data.length == 0) {
            return "";
        }
        String charset = extractCharsetFromContentType(contentType);
        Charset resolved = charset == null ? StandardCharsets.UTF_8 : Charset.forName(charset);
        return new String(data, resolved);
    }

    protected static String extractCharsetFromContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return null;
        }
        String[] parts = contentType.split(";");
        for (int index = 1; index < parts.length; index++) {
            String part = parts[index].trim();
            if (part.toLowerCase().startsWith("charset=")) {
                String charset = part.split("=", 2)[1].trim();
                if ((charset.startsWith("\"") && charset.endsWith("\""))
                        || (charset.startsWith("'") && charset.endsWith("'"))) {
                    charset = charset.substring(1, charset.length() - 1);
                }
                return charset;
            }
        }
        return null;
    }
}
