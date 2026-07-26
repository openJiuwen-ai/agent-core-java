/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.model_clients.errors.ErrorResponseBodySanitizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelClientErrorHandlingTest {
    @Test
    void sanitizerRedactsSensitiveJsonAndBearerTokens() {
        String body = """
                {"error":{"message":"Authorization: Bearer secret-token","api_key":"sk-secret","token":"abc"}}
                """;

        ErrorResponseBodySanitizer.SanitizedBody sanitized = ErrorResponseBodySanitizer.sanitize(body, 4096);

        assertThat(sanitized.body()).contains("[REDACTED]");
        assertThat(sanitized.body()).doesNotContain("secret-token", "sk-secret", "abc");
        assertThat(sanitized.truncated()).isFalse();
    }

    @Test
    void sanitizerRedactsCommonSensitiveFieldNames() {
        String body = """
                {
                  "client_secret": "json-client-secret",
                  "id_token": "json-id-token",
                  "accessToken": "json-access-token",
                  "refreshToken": "json-refresh-token",
                  "api-key": "json-api-key",
                  "x-api-key": "json-x-api-key",
                  "secret_key": "json-secret-key",
                  "query": "client_secret=query-client-secret"
                }
                client_secret=form-client-secret accessToken=form-access refreshToken=form-refresh api-key=form-api-key
                x-api-key=form-x-api-key secret_key=form-secret-key id_token=form-id-token
                """;

        ErrorResponseBodySanitizer.SanitizedBody sanitized = ErrorResponseBodySanitizer.sanitize(body, 4096);

        assertThat(sanitized.body()).contains("[REDACTED]");
        assertThat(sanitized.body()).doesNotContain(
                "json-client-secret",
                "json-id-token",
                "json-access-token",
                "json-refresh-token",
                "json-api-key",
                "json-x-api-key",
                "json-secret-key",
                "query-client-secret",
                "form-client-secret",
                "form-access",
                "form-refresh",
                "form-api-key",
                "form-x-api-key",
                "form-secret-key",
                "form-id-token");
        assertThat(sanitized.truncated()).isFalse();
    }

    @Test
    void sanitizerRemovesControlCharactersExceptWhitespaceControls() {
        String body = "alpha\u0000beta\nline\rnext\tcell\u001Fgama";

        ErrorResponseBodySanitizer.SanitizedBody sanitized = ErrorResponseBodySanitizer.sanitize(body, 4096);

        assertThat(sanitized.body()).isEqualTo("alphabeta\nline\rnext\tcellgama");
        assertThat(sanitized.truncated()).isFalse();
    }

    @Test
    void sanitizerTruncatesLongBody() {
        String body = "x".repeat(32);

        ErrorResponseBodySanitizer.SanitizedBody sanitized = ErrorResponseBodySanitizer.sanitize(body, 20);

        assertThat(sanitized.body()).hasSizeLessThanOrEqualTo(20);
        assertThat(sanitized.body()).startsWith("xxxxx");
        assertThat(sanitized.body()).contains("[truncated]");
        assertThat(sanitized.truncated()).isTrue();
    }

    @Test
    void sanitizerRedactsBeforeTruncatingLongSensitiveValues() {
        String secret = "secret-value-" + "a".repeat(128);
        String body = "client_secret=" + secret + " " + "x".repeat(128);

        ErrorResponseBodySanitizer.SanitizedBody sanitized = ErrorResponseBodySanitizer.sanitize(body, 40);

        assertThat(sanitized.body()).hasSizeLessThanOrEqualTo(40);
        assertThat(sanitized.body()).contains("[REDACTED]");
        assertThat(sanitized.body()).doesNotContain(secret, "secret-value");
        assertThat(sanitized.truncated()).isTrue();
    }

    @Test
    void sanitizerKeepsTruncatedBodyWithinVerySmallMaxLength() {
        String body = "client_secret=small-secret";

        ErrorResponseBodySanitizer.SanitizedBody sanitized = ErrorResponseBodySanitizer.sanitize(body, 4);

        assertThat(sanitized.body()).hasSizeLessThanOrEqualTo(4);
        assertThat(sanitized.body()).doesNotContain("small-secret");
        assertThat(sanitized.truncated()).isTrue();
    }
}
