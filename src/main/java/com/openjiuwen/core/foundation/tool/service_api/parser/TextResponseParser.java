/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.foundation.tool.service_api.parser;

import java.util.Map;
import java.util.Set;

/**
 * Text response parser.
 * <p>
 * Mirrors Python's {@code TextResponseParser}.
 */
public class TextResponseParser extends BaseResponseParser {

    private static final Set<String> TEXT_CONTENT_TYPES = Set.of(
            "text/plain",
            "text/html",
            "text/xml",
            "text/css",
            "text/javascript",
            "text/csv",
            "application/xml",
            "application/xhtml+xml",
            "application/javascript",
            "application/x-www-form-urlencoded"
    );

    @Override
    public boolean canParse(String contentType, int statusCode, Map<String, String> headers) {
        if (contentType == null) {
            contentType = "";
        }
        if (TEXT_CONTENT_TYPES.contains(contentType)) {
            return true;
        }
        if (contentType.startsWith("text/")) {
            return true;
        }
        if (contentType.contains("xml") && !contentType.contains("json")) {
            return true;
        }
        if (contentType.isEmpty() && statusCode == 200 && headers != null) {
            String accept = headers.getOrDefault("Accept", "").toLowerCase();
            return accept.contains("text/") || accept.contains("html") || accept.contains("xml");
        }
        return false;
    }

    @Override
    public Object parse(byte[] responseData, String contentType) {
        if (responseData == null || responseData.length == 0) {
            return "";
        }
        return decodeBytes(responseData, contentType);
    }
}
