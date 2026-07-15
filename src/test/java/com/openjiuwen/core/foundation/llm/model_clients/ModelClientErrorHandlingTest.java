/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ErrorResponseBodySanitizer;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelCallFailureStage;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelClientException;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelClientInternalException;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelClientInternalFailureInfo;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelCallFailureInfo;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelHttpFailureInfo;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelHttpStatusException;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelResponseParseException;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelResponseParseFailureInfo;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelStreamException;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelStreamFailureInfo;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelTransportException;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelTransportFailureInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void modelHttpStatusExceptionCarriesTypedFailureInfo() {
        String rawSecret = "sk-raw-secret";
        String safeBody = "{\"error\":\"invalid api key\",\"key\":\"[redacted]\"}";
        ModelHttpFailureInfo info = new ModelHttpFailureInfo(
                ModelCallFailureStage.HTTP_STATUS,
                "OpenAI",
                "https://api.example.test/v1",
                false,
                401,
                safeBody,
                false);

        ModelHttpStatusException error = new ModelHttpStatusException(info, null);
        ModelHttpFailureInfo typedDetails = error.getFailureInfo();

        assertFailure(error, info, typedDetails, ModelCallFailureStage.HTTP_STATUS);
        assertThat(error.getMessage()).contains("HTTP 401");
        assertThat(info.safeResponseBody()).isEqualTo(safeBody);
        assertThat(info.responseBody()).isEqualTo(safeBody);
        assertThat(error.getParams())
                .containsEntry("failure_stage", "HTTP_STATUS")
                .containsEntry("status_code", 401)
                .containsEntry("response_body", safeBody);
        assertThat(error.getMessage()).doesNotContain(rawSecret);
        assertThat(error.getParams()).doesNotContainValue(rawSecret);
    }

    @Test
    void modelTransportExceptionCarriesSafeTypedFailureInfo() {
        String rawSecret = "Bearer raw-token";
        ModelTransportFailureInfo info = new ModelTransportFailureInfo(
                ModelCallFailureStage.TRANSPORT,
                "OpenAI",
                "https://api.example.test/v1",
                false,
                "connect",
                "SocketTimeoutException",
                "connection timed out with token [redacted]");

        ModelTransportException error = new ModelTransportException(info, null);
        ModelTransportFailureInfo typedDetails = error.getFailureInfo();

        assertFailure(error, info, typedDetails, ModelCallFailureStage.TRANSPORT);
        assertThat(info.safeExceptionMessage()).contains("[redacted]");
        assertThat(error.getParams())
                .containsEntry("failure_stage", "TRANSPORT")
                .containsEntry("phase", "connect")
                .containsEntry("exception_class", "SocketTimeoutException")
                .containsEntry("exception_message", "connection timed out with token [redacted]");
        assertThat(error.getMessage()).doesNotContain(rawSecret);
        assertThat(error.getParams()).doesNotContainValue(rawSecret);
    }

    @Test
    void modelResponseParseExceptionCarriesSafeTypedFailureInfo() {
        String rawSecret = "raw-json-secret";
        ModelResponseParseFailureInfo info = new ModelResponseParseFailureInfo(
                ModelCallFailureStage.RESPONSE_PARSE,
                "OpenAI",
                "https://api.example.test/v1",
                false,
                "parse_json",
                "{\"api_key\":\"[redacted]\"}",
                true,
                "JsonProcessingException",
                "unexpected token near [redacted]");

        ModelResponseParseException error = new ModelResponseParseException(info, null);
        ModelResponseParseFailureInfo typedDetails = error.getFailureInfo();

        assertFailure(error, info, typedDetails, ModelCallFailureStage.RESPONSE_PARSE);
        assertThat(info.safeResponseBody()).contains("[redacted]");
        assertThat(info.responseBody()).isEqualTo(info.safeResponseBody());
        assertThat(info.safeExceptionMessage()).contains("[redacted]");
        assertThat(error.getParams())
                .containsEntry("failure_stage", "RESPONSE_PARSE")
                .containsEntry("phase", "parse_json")
                .containsEntry("response_body", "{\"api_key\":\"[redacted]\"}")
                .containsEntry("response_body_truncated", true)
                .containsEntry("exception_message", "unexpected token near [redacted]");
        assertThat(error.getMessage()).doesNotContain(rawSecret);
        assertThat(error.getParams()).doesNotContainValue(rawSecret);
    }

    @Test
    void modelStreamExceptionCarriesSafeTypedFailureInfo() {
        String rawSecret = "raw-stream-secret";
        ModelStreamFailureInfo info = new ModelStreamFailureInfo(
                ModelCallFailureStage.STREAM,
                "OpenAI",
                "https://api.example.test/v1",
                true,
                "read_chunk",
                "data",
                "IOException",
                "stream closed after [redacted]");

        ModelStreamException error = new ModelStreamException(info, null);
        ModelStreamFailureInfo typedDetails = error.getFailureInfo();

        assertFailure(error, info, typedDetails, ModelCallFailureStage.STREAM);
        assertThat(info.safeExceptionMessage()).contains("[redacted]");
        assertThat(error.getParams())
                .containsEntry("failure_stage", "STREAM")
                .containsEntry("phase", "read_chunk")
                .containsEntry("event", "data")
                .containsEntry("exception_message", "stream closed after [redacted]");
        assertThat(error.getMessage()).doesNotContain(rawSecret);
        assertThat(error.getParams()).doesNotContainValue(rawSecret);
    }

    @Test
    void modelClientInternalExceptionCarriesSafeTypedFailureInfo() {
        String rawSecret = "raw-internal-secret";
        ModelClientInternalFailureInfo info = new ModelClientInternalFailureInfo(
                ModelCallFailureStage.CLIENT_INTERNAL,
                "OpenAI",
                "https://api.example.test/v1",
                false,
                "build_request",
                "missing required config [redacted]",
                "IllegalStateException",
                "config included [redacted]");

        ModelClientInternalException error = new ModelClientInternalException(info, null);
        ModelClientInternalFailureInfo typedDetails = error.getFailureInfo();

        assertFailure(error, info, typedDetails, ModelCallFailureStage.CLIENT_INTERNAL);
        assertThat(info.safeMessage()).contains("[redacted]");
        assertThat(info.safeExceptionMessage()).contains("[redacted]");
        assertThat(error.getParams())
                .containsEntry("failure_stage", "CLIENT_INTERNAL")
                .containsEntry("phase", "build_request")
                .containsEntry("message", "missing required config [redacted]")
                .containsEntry("exception_message", "config included [redacted]");
        assertThat(error.getMessage()).doesNotContain(rawSecret);
        assertThat(error.getParams()).doesNotContainValue(rawSecret);
    }

    @Test
    void failureInfoRejectsMismatchedStage() {
        assertThatThrownBy(() -> new ModelHttpFailureInfo(
                ModelCallFailureStage.TRANSPORT, "OpenAI", "https://api.example.test/v1",
                false, 500, "{}", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelTransportFailureInfo(
                ModelCallFailureStage.HTTP_STATUS, "OpenAI", "https://api.example.test/v1",
                false, "connect", "IOException", "safe"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelResponseParseFailureInfo(
                ModelCallFailureStage.STREAM, "OpenAI", "https://api.example.test/v1",
                false, "parse", "{}", false, "JsonException", "safe"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelStreamFailureInfo(
                ModelCallFailureStage.RESPONSE_PARSE, "OpenAI", "https://api.example.test/v1",
                true, "read", "data", "IOException", "safe"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelClientInternalFailureInfo(
                ModelCallFailureStage.TRANSPORT, "OpenAI", "https://api.example.test/v1",
                false, "build", "safe", "IllegalStateException", "safe"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static <T extends ModelCallFailureInfo> void assertFailure(
            ModelClientException error,
            T info,
            T typedDetails,
            ModelCallFailureStage stage) {
        assertThat(error).isInstanceOf(BaseError.class);
        assertThat(error.getStatus()).isEqualTo(StatusCode.MODEL_CALL_FAILED);
        assertThat(error.getFailureInfo()).isSameAs(info);
        assertThat(typedDetails).isSameAs(info);
        assertThat(error.getStage()).isEqualTo(stage);
        assertThat(error.getParams()).doesNotContainKey("request_level_headers");
    }
}
