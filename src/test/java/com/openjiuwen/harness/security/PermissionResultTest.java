package com.openjiuwen.harness.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionResultTest {

    @Test
    void flags_deriveFromPermissionLevel() {
        PermissionResult allow = PermissionResult.builder()
                .permission(PermissionLevel.ALLOW).matchedRule("tools.x").build();
        PermissionResult ask = PermissionResult.builder()
                .permission(PermissionLevel.ASK).matchedRule("file_guard").build();
        PermissionResult deny = PermissionResult.builder()
                .permission(PermissionLevel.DENY).matchedRule("builtin[r]").build();
        assertThat(allow.isAllowed()).isTrue();
        assertThat(allow.needsApproval()).isFalse();
        assertThat(ask.needsApproval()).isTrue();
        assertThat(ask.isAllowed()).isFalse();
        assertThat(deny.isDenied()).isTrue();
    }

    @Test
    void externalPaths_defaultsToEmptyList() {
        PermissionResult result = PermissionResult.builder()
                .permission(PermissionLevel.ALLOW).build();
        assertThat(result.getExternalPaths()).isNotNull().isEmpty();
    }

    @Test
    void confirmResponse_defaultsAreFalseAndFeedbackEmpty() {
        PermissionConfirmResponse response = PermissionConfirmResponse.builder()
                .approved(true).build();
        assertThat(response.isApproved()).isTrue();
        assertThat(response.isAutoConfirm()).isFalse();
        assertThat(response.isPersistAllow()).isFalse();
        assertThat(response.getFeedback()).isEmpty();
    }

    @Test
    void confirmResponse_fullPersistShape() {
        PermissionConfirmResponse response = PermissionConfirmResponse.builder()
                .approved(true).autoConfirm(true).persistAllow(true).feedback("ok").build();
        assertThat(response.isApproved()).isTrue();
        assertThat(response.isAutoConfirm()).isTrue();
        assertThat(response.isPersistAllow()).isTrue();
        assertThat(response.getFeedback()).isEqualTo("ok");
    }
}
