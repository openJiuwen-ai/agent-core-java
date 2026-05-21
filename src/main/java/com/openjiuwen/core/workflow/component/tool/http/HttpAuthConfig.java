/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP authentication configuration.
 * <p>
 * Mirrors Python's {@code HttpAuthConfig}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpAuthConfig {

    @Builder.Default
    private HttpAuthType type = HttpAuthType.NONE;
    
    // Basic/Digest auth
    private String username;
    private String password;
    
    // Bearer token
    private String token;
    
    // API Key
    private String apiKey;
    @Builder.Default
    private String inLocation = "header";
    @Builder.Default
    private String name = "Authorization";
    
    // AWS Signature
    private String accessKey;
    private String secretKey;
    private String region;
}