/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api.parser;

import java.util.Locale;

/**
 * Base class for response parsers.
 * <p>
 * Mirrors Python's {@code BaseResponseParser}.
 * 
 * @since 0.1.7
 */
public abstract class BaseResponseParser {
    /**
     * canParse.
     * 
     * @param contentType contentType
     * @param statusCode statusCode
     * @param headers headers
     * @return the result
     * @since 0.1.7
     */
    public abstract boolean canParse(String contentType, int statusCode, java.util.Map<String, String> headers);

    /**
     * Parse the response data.
     * 
     * @param responseData the raw response bytes
     * @param contentType the Content-Type header
     * @return parsed result
     * @since 0.1.7
     */
    public abstract Object parse(byte[] responseData, String contentType);

    /**
     * Decode bytes using the charset from Content-Type, defaulting to UTF-8.
     * 
     * @param data data
     * @param contentType contentType
     * @return the result
     * @since 0.1.7
     */
    protected String decodeBytes(byte[] data, String contentType) {
        if (data == null || data.length == 0) {
            return "";
        }
        String charset = extractCharsetFromContentType(contentType);
        if (charset == null) {
            charset = "UTF-8";
        }
        try {
            return new String(data, charset);
        } catch (java.io.UnsupportedEncodingException e) {
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Extract charset from Content-Type header value (e.g., "text/html; charset=utf-8").
     * 
     * @param contentType contentType
     * @return the result
     * @since 0.1.7
     */
    protected static String extractCharsetFromContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return null;
        }
        String[] parts = contentType.split(";");
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].strip();
            if (part.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                String charset = part.substring("charset=".length()).strip();
                // Strip quotes
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
