/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.foundation.tool.auth.AuthType;
import com.openjiuwen.core.foundation.tool.auth.ToolAuthConfig;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental RESTful API auth parity test.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.foundation.tool.test_auth} in
 * {@code tests/unit_tests/core/foundation/tool/test_auth.py}.</p>
 */
class RestfulApiAuthPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/core/foundation/tool/test_auth.py";

    @TestFactory
    Collection<DynamicTest> pythonRestfulApiAuthCases() {
        return List.of(caseOf("TestRestfulApiAuth::test_restful_api_auth_flow",
                RestfulApiAuthPythonParityTest::restfulApiAuthFlow));
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void restfulApiAuthFlow() {
        ToolAuthConfig capturedAuthConfig = new ToolAuthConfig(
                AuthType.SSL.getValue(),
                Map.of(
                        "verify_switch_env", "RESTFUL_SSL_VERIFY",
                        "ssl_cert_env", "RESTFUL_SSL_CERT"
                ),
                "restful_api",
                "test-restful-api"
        );

        assertNotNull(capturedAuthConfig);
        assertEquals(AuthType.SSL.getValue(), capturedAuthConfig.getAuthType());
        assertEquals("restful_api", capturedAuthConfig.getToolType());
        assertEquals("test-restful-api", capturedAuthConfig.getToolId());
        assertEquals("RESTFUL_SSL_VERIFY", capturedAuthConfig.getConfig().get("verify_switch_env"));
        assertEquals("RESTFUL_SSL_CERT", capturedAuthConfig.getConfig().get("ssl_cert_env"));
    }
}
