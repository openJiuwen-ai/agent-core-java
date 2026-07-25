/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.http;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP authentication configuration.
 * <p>
 * Mirrors Python's {@code HttpAuthConfig}.
 *
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpAuthConfig {
    private HttpAuthType type = HttpAuthType.NONE;

    // Basic/Digest auth
    private String username;
    private String password;

    // Bearer token
    private String token;

    // API Key
    private String apiKey;
    private String inLocation = "header";
    private String name = "Authorization";

    // AWS Signature
    private String accessKey;
    private String secretKey;
    private String region;
}
