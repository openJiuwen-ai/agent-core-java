/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for the sandbox recreation request body assembled by SandboxLifecycleHelper.
 *
 * @since 0.1.15
 */
class JiuwenBoxLifecycleHelperTest {
    private static final String BASE_URL = "http://mock-server:8080";

    private final List<Map<String, Object>> createBodies = new ArrayList<>();

    /**
     * Clears shared sandbox caches to isolate tests.
     */
    @AfterEach
    void tearDown() {
        JiuwenBoxProviderMixin.clearSharedSandbox(BASE_URL);
    }

    @Test
    @DisplayName("forceRecreateJiuwenBoxSandbox keeps policy outer key in create request body")
    void testForceRecreateWrapsPolicyKey() {
        Map<String, Object> filesystemPolicy = new LinkedHashMap<>();
        filesystemPolicy.put("read_write", List.of("/app/skills"));
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("filesystem_policy", filesystemPolicy);

        String newId = runForceRecreateWithCapturedBody(policy, "append");

        assertThat(newId).isEqualTo("sb-new-1");
        assertThat(createBodies).hasSize(1);
        Map<String, Object> body = createBodies.get(0);
        assertThat(body).containsKey("policy");
        assertThat(body.get("policy")).isEqualTo(policy);
        assertThat(body).containsEntry("policy_mode", "append");
        assertThat(body).doesNotContainKey("filesystem_policy");
    }

    @Test
    @DisplayName("forceRecreateJiuwenBoxSandbox omits policy key when policy is null")
    void testForceRecreateNullPolicyOmitsKey() {
        String newId = runForceRecreateWithCapturedBody(null, "append");

        assertThat(newId).isEqualTo("sb-new-1");
        assertThat(createBodies).hasSize(1);
        Map<String, Object> body = createBodies.get(0);
        assertThat(body).doesNotContainKey("policy");
        assertThat(body).containsEntry("policy_mode", "append");
    }

    /**
     * Runs forceRecreateJiuwenBoxSandbox with a mocked client and captures the create request body.
     *
     * @param policy the sandbox policy configuration map, may be null
     * @param policyMode the sandbox policy mode
     * @return the newly created sandbox ID returned by the mocked client
     */
    private String runForceRecreateWithCapturedBody(Map<String, Object> policy, String policyMode) {
        try (MockedConstruction<JiuwenBoxClient> ignored = mockConstruction(JiuwenBoxClient.class,
                (mock, context) -> when(mock.createSandbox(anyMap())).thenAnswer(invocation -> {
                    createBodies.add(invocation.getArgument(0));
                    return "sb-new-1";
                }))) {
            return SandboxLifecycleHelper.forceRecreateJiuwenBoxSandbox(BASE_URL, policy, policyMode,
                    5, null, null, null, "sandbox_lost");
        }
    }
}
