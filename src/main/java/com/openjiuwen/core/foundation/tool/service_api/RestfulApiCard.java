/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.foundation.tool.ToolCard;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.Set;

/**
 * RESTful API tool card with HTTP method and URL configuration.
 * <p>
 * Mirrors Python's {@code RestfulApiCard}.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RestfulApiCard extends ToolCard {

    /** Supported HTTP methods. */
    public static final Set<String> SUPPORTED_METHODS = Set.of("POST", "GET");

    /** Restful API URL, e.g. /api/v1/users. */
    private final String url;

    /** HTTP method (POST or GET). */
    @Builder.Default
    private final String method = "POST";

    /** Default request headers. */
    @Builder.Default
    private final Map<String, Object> headers = Map.of();

    /** Default query parameters. */
    @Builder.Default
    private final Map<String, Object> queries = Map.of();

    /** Path parameters for URL placeholders. */
    @Builder.Default
    private final Map<String, Object> paths = Map.of();

    /** Request timeout in seconds. */
    @Builder.Default
    private final double timeout = 60.0;

    /** Maximum response size in bytes (default 10 MB). */
    @Builder.Default
    private final int maxResponseByteSize = 10 * 1024 * 1024;
}
