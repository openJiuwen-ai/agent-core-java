/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for response parsers and decompressors (singleton).
 * <p>
 * Mirrors Python's {@code ParserRegistry} (Singleton metaclass).
 */
public final class ParserRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ParserRegistry.class);

    private static volatile ParserRegistry instance;

    private final List<BaseResponseParser> parsers = new ArrayList<>();
    private final Map<String, BaseResponseDecompressor> decompressors = new LinkedHashMap<>();

    private ParserRegistry() {
        registerDefaultComponents();
    }

    /** Get the singleton instance. */
    public static ParserRegistry getInstance() {
        if (instance == null) {
            synchronized (ParserRegistry.class) {
                if (instance == null) {
                    instance = new ParserRegistry();
                }
            }
        }
        return instance;
    }

    private void registerDefaultComponents() {
        // Register parsers (order matters — first matching parser wins)
        register(new JsonResponseParser());
        register(new TextResponseParser());

        // Register decompressors
        registerDecompressor("gzip", new GzipDecompressor());
        registerDecompressor("deflate", new DeflateDecompressor());
    }

    /** Register a response parser. */
    public void register(BaseResponseParser parser) {
        parsers.add(parser);
    }

    /** Register a decompressor for the given encoding. */
    public void registerDecompressor(String encoding, BaseResponseDecompressor decompressor) {
        decompressors.put(encoding.toLowerCase(), decompressor);
    }

    /**
     * Parse the HTTP response by decompressing (if needed) and then delegating to a matching parser.
     *
     * @param responseHeaders response headers
     * @param responseData    raw response bytes
     * @param statusCode      HTTP status code
     * @return parsed result
     * @throws IllegalArgumentException if no parser can handle the content type
     */
    public Object parse(Map<String, String> responseHeaders, byte[] responseData, int statusCode) {
        // Normalize headers to lower-case keys
        Map<String, String> lowerHeaders = new LinkedHashMap<>();
        if (responseHeaders != null) {
            responseHeaders.forEach((k, v) -> lowerHeaders.put(k.toLowerCase(), v));
        }
        String contentType = lowerHeaders.getOrDefault("content-type", "text/plain");
        String contentEncoding = lowerHeaders.getOrDefault("content-encoding", "");

        // 1. Apply decompression if needed
        byte[] data = responseData;
        if (!contentEncoding.isEmpty() && data != null && data.length > 0) {
            data = applyDecompression(data, contentEncoding);
        }

        // 2. Find appropriate parser
        for (BaseResponseParser parser : parsers) {
            if (parser.canParse(contentType, statusCode, responseHeaders)) {
                return parser.parse(data, contentType);
            }
        }

        throw new IllegalArgumentException("No response parser found for content-type: " + contentType);
    }

    private byte[] applyDecompression(byte[] data, String contentEncoding) {
        String[] encodings = contentEncoding.split(",");
        for (String encoding : encodings) {
            String enc = encoding.strip().toLowerCase();
            BaseResponseDecompressor decompressor = decompressors.get(enc);
            if (decompressor != null && decompressor.canDecompress(enc)) {
                try {
                    data = decompressor.decompress(data);
                } catch (Exception e) {
                    LOG.error("Decompression failed ({}): {}", enc, e.getMessage());
                    break;
                }
            }
        }
        return data;
    }
}
