/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.common.utils.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Registry for response parsers and decompressors.
 * <p>
 * Mirrors Python's {@code ParserRegistry} in
 * {@code openjiuwen/core/foundation/tool/service_api/response_parser.py}.
 *
 * <p>The Python source defines {@code _apply_decompression()} but the current
 * {@code parse()} implementation does not call it. This Java translation keeps
 * the helper available while preserving that exact parse flow.</p>
 */
public class ParserRegistry {

    private static final Logger LOGGER = Logger.getLogger(ParserRegistry.class.getName());

    private final List<BaseResponseParser> parsers = new ArrayList<>();
    private final Map<String, BaseResponseDecompressor> decompressors = new LinkedHashMap<>();

    private ParserRegistry() {
        registerDefaultComponents();
    }

    public static ParserRegistry getInstance() {
        return Singleton.getInstance(ParserRegistry.class, ParserRegistry::new);
    }

    public static ParserRegistry getParserRegistry() {
        return getInstance();
    }

    public void register(BaseResponseParser parser) {
        parsers.add(parser);
    }

    public void registerDecompressor(String encoding, BaseResponseDecompressor decompressor) {
        decompressors.put(encoding.toLowerCase(), decompressor);
    }

    byte[] applyDecompression(byte[] responseData, String contentEncoding) {
        if (contentEncoding == null || contentEncoding.isEmpty() || responseData == null || responseData.length == 0) {
            return responseData;
        }

        String[] encodings = contentEncoding.split(",");
        byte[] current = responseData;
        for (String rawEncoding : encodings) {
            String encoding = rawEncoding.trim().toLowerCase();
            BaseResponseDecompressor decompressor = decompressors.get(encoding);
            if (decompressor != null && decompressor.canDecompress(encoding)) {
                try {
                    current = decompressor.decompress(current);
                } catch (Exception exception) {
                    LOGGER.severe("Decompression failed (" + encoding + "): " + exception.getMessage());
                    break;
                }
            }
        }
        return current;
    }

    public Object parse(Map<String, String> responseHeaders, byte[] responseData, int statusCode) {
        Map<String, String> lowerHeaders = new LinkedHashMap<>();
        if (responseHeaders != null) {
            responseHeaders.forEach((key, value) -> lowerHeaders.put(key.toLowerCase(), value));
        }
        String contentType = lowerHeaders.getOrDefault("content-type", "text/plain");
        String contentEncoding = lowerHeaders.getOrDefault("content-encoding", "");

        Object result = null;
        boolean parsed = false;
        for (BaseResponseParser parser : parsers) {
            if (parser.canParse(contentType, statusCode, responseHeaders)) {
                result = parser.parse(responseData, contentEncoding, responseHeaders);
                parsed = true;
                break;
            }
        }

        if (!parsed) {
            throw new IllegalArgumentException("not found response parser for " + contentType);
        }
        return result;
    }

    private void registerDefaultComponents() {
        register(new JsonResponseParser());
        register(new TextResponseParser());
        registerDecompressor("gzip", new GzipDecompressor());
        registerDecompressor("deflate", new DeflateDecompressor());
    }
}
