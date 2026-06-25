/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

/**
 * Exception thrown when a jiuwenbox sandbox is not found (404 response with sandbox-not-found body).
 *
 * @since 2026-01-01
 * @version 1.0
 */
public class SandboxNotFoundException extends RuntimeException {
    private final String sandboxId;
    private final int statusCode;
    private final String responseBody;

    /**
     * Constructs a SandboxNotFoundException with sandbox ID and HTTP response details.
     *
     * @param sandboxId the sandbox ID that was not found
     * @param statusCode the HTTP status code returned by the server
     * @param responseBody the raw response body from the server
     */
    public SandboxNotFoundException(String sandboxId, int statusCode, String responseBody) {
        super("Sandbox '" + sandboxId + "' not found (HTTP " + statusCode + ")");
        this.sandboxId = sandboxId;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * Returns the sandbox ID that was not found.
     *
     * @return the sandbox ID
     */
    public String getSandboxId() {
        return sandboxId;
    }

    /**
     * Returns the HTTP status code from the server response.
     *
     * @return the HTTP status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the raw response body from the server.
     *
     * @return the response body string
     */
    public String getResponseBody() {
        return responseBody;
    }
}
