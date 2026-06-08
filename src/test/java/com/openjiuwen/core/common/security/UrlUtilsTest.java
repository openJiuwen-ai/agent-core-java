/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's behavior around
 * {@code openjiuwen/core/common/security/url_utils.py}.
 */
class UrlUtilsTest {

    @AfterEach
    void resetEnvReader() {
        UrlUtils.resetEnvReaderForTests();
    }

    @Test
    void checkUrlIsValidRejectsEmptyUrl() {
        BaseError error = assertThrows(BaseError.class, () -> UrlUtils.checkUrlIsValid(""));

        assertEquals(StatusCode.COMMON_URL_INPUT_INVALID, error.getStatus());
    }

    @Test
    void checkUrlIsValidRejectsIllegalProtocol() {
        BaseError error = assertThrows(BaseError.class, () -> UrlUtils.checkUrlIsValid("ftp://example.com"));

        assertEquals(StatusCode.COMMON_URL_INPUT_INVALID, error.getStatus());
    }

    @Test
    void checkUrlIsValidAllowsLoopbackWhenProtectionDisabled() {
        UrlUtils.setEnvReaderForTests(name -> "SSRF_PROTECT_ENABLED".equals(name) ? "false" : null);

        UrlUtils.checkUrlIsValid("http://127.0.0.1/service");
    }

    @Test
    void getGlobalProxyUrlRespectsNoProxy() {
        UrlUtils.setEnvReaderForTests(name -> switch (name) {
            case "NO_PROXY" -> ".example.com";
            case "http_proxy" -> " http://proxy.internal:8080 ";
            default -> null;
        });

        assertNull(UrlUtils.getGlobalProxyUrl("http://api.example.com/v1"));
    }

    @Test
    void getGlobalProxiesReturnsTrimmedProxyMap() {
        UrlUtils.setEnvReaderForTests(name -> switch (name) {
            case "HTTPS_PROXY" -> " https://proxy.internal:8443 ";
            default -> null;
        });

        assertEquals(
                Map.of("http", "https://proxy.internal:8443", "https", "https://proxy.internal:8443"),
                UrlUtils.getGlobalProxies("https://external.example.net")
        );
    }

    @Test
    void shouldBypassProxySupportsWildcardAndCidr() {
        UrlUtils.setEnvReaderForTests(name -> switch (name) {
            case "NO_PROXY" -> "*,10.0.0.0/8";
            default -> null;
        });

        assertTrue(UrlUtils.shouldBypassProxy("http://10.1.2.3/path"));
        assertTrue(UrlUtils.shouldBypassProxy("http://example.org/path"));
        assertFalse(UrlUtils.shouldBypassProxy("not a url"));
    }
}
